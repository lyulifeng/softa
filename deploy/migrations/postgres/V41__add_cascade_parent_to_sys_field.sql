-- Add the `cascade_parent` column to the metadata catalog: sys_field plus its studio mirror
-- design_field. Matches the entity declarations (@Field(label = "Cascade Parent") on SysField /
-- DesignField) introduced together with the @Field(cascadeParent = ...) annotation attribute: the
-- flag marks a MANY_TO_ONE as the parent a dependent import-template dropdown narrows by (the level
-- a track belongs to, not the country it is filed under). See OptionDropdownResolver.
--
-- The catalog is itself annotation-managed, so a non-empty scanner-scope auto-applies the sys_field
-- column on boot; this script converges the environments where nothing auto-applies — an empty
-- scanner-scope (checker-only, the production shape), and design_* in every environment, which the
-- boot reconcile never covers.
--
-- The UPDATEs are not optional. FIELD_ATTRS is derived reflectively from SysField, so the new
-- attribute joins the cross-lane checksum the moment the column exists, and null does not hash as
-- false: a NULL design_field row makes its whole model aggregate read as drifted against a runtime
-- that parsed an explicit false. The same applies to sys_field rows on any environment whose column
-- was added without a default.
--
-- Ordering: run before booting the patched binary. SysJdbcLoader SELECTs the column explicitly; the
-- strict load fail-fasts on a missing column, and the lenient (checker) load degrades to an empty
-- catalog and reports everything as drift. Deployments without studio-starter have no design_*
-- tables — skip that section.
--
-- PostgreSQL variant: IF NOT EXISTS covers the re-run and the environment that already ALTERed
-- out of band; there is no column ordering and COMMENT is a separate statement.

ALTER TABLE sys_field ADD COLUMN IF NOT EXISTS cascade_parent boolean DEFAULT false;
COMMENT ON COLUMN sys_field.cascade_parent IS
    'Cascade Parent;The MANY_TO_ONE a dependent import-template dropdown narrows by';
UPDATE sys_field SET cascade_parent = false WHERE cascade_parent IS NULL;

ALTER TABLE design_field ADD COLUMN IF NOT EXISTS cascade_parent boolean DEFAULT false;
COMMENT ON COLUMN design_field.cascade_parent IS
    'Cascade Parent;The MANY_TO_ONE a dependent import-template dropdown narrows by';
UPDATE design_field SET cascade_parent = false WHERE cascade_parent IS NULL;
