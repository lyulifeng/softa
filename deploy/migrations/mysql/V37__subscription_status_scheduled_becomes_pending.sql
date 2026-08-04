-- Replace the SubscriptionStatus item "Scheduled" with "Pending" — code, label and business data all one word.
--
-- The code used to be `Scheduled` while the label said `Pending`, so the value the frontend switches on and the
-- value ops reads were different words and a reader of either side had to know about the other.
--
-- This is a replacement rather than a tracked rename, so the scanner does exactly one of the three things
-- needed: it adds the "Pending" item. It never DROPs, so the old item survives its warning; and it never
-- touches business data. Both remaining halves are here.
--
-- Order matters between this script and the boot that introduces the new item. Run this first — or accept that
-- between the two, `tenant_subscription.subscription_status` holds a value the enum cannot answer to, which
-- fails to deserialize rather than merely displaying oddly.

-- 1. Carry the rows. Idempotent by construction: a second run matches nothing.
UPDATE tenant_subscription
   SET subscription_status = 'Pending'
 WHERE subscription_status = 'Scheduled';

-- 2. Retire the old option item. Without this it keeps appearing in every status picker and filter, offering
--    operators a value the projection can no longer produce.
DELETE FROM sys_option_item
 WHERE option_set_code = 'SubscriptionStatus'
   AND item_code = 'Scheduled';

--    …and from the studio mirror where one exists. Guarded because design_option_item is optional: a runtime
--    without studio-starter has no mirror, and an unconditional DELETE would fail the whole migration there.
SET @has_design_mirror := (SELECT COUNT(*) FROM information_schema.TABLES
                            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'design_option_item');
SET @sql := IF(@has_design_mirror > 0,
    'DELETE FROM design_option_item WHERE option_set_code = ''SubscriptionStatus'' AND item_code = ''Scheduled''',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
