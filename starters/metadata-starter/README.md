# Softa Metadata Starter

Annotation-driven metadata management for Softa applications. Entities and
their fields are described in Java annotations (`@Model` / `@Field` /
`@OptionSet` / `@OptionItem` / `@Index`); a boot-time scanner reconciles the
annotations with `sys_*` catalog rows and, for the packages listed in
`scanner-scope`, applies the corresponding DDL automatically.

## Quick start

### 1. Add the dependency

```xml
<dependency>
    <groupId>io.softa</groupId>
    <artifactId>metadata-starter</artifactId>
    <version>${softa.version}</version>
</dependency>
```

### 2. Annotate your entity

```java
import io.softa.framework.orm.annotation.*;
import io.softa.framework.orm.entity.AuditableModel;

@Data
@EqualsAndHashCode(callSuper = true)
@Model(
    label = "Customer",
    businessKey = {"code"},
    description = "Customer master"
)
@Index(indexName = "uk_customer_code", fields = {"code"}, unique = true)
@Index(fields = {"status", "createdTime"})
public class Customer extends AuditableModel {

    @Field(label = "ID")
    private Long id;

    @Field(label = "Customer Code", required = true, length = 32)
    private String code;

    @Field(label = "Customer Name", required = true, length = 100)
    private String name;

    @Field(label = "Tier")
    private CustomerTier tier;       // enum → FieldType.OPTION (auto-inferred)

    @Field(label = "Status")
    private String status;

    @Field(label = "Email")
    private String email;
}
```

### 3. Set `scanner-scope` in your dev profile (NEVER non-empty in prod)

```yaml
# application-dev.yml
system:
  metadata:
    scanner-scope:
      - "*"          # manage every package; on a shared dev DB, narrow to
                     # your own packages, e.g. ["io\\.acme\\.app.*"]
```

### 4. Boot the app

```
MetadataAnnotationScanner: scanner-scope active (matchAll=true), scanning classpath...
MetadataAnnotationScanner: 12 in-scope @Model class(es), 1 in-scope @OptionSet enum(s) (of 12 / 1 on classpath)
MetadataAnnotationScanner: applied N row change(s) to sys_*
DdlOrchestrator: CREATE TABLE customer OK
DdlOrchestrator: applied 1 DDL statement(s); 0 drop operation(s) deferred to manual SQL
```

Then `SELECT model_name, app_code FROM sys_model WHERE app_code = '<system.app-code>';`
returns the built-in `SYSTEM_MODEL`s plus your annotated entities for this
runtime app.

## The 5 annotations

`@Model` / `@Field` / `@Index` live in `io.softa.framework.orm.annotation`;
`@OptionSet` / `@OptionItem` live in `io.softa.framework.base.annotation` — so
framework enums in `softa-base` (e.g. `Language`) can carry them without a
module cycle.

| Annotation | Target | Purpose |
|---|---|---|
| `@Model` | class | Describes an entity (table, business key, id strategy, multi-tenancy, soft delete, etc.) |
| `@Field` | field | Describes a column (label, type, required, length, related model, etc.) |
| `@OptionSet` | enum class | Marks an enum as a managed option set |
| `@OptionItem` | enum constant | Describes a single option (display name, sequence, tone, icon) |
| `@Index` | class (`@Repeatable`) | Declares a database index (fields, unique, globally-unique name, optional unique-violation message) |

**Key inference rules** (the parser does heavy lifting; you mostly don't
need to specify):

- `modelName` = class `getSimpleName()`
- `fieldName` = Java field name
- `tableName` = `snake_case(modelName)`
- `columnName` = `snake_case(fieldName)`
- `fieldType` = Java type → maps via `TypeInference` table
  (`String → STRING`, `Integer → INTEGER`, `enum → OPTION`,
  `List<enum> → MULTI_OPTION`, `@Model POJO → MANY_TO_ONE`,
  `DTOFieldObject POJO → DTO`, etc.)
- `TEXT` (unbounded long text — MySQL MEDIUMTEXT / PostgreSQL TEXT) is never
  inferred: declare `@Field(fieldType = FieldType.TEXT)` on a `String` field.
  `length` is optional and only an app-level guard (the column is unbounded).
- `optionSetCode` = enum class `getSimpleName()` (always derived; not declarable)
- `itemCode` = `@JsonValue` field/method value on the enum constant
- `OPTION` / `MULTI_OPTION` cannot be written explicitly — only inferred
- Index name = `idx_<table>_<col1>_<col2>...` or `uk_<table>_<col1>_<col2>...`
- Explicit `tableName`, `columnName`, and `indexName` values must satisfy
  `StringTools.isTableOrColumn` and must not be SQL reserved words because DDL
  renders identifiers unquoted.

## Runtime catalog identity (`app_code`)

