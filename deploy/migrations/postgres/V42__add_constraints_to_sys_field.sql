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
-- NO BACKFILL UPDATE: an undeclared constraint is null on both sides from the first boot (see the
-- MySQL variant for why a boolean attribute is different).
--
-- Ordering: run before booting the patched binary. Deployments without studio-starter have no
-- design_* tables — skip that section.
--
-- PostgreSQL variant: IF NOT EXISTS covers the re-run and the environment that already ALTERed out
-- of band; there is no column ordering and COMMENT is a separate statement.

ALTER TABLE sys_field ADD COLUMN IF NOT EXISTS constraints TEXT;
COMMENT ON COLUMN sys_field.constraints IS 'Value domain and conditional state / validity rules; see FieldConstraints';

ALTER TABLE design_field ADD COLUMN IF NOT EXISTS constraints TEXT;
COMMENT ON COLUMN design_field.constraints IS 'Value domain and conditional state / validity rules; see FieldConstraints';
