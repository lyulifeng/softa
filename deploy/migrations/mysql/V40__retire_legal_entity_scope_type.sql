-- Retire the LEGAL_ENTITY data-scope type.
--
-- Numbered V40, not V35, although V35 is the next free name in this directory: the framework's
-- migrations for this feature were delivered through the deploying app instead, and
-- zingkey-hcm/deploy/hcm-app/init_mysql already carries softa-V35 … softa-V39. Reusing V35 here would
-- make "run V35 before the new jar" name two different scripts, one of which many environments have
-- already applied.
--
-- Why it goes: it compiled to `legal_entity_id = USER_COMP_ID` — the company the *caller* belongs to —
-- so a single role behaved differently for each holder: an HR in company A saw all of A's records, the
-- same role held in company B saw B's. Which companies a role may reach is a property of the role, and
-- it is now configured as the data scope on the company model itself (`role_data_scope.model =
-- 'LegalEntity'`), which the snapshot resolves into the grant that bounds every multi-company read.
--
-- WHY THIS SCRIPT IS NOT OPTIONAL. Dropping the enum constant alone is not a no-op on stored data, and
-- it fails in the dangerous direction. The snapshot parses these rows with `ScopeType.valueOf(...)` and
-- *silently skips* a name it does not recognise, so a stored LEGAL_ENTITY rule becomes no rule at all —
-- and a model with no rule is not narrowed. A role that was bounded to one company would quietly widen
-- to every company, with nothing logged and nothing on screen. Removing the seed entry does not help
-- either: seed loading is create-or-update and never deletes, so `data_scope_type` keeps its row and the
-- wizard keeps offering a type the runtime can no longer honour.
--
-- ORDERING: run this BEFORE booting the patched binary. Between the two, roles relying on the retired
-- type are unbounded on the company axis.
--
-- Section 1 is a report and changes nothing — run it first and read it. Section 2 is safe and
-- unconditional. Section 3 is the decision, and is deliberately left commented out: it is the only part
-- that can change what an existing role sees, and which way to go is a per-deployment call. On a fresh
-- install or any environment where nobody configured this type, section 1 returns no rows and sections
-- 2/3 are no-ops.

-- ── 1. Report: who is affected ────────────────────────────────────────────────────────────────────
-- Every role scope mentioning the retired type, split by whether it is the only rule on that row.
-- `sole_rule = 1` is the set that needs an administrator: those roles were bounded to the holder's own
-- company and nothing else, so there is no rule left to bound them once it goes.
SELECT rds.tenant_id,
       rds.role_id,
       r.name AS role_name,
       rds.model,
       JSON_LENGTH(rds.data_scopes) = 1 AS sole_rule,
       rds.data_scopes
FROM role_data_scope rds
         LEFT JOIN role r ON r.id = rds.role_id
WHERE JSON_SEARCH(rds.data_scopes, 'one', 'LEGAL_ENTITY') IS NOT NULL
ORDER BY sole_rule DESC, rds.tenant_id, rds.role_id;

-- Roles that already name their companies, and so need nothing beyond section 2.
SELECT DISTINCT rds.role_id
FROM role_data_scope rds
WHERE JSON_SEARCH(rds.data_scopes, 'one', 'LEGAL_ENTITY') IS NOT NULL
  AND EXISTS (SELECT 1
              FROM role_data_scope company
              WHERE company.role_id = rds.role_id
                AND company.tenant_id = rds.tenant_id
                AND company.model = 'LegalEntity'
                AND JSON_LENGTH(company.data_scopes) > 0);

-- ── 2. Strip the rule where other rules remain ────────────────────────────────────────────────────
-- Safe on its own terms: the row keeps at least one rule, so the model stays bounded by that rule
-- rather than falling through to unnarrowed. Rules are OR-combined, so removing one can only narrow.
--
-- One statement per position because MySQL cannot remove array elements by predicate; three passes
-- cover a rule list of any length in practice (the wizard writes at most a handful) and each is a no-op
-- once the type is gone. Runs before the delete in section 3 so a re-run is idempotent.
UPDATE role_data_scope
SET data_scopes = JSON_REMOVE(data_scopes,
                              JSON_UNQUOTE(REPLACE(JSON_SEARCH(data_scopes, 'one', 'LEGAL_ENTITY'),
                                                   '.scopeType', '')))
WHERE JSON_SEARCH(data_scopes, 'one', 'LEGAL_ENTITY') IS NOT NULL
  AND JSON_LENGTH(data_scopes) > 1;

UPDATE role_data_scope
SET data_scopes = JSON_REMOVE(data_scopes,
                              JSON_UNQUOTE(REPLACE(JSON_SEARCH(data_scopes, 'one', 'LEGAL_ENTITY'),
                                                   '.scopeType', '')))
WHERE JSON_SEARCH(data_scopes, 'one', 'LEGAL_ENTITY') IS NOT NULL
  AND JSON_LENGTH(data_scopes) > 1;

UPDATE role_data_scope
SET data_scopes = JSON_REMOVE(data_scopes,
                              JSON_UNQUOTE(REPLACE(JSON_SEARCH(data_scopes, 'one', 'LEGAL_ENTITY'),
                                                   '.scopeType', '')))
WHERE JSON_SEARCH(data_scopes, 'one', 'LEGAL_ENTITY') IS NOT NULL
  AND JSON_LENGTH(data_scopes) > 1;

-- The type itself: the wizard reads this table, so leaving the row would keep offering a scope the
-- runtime silently drops. Unconditional — the row is framework seed data, not tenant configuration.
DELETE FROM data_scope_type WHERE id = 'LEGAL_ENTITY';

-- ── 3. The decision: rows where it was the ONLY rule ──────────────────────────────────────────────
-- Pick one and uncomment it. Doing nothing leaves those rows carrying a rule the runtime drops, which
-- is the widening this script exists to prevent — so this is not a section to skip, only to choose.
--
-- (a) FAIL CLOSED — the role sees nothing on that model until an administrator configures the legal
--     entities it may reach. Visibly broken beats invisibly widened for a permission boundary, and the
--     save path now refuses to store the ambiguous configuration, so the fix is a guided one. Choose
--     this unless someone is standing by to reconfigure the roles listed in section 1.
--
-- UPDATE role_data_scope
-- SET data_scopes = JSON_ARRAY(JSON_OBJECT('scopeType', 'CUSTOM', 'scopeExpr', JSON_ARRAY(JSON_ARRAY('id', 'IN', JSON_ARRAY()))))
-- WHERE JSON_SEARCH(data_scopes, 'one', 'LEGAL_ENTITY') IS NOT NULL
--   AND JSON_LENGTH(data_scopes) = 1;
--
-- (b) DROP THE ROW — the model falls back to unnarrowed on the company axis for that role. Only
--     defensible where the role's other scopes already bound it, or where the tenant has exactly one
--     legal entity and the axis is meaningless. This is the widening; it is written out rather than
--     hidden so that choosing it is deliberate.
--
-- DELETE FROM role_data_scope
-- WHERE JSON_SEARCH(data_scopes, 'one', 'LEGAL_ENTITY') IS NOT NULL
--   AND JSON_LENGTH(data_scopes) = 1;

-- ── 4. Verify ─────────────────────────────────────────────────────────────────────────────────────
-- Expect zero rows. A non-zero count means section 3 was skipped: those roles are the ones that widen.
SELECT COUNT(*) AS rules_still_referencing_retired_type
FROM role_data_scope
WHERE JSON_SEARCH(data_scopes, 'one', 'LEGAL_ENTITY') IS NOT NULL;