The runtime `sys_model` / `sys_field` / `sys_option_set` /
`sys_option_item` / `sys_model_index` catalog no longer carries an
`ownership` column. The ownership tier was retired before the current baseline;
see `deploy/migrations/README.md` for the removed V1/V3/V7 migrations.

Every app-scoped runtime metadata row is instead stamped with `app_code` from
`system.app-code`. This is the catalog identity used by scanner writes,
runtime export/apply APIs, checksum comparison, and FK backfill SQL. A single
app may use multiple databases, and multiple apps may share one database, as
long as each app keeps a distinct `app_code` and physical table names do not
collide.

`scanner-scope` remains a package filter for Java annotation reconciliation,
not an ownership tier. In development the scanner materializes the annotated
catalog for this runtime app; in production Studio/connector publish applies
the app-scoped design catalog. Per-tenant runtime metadata customization is not
represented as separate `sys_*` rows.

## `scanner-scope` behavior matrix

`scanner-scope` is a list of regex patterns full-matched against each
`@Model`/`@OptionSet` class's package name. `"*"` (sole entry) = all packages;
empty / unset = manage nothing.

| `system.metadata.scanner-scope` | Scanner runs | DDL execution | Drift detection |
|---|---|---|---|
| `["*"]` | Boot-time, eager, all packages | Auto: `CREATE TABLE` / `ADD COLUMN` / `MODIFY COLUMN` / `ADD INDEX`. **Never auto-DROP** | Code-less catalog roots named in a WARN with copy-paste SQL |
| `["io\\.acme\\.foo.*", …]` | Boot-time, in-scope packages only | Same auto-policy, in-scope models only | n/a |
| empty / unset (default, prod) | n/a | n/a | `MetadataAnnotationChecker` runs post-boot on a virtual thread; logs WARN if code-vs-DB drift detected, audits the physical schema against `sys_*` (see Physical recovery), and records the `GET /metadata/status` snapshot |

On a **shared dev database**, give each developer a narrow scope (their own
packages) so the scanner only reconciles the Java packages they are actively
changing. Scope is per-package, not per-class, and it is not an ownership
barrier; app identity is still `app_code`, and physical table-name collisions
remain a database-level concern.

### Catalog row policy (what the scanner writes to `sys_*`)

The catalog is an aggregate: `sys_model` / `sys_option_set` are the **roots**, `sys_field` /
`sys_model_index` / `sys_option_item` their attributes.

| Change | Applied |
|---|---|
| Root added / modified (model, option set) | ✅ |
| Attribute added / modified / **removed**, on a root whose class is present | ✅ — the annotations own the root's attribute set |
| **Root removed** (a `sys_model` / `sys_option_set` row with no `@Model` / `@OptionSet` class) | ❌ under **every** scope, `["*"]` included — the root and all its attribute rows are left untouched; `["*"]` logs a WARN naming them with copy-paste `DELETE` SQL |

Rationale, and why this differs from a Java-class deletion being "obvious drift": a code-less root
is a **first-class** state here — the Studio no-code lane and metadata seed files author models
that never have a Java class, and nothing in the catalog records row ownership (the `Ownership`
enum is retained but unused). So "orphan" and "deliberately code-less" are indistinguishable, and
auto-deleting would silently destroy hand-authored definitions on every boot. Same asymmetry as
the physical schema below: grow automatically, destroy only when a human says so.

**DDL auto-execute policy**:

| Operation | Auto-executed |
|---|---|
| `CREATE TABLE IF NOT EXISTS` | ✅ |
| `ADD COLUMN` | ✅ |
| `MODIFY COLUMN` (type / nullable / length / default) | ✅ when the physical comparison says EQUAL / WIDEN (or no physical facts are available) |
| `MODIFY COLUMN` that would **narrow** the physical column (or the type families are incomparable) | ❌ — a MODIFY re-states the full column definition, so even a comment-only change could truncate a physically wider column; logs WARN with copy-paste SQL |
| `ADD INDEX` | ✅ |
| `DROP TABLE` / `DROP COLUMN` / `DROP INDEX` | ❌ — logs WARN with copy-paste SQL |

Rationale: additive DDL doesn't lose data; `DROP` operations are destructive
and may take minutes on large tables. Even in dev, you should consciously
choose to drop schema.

### Physical recovery

The diff is computed against `sys_*`, which a hand-touched database can leave
out of step with the physical schema — a planned `MODIFY` can target a
hand-dropped column, planned ALTERs a hand-dropped table, a planned `CREATE` a
pre-existing table. The orchestrator therefore snapshots the managed tables via
`DatabaseMetaData` before rendering and prepends **additive-only** recovery DDL
(labelled `[physical-recovery]` in the logs). The snapshot runs on **every**
boot (the drift audit needs it too) and costs a constant number of round trips
regardless of model count — tables and columns are each one catalog-wide
metadata pass filtered in memory, index names one dialect query (MySQL /
PostgreSQL; other engines fall back to one `getIndexInfo` per table). It is
logged as `physical snapshot = N managed table(s)`. There is no switch for this —
posture follows `scanner-scope` alone (active scope recovers, empty scope
audits read-only), and an introspection failure degrades gracefully:

