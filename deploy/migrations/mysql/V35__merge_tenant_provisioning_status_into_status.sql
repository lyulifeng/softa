-- Collapse the tenant's two lifecycle fields into one.
--
-- `tenant_info` carried an operational `status` (Draft / Active / Suspended / Closed) alongside a
-- separate `provisioning_status` (Initializing / Ready / Failed) for how far its setup had got. Two
-- fields meant "is this tenant usable" had two answers to reconcile, and every caller had to know
-- which one to ask. TenantStatus now holds both: Draft / Initializing / Active / Suspended / Closed.
--
-- The two dropped values do not become states of their own:
--   Ready   -> Active  (setup finished is exactly what Active means now)
--   Failed  -> Draft   (nobody treats "never set up" and "setup failed" differently — both mean
--                       "not built, press Rebuild", so one state carries both)
--
-- Order matters: the value mapping runs BEFORE the column is dropped, or the in-flight information
-- is gone. A tenant mid-setup that lost its Initializing marker would read as Active and admit
-- logins into a workspace whose roles are still arriving; one that lost Failed would read as Active
-- and never offer the rebuild that fixes it.
--
-- Only rows whose provisioning axis actually said something are touched. Ready and NULL (a tenant
-- created before the axis existed, seeded synchronously) already agree with whatever `status` says,
-- so they are left exactly as they are — including Suspended and Closed tenants, which are fully
-- built and must not be dragged back to Active by this merge.

-- 1. Mid-setup: carry the marker onto the surviving field.
UPDATE tenant_info
   SET status = 'Initializing'
 WHERE provisioning_status = 'Initializing';

-- 2. Failed setup: back to Draft, the state the rebuild action starts from.
UPDATE tenant_info
   SET status = 'Draft'
 WHERE provisioning_status = 'Failed';

-- 3. Drop the merged-away column.
ALTER TABLE tenant_info DROP COLUMN provisioning_status;

-- 4. Retire its catalog row. The scanner never auto-DROPs, so a leftover sys_field row would keep
--    describing a column that no longer exists — the API would advertise the field and reads of it
--    would fail. `tenant_info` is annotation-managed, so the row was written by the scanner and has
--    to be removed by hand here.
DELETE FROM sys_field
 WHERE model_name = 'TenantInfo'
   AND field_name = 'provisioningStatus';

--    …and from the studio mirror, which the same reasoning applies to: a leftover design_field row keeps
--    the field in the Studio workspace, where a later publish would re-add the column this script just
--    dropped. Guarded by an existence check because design_field is optional — a runtime without
--    studio-starter has no mirror, and an unconditional DELETE would fail the whole migration there.
SET @has_design_field := (SELECT COUNT(*) FROM information_schema.TABLES
                           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'design_field');
SET @sql := IF(@has_design_field > 0,
    'DELETE FROM design_field WHERE model_name = ''TenantInfo'' AND field_name = ''provisioningStatus''',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- The new Initializing option item is NOT inserted here. Adding an option is additive structure, which
-- the annotation lane self-applies: TenantStatus is an @OptionSet inside the scanner's tenant scope, so
-- the scanner writes the item and re-numbers the set's sequences from the enum's declared order. Doing it
-- by hand would mean re-implementing that ordering in SQL — and getting it wrong shows operators a
-- jumbled status picker. Only the two things the scanner will never do are here: transforming values,
-- and dropping what was removed.
