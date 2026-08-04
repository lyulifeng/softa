-- Give every existing tenant the free period it is now assumed to own, and retire the state that existed
-- only because it did not.
--
-- Free used to be unrecorded: a tenant with no period covering today fell back to the catalog's lowest tier,
-- and "no period rows at all" had its own status, NeverSubscribed. Provisioning now writes one open-ended
-- floor-plan TRIAL period at tenant creation, which makes entitlement a fact about data rather than about a
-- fallback rule, lets the free period be time-boxed (give it an end date and free access ends), and makes
-- NeverSubscribed unreachable.
--
-- Existing subscriptions predate that, so they have no such row. The resolver survives them — it reads
-- "no floor period at all" as "the row is missing" and falls back with a warning rather than revoking access —
-- but the fallback is the repair path, not the design. This script does the repair.
--
-- Order is load-bearing throughout: each step's premise is the previous step's result.

-- 0. Widen the period table's unique key BEFORE inserting anything.
--
--    It was (subscription_id, effective_start_date), which allowed one period per start date. That held only
--    while overlap was rejected. Now every tenant owns an open-ended free period dated from its creation, so
--    anything sold on that same day collides with it — including step 2's backfill, and including a tenant
--    created with a plan through the UI, which fails with a bare integrity-constraint error.
--
--    The plan joins the key rather than the key being dropped: the same plan recorded twice from the same day
--    is still a mis-entry worth rejecting, and the projection breaks remaining ties deterministically by tier.
ALTER TABLE tenant_subscription_period DROP INDEX uk_tenant_subscription_period;
ALTER TABLE tenant_subscription_period
  ADD UNIQUE INDEX uk_tenant_subscription_period (subscription_id, effective_start_date, plan_id);

-- 1. Let plan_id be null again.
--
--    The projection writes null into it deliberately: a tenant with no covering period must lose its plan,
--    which is how expiry takes effect and how the resolver knows to look further. The column had been
--    tightened to NOT NULL by an earlier annotation that has since been corrected, and the scanner cannot
--    relax it on its own — it compares against sys_field, which already agrees, so it sees no work to do and
--    the column stays as it is. Without this the next tenant to expire fails its projection silently, inside
--    a cron job, and keeps its old plan.
ALTER TABLE tenant_subscription
  MODIFY COLUMN plan_id VARCHAR(64) NULL
  COMMENT 'Projected: plan of the period covering projectedForDate. Null = no covering period';

-- 2. Write the missing free period, one per subscription that lacks one.
--
--    Dated from the TENANT's creation, not today: the row means "this tenant has had free access since it
--    existed", and dating it today would leave every historical month looking uncovered in the period table.
--    Open-ended, because nobody has time-boxed these.
--
--    TRIAL, not PAID: nobody paid for it. The distinction is what keeps the free period out of revenue reads,
--    and it is what the projection turns into "on trial" rather than "subscribed".
--
--    `tenant_id` is stamped from the subscription's own tenant so the row is visible under tenant isolation —
--    a period row written without it would be invisible to the very tenant it belongs to.
--
--    The floor plan is resolved from the catalog (lowest tier, ties by id), NOT hard-coded as 'plan.free':
--    a deployment names its own plans, and a fixed id would silently insert a dangling reference elsewhere.
INSERT INTO tenant_subscription_period
    (id, subscription_id, tenant_id, plan_id, period_type, effective_start_date, effective_end_date,
     created_time, created_by, updated_time, updated_by)
SELECT
    -- No sequence is reachable from SQL, and the column is a plain BIGINT (distributed ids are assigned in
    -- the application). Offsetting from the current maximum keeps these clear of existing rows, and of each
    -- other via ROW_NUMBER.
    (SELECT COALESCE(MAX(p2.id), 0) FROM tenant_subscription_period p2)
        + ROW_NUMBER() OVER (ORDER BY s.id),
    s.id,
    t.id,
    (SELECT pl.id FROM plan pl WHERE pl.tier IS NOT NULL ORDER BY pl.tier ASC, pl.id ASC LIMIT 1),
    'Trial',
    DATE(COALESCE(t.created_time, s.created_time, NOW())),
    NULL,
    NOW(), 'migration:V36', NOW(), 'migration:V36'
  FROM tenant_subscription s
  LEFT JOIN tenant_info t ON t.subscription_id = s.id
 WHERE EXISTS (SELECT 1 FROM plan pl WHERE pl.tier IS NOT NULL)
   AND NOT EXISTS (
        SELECT 1 FROM tenant_subscription_period p
         WHERE p.subscription_id = s.id
           AND p.plan_id = (SELECT pl.id FROM plan pl
                             WHERE pl.tier IS NOT NULL ORDER BY pl.tier ASC, pl.id ASC LIMIT 1));

-- 3. Retire NeverSubscribed from the projected rows.
--
--    Every one of them now owns an open-ended free period that covers today, so the projection will compute
--    Trial for them on its next refresh. Setting it here rather than waiting is what keeps the window between
--    this migration and that refresh from serving a status the enum no longer has — which would fail to
--    deserialize, not merely display oddly.
--
--    plan_id is set alongside for the same window: the projection will write the same value, and until it runs
--    a null here would make the resolver treat these tenants as uncovered.
UPDATE tenant_subscription s
   SET s.subscription_status = 'Trial',
       s.plan_id = (SELECT pl.id FROM plan pl WHERE pl.tier IS NOT NULL ORDER BY pl.tier ASC, pl.id ASC LIMIT 1)
 WHERE s.subscription_status = 'NeverSubscribed'
   AND EXISTS (SELECT 1 FROM plan pl WHERE pl.tier IS NOT NULL);

-- 4. Retire its option item. Removing an option is destructive, so the scanner never does it — a leftover row
--    keeps NeverSubscribed in every status picker and filter, offering operators a value nothing can produce.
DELETE FROM sys_option_item
 WHERE option_set_code = 'SubscriptionStatus'
   AND item_code = 'NeverSubscribed';

--    …and from the studio mirror where one exists. Guarded because design_option_item is optional: a runtime
--    without studio-starter has no mirror, and an unconditional DELETE would fail the whole migration there.
SET @has_design_mirror := (SELECT COUNT(*) FROM information_schema.TABLES
                            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'design_option_item');
SET @sql := IF(@has_design_mirror > 0,
    'DELETE FROM design_option_item WHERE option_set_code = ''SubscriptionStatus'' AND item_code = ''NeverSubscribed''',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Deliberately NOT done here: relabelling Scheduled to "Pending". A label is additive metadata the annotation
-- lane self-applies, so the scanner reconciles it from the enum on the next boot; writing it by hand would
-- duplicate a rule that already has an owner.