| Drift | Recovery |
|---|---|
| `MODIFY COLUMN` targets a physically missing column | `ADD COLUMN` from the code definition first; the original `MODIFY` then re-asserts |
| ALTERs target a physically missing table | full `CREATE TABLE` from the code definition first |
| `CREATE TABLE` targets a pre-existing table | the CREATE degrades to already-applied; missing declared columns / indexes are added (adoption) |
| Declared rename whose old + new columns are both gone | `ADD COLUMN` of the new shape; the `CHANGE` degrades |
| `MODIFY COLUMN` whose physical comparison says NARROW / INCOMPARABLE | deferred to the warn-only SQL block (widen freely, never narrow silently) |

Type comparison is by `java.sql.Types` **equivalence class** on both sides (the
declared side through `FieldType.getSqlType()` / the TO_ONE FK's resolved
mirror; the observed side through the same reverse map the studio JDBC
connector uses) — never by parsing engine type-name strings. Within a class,
widths compare numerically (declared TEXT/JSON/DTO and physical TEXT/CLOB count
as unbounded); `INTEGER ⊂ LONG ⊂ BIG_DECIMAL` may widen along the lattice; a
declared BOOLEAN accepts an integer-class column (MySQL renders BOOLEAN as
TINYINT). Anything the comparison cannot classify degrades to
report-not-act. Non-EQUAL verdicts also appear in the drift audit's
TYPE MISMATCH section on every boot, so a deferred narrowing stays visible
after its one-time WARN.

The originally planned statements always still run after the recovery unit, so
a stale introspection can only add degrade-to-WARN noise — never lose a change.
Physical-only extras (columns/tables the code doesn't declare) are never
touched, and nothing here changes the no-auto-DROP policy. Introspection
failure logs a WARN and falls back to plain diff-driven planning (in which
genuine drift behind a planned statement fails the boot, exactly the
pre-recovery behavior — the narrowing guard likewise cannot judge without
facts and lets the MODIFY through).

**Whole-catalog drift audit + `GET /metadata/status`**: the same snapshot also
feeds a consolidated physical health report on every boot — declared tables /
columns / indexes that are physically missing, plus undeclared extras found on
managed tables (hand-added, or orphaned by a warn-only DROP) — logged in one
block by the scanner (active scope, audited against the from-code metadata) or
the checker (empty scope, audited against `sys_*`, read-only). Recovery only
heals declared-missing entries that sit behind the current diff; everything
else stays in the report until acted on. The boot snapshot — code vs catalog
fingerprints (SHA-256 over the per-aggregate checksums the studio handshake
uses) plus the drift report — is served by `GET /metadata/status`, so "did my
change reach this runtime?" is one call.

### Field / model rename — declare `renamedFrom`

The scanner uses **set-based comparison** keyed by `fieldName` / `modelName` /
`itemCode`, so an *undeclared* rename looks identical to "drop old + add new":
ADD COLUMN (auto) + WARN-only DROP → both columns coexist, old keeps the data,
new is NULL = **silent data divorce**.

Declaring the prior name fixes this:

```java
@Field(label = "External ID", renamedFrom = "legacyId")   // single-step; prior fieldName only
private Long externalId;
```

The parser carries the prior name, the `DiffEngine` pairs the two sides into one
`Modification(kind=RENAME)`, and the scanner **auto-executes** `CHANGE COLUMN`
(PostgreSQL: `RENAME COLUMN`) and updates the `sys_field` row in place by its
surrogate `id` — data is moved, not divorced. `@Model(renamedFrom = "OldName")` on
a type auto-executes `ALTER TABLE old RENAME TO new` and cascades `model_name` onto
every `sys_field` / `sys_model_index` row (no field churn). A half-applied rename —
both the new and prior name present — fails fast until resolved manually.

**Still hand-migrate** (`renamedFrom` does not cover these):
- Renaming a `@OptionItem` `@JsonValue` code (item_code + business rows) — explicit
  `UPDATE` migration.
- A rename entangled with a data transform (type change, split/merge).

See the *Manual migrations* section of
[annotation-lane.md](../../docs/ai/framework/annotation-lane.md).

## Configuration

Optional: MQ topic config for change notifications (if `message-starter` is
on the classpath):

```yaml
mq:
  topics:
    inner-broadcast:
      topic: dev_demo_inner_broadcast
      sub: dev_demo_inner_broadcast_sub
```
