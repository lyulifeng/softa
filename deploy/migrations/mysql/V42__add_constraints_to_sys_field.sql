-- Add the field-constraints column to the metadata catalog: sys_field plus its studio mirror
-- design_field. Matches the entity declaration (SysField.constraints / DesignField.constraints, a
-- FieldType.DTO column) introduced together with the @Field(min / max / pattern / constraintMessage /
-- requiredWhen / hiddenWhen / readonlyWhen / invalidWhen) annotation attributes.
--
-- ONE column for the whole declaration, as canonical JSON:
--   {"message":"Headcount cannot be negative.","min":"0"}
--   {"requiredWhen":[["reason","=","Others"]]}
-- The value domain is enforced by the field processors every write path shares (create, update,
-- batch, import, seed loading, flow write nodes); the conditions by FieldConstraintsEnforcer before
-- the processor chain, on the patch merged onto the stored row. Nothing here is a CHECK: a rule is
-- tightened by redeploying, rows written before it stay valid. A new kind of constraint is a new
-- JSON key, not another column — this is the last migration of its kind.
--
-- The catalog is itself annotation-managed, so a non-empty scanner-scope auto-applies the sys_field
-- column on boot; this script converges the environments where nothing auto-applies — an empty
-- scanner-scope (checker-only, the production shape), and design_* in every environment, which the
-- boot reconcile never covers.
--
-- NO BACKFILL UPDATE. FIELD_ATTRS is derived reflectively from SysField, so the attribute joins the
-- cross-lane checksum the moment the column exists — but an undeclared constraint is null on the code
-- side (never "{}") and a freshly added column is null on the DB side. The two agree from the first
-- boot. (A boolean attribute such as V34's auto_sequence needed the backfill precisely because the
-- parser writes an explicit false and null does not hash as false.)
--
-- Ordering: run before booting the patched binary. SysJdbcLoader SELECTs the column explicitly; the
-- strict load fail-fasts on a missing column, and the lenient (checker) load degrades to an empty
-- catalog and reports everything as drift. Deployments without studio-starter have no design_*
-- tables — skip that section.

ALTER TABLE sys_field
    ADD COLUMN constraints MEDIUMTEXT COMMENT 'Value domain and conditional state / validity rules; see FieldConstraints' AFTER scale;

ALTER TABLE design_field
    ADD COLUMN constraints MEDIUMTEXT COMMENT 'Value domain and conditional state / validity rules; see FieldConstraints' AFTER scale;
