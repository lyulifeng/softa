# Permission Starter

Enforce-side permission SDK: a **route-admission interceptor** + a **data-scope
engine** + the data-plane `PermissionService` implementation + the **endpoint /
sensitive-field caches**. It carries **no login / RBAC** of its own — it consumes
a `PermissionInfo` snapshot and the RBAC config through SPIs, so a service can
enforce permissions without bundling user management.

```xml
<dependency>
  <groupId>io.softa</groupId>
  <artifactId>permission-starter</artifactId>
  <version>2.0.3</version>
</dependency>
```

## Dependency & the hard invariant

`permission-starter` depends only on [`softa-web`](../../framework/softa-web/README.md)
(→ `softa-orm` → `softa-base`). It **must not** depend on
[`user-starter`](../user-starter/README.md).

```
softa-orm  ←  permission-starter  ←  user-starter
```

The layering is acyclic: `user-starter` depends on `permission-starter` (it uses
the engine + caches and implements the SPIs), never the reverse. The invariant
**`permission-starter ⊥ user-starter`** is what lets a pure-enforce microservice
ship the interceptor + engine with **zero** login / OAuth / RBAC.

## What you get

`PermissionStarterAutoConfiguration` is an `@AutoConfiguration` that
`@ComponentScan`s `io.softa.starter.permission`. Put the jar on the classpath and
you get, auto-wired:

- **`PermissionInterceptor`** — route admission (coarse: does this user hold a
  permission mapped to this endpoint?), registered on `/**`.
- **The scope engine** — `ScopeRuleCompiler` + `ScopeApplicabilityResolver` +
  `IdentityScopeCompiler` (data-driven identity scopes) + `ScopeContributor`
  implementations → row-level `Filters`.
- **`EndpointIndex`** (endpoint → required permission) and
  **`SensitiveFieldSetCache`** (model → masked fields), each built once at
  `@PostConstruct` from an SPI (seed data; redeploy to refresh).
- **`EndpointCoverageValidator`** — startup (`ApplicationReadyEvent`) check that
  every Spring MVC handler URL is covered by a permission in the `EndpointIndex`
  (or a public / authenticated-bypass pattern); log-only, never fail-fast.
- **`PermissionServiceImpl`** — the data-plane `PermissionService` that
  `ModelServiceImpl` calls transparently at the CRUD boundary for scope
  row-filtering, field masking and write guards; registered via
  `@Bean @ConditionalOnMissingBean` (an app may override, e.g. a no-op stub).

## The SPI seams

`permission-starter` defines the **contract**; someone else provides the
**data**. `PermissionService` lives in `softa-orm` (because `ModelServiceImpl`
calls it directly); the rest live in `io.softa.starter.permission.spi`.

| SPI | Location | Purpose | Default (this module) / real impl |
|---|---|---|---|
| `PermissionService` | `softa-orm` | data-plane scope / mask / write guard | `PermissionServiceImpl` |
| `ScopeContributor` | `permission-starter.spi` | one scope type → `Filters` | code contributors (Custom / DepartmentSubtree / ManagedDepartments) |
| `PermissionSnapshotProvider` | `permission-starter.spi` | `(tenantId, userId) → PermissionInfo` | `DefaultPermissionSnapshotProvider` (builds via 约定读) — or `RedisPermissionSnapshotProvider` (read-only) for pure-enforce |
| `PermissionEndpointSource` | `permission-starter.spi` | endpoint → permission rows | `DbPermissionEndpointSource` |
| `SensitiveFieldSetSource` | `permission-starter.spi` | sensitive-field-set defs | `DbSensitiveFieldSetSource` |

Every SPI ships a **`@ConditionalOnMissingBean` default in this module**, so
permission-starter is self-sufficient: `DefaultPermissionSnapshotProvider` builds
the per-user snapshot from the RBAC config models **by name** (约定读 into view
DTOs), and the `Db*` sources read the endpoint / sensitive-field-set config the
same way. `user-starter` implements **none** of these SPIs — it is fully ⊥ of the
engine (no compile dependency in *either* direction, main or test). An app that
needs different behaviour (a pure-enforce microservice with an RPC re-sourcer, a
no-op stub, …) registers its own bean and the `@ConditionalOnMissingBean` steps
aside.

## Package layout

```
config/       PermissionStarterAutoConfiguration
spi/          PermissionSnapshotProvider · PermissionEndpointSource(+Def)
              SensitiveFieldSetSource(+Def) · ScopeContributor
              PermissionInfo · ScopeRule · ScopeType   (snapshot + scope value types)
spi/support/  AbstractCacheAsideSnapshotProvider · RedisPermissionSnapshotProvider
              DefaultPermissionSnapshotProvider (约定读 build, monolith default)
              DbPermissionEndpointSource · DbSensitiveFieldSetSource · JsonCoerce
scope/        ScopeRuleCompiler · ScopeApplicabilityResolver · IdentityScopeCompiler · ScopeFilterTemplates
              DataScopeType (+ data-system/DataScopeType.Builtin.json) · DataScopeTypeReader
              + org-scope support (EmployeeContextEnricher, Department*Resolver, PermissionScopeConfig)
scope/contributor/  Custom · DepartmentSubtree · ManagedDepartments
index/        EndpointIndex · EndpointCoverageValidator
sensitive/    SensitiveFieldSetCache
interceptor/  PermissionInterceptor · PermissionInterceptorProperties · PermissionWebMvcConfig
service/      PermissionServiceImpl
```

## Snapshot re-sourcing

The per-user snapshot is cached under a canonical key — the single source of
truth is `PermissionSnapshotProvider.userSnapshotKey(tenantId, userId)` →
`perm:{tenantId}:user:{userId}`. The producer (`DefaultPermissionSnapshotProvider`)
and every reader use it (user-starter mirrors the shape in `PermissionSnapshotKey`
for its own cache reads / evictions), so the key can never drift.

**Monolith default** — `DefaultPermissionSnapshotProvider` (standalone; its `get()`
carries `@SkipPermissionCheck` so the config reads bypass scope filtering) is a
3-tier cache-aside: request-scoped → Redis (`perm:`, TTL 1h) → **build** from the
RBAC config models by name (约定读 into view DTOs). A user with no active roles
gets a non-null empty-grants snapshot; only a build failure (RBAC models absent /
DB error) fails closed to null.

**Pure-enforce re-sourcing** — `AbstractCacheAsideSnapshotProvider` is the reusable
skeleton (`get()` is `final`):

```
read cache ─ hit  → return
           └ miss → single-flight → resolveOnMiss(t,u) → back-fill → return
                    (any failure → null → caller fails closed)
```

Its one shipped subclass, `RedisPermissionSnapshotProvider`, has `resolveOnMiss →
null` (a fail-closed cache reader — correct when a principal keeps the cache warm).
**Cross-process re-sourcing is deliberately out of the framework**: a pure-enforce
microservice provides its own subclass — forward to the principal's
`GET /me/uiContext`, an internal RPC, a local DB rebuild, … Transport, discovery
and service-to-service auth are **deployment** concerns; the SPI + template are the
framework's whole contribution.

`PermissionEndpointSource` / `SensitiveFieldSetSource` defaults
(`DbPermissionEndpointSource` / `DbSensitiveFieldSetSource`) read the
`Permission` / `SensitiveFieldSet` config by **model name** (convention read,
guarded by `ModelManager.existModel`). These are boot-time config reads, so a
standalone service with DB access needs no extra wiring.

## Scope engine

Scope **types are data**; their compilation is **code only where it must be**.

- **Type registry (data)** — `DataScopeType` (seed `data-system/DataScopeType.Builtin.json`)
  holds each scope type's metadata: `appliesToAll` (ALL / CUSTOM); for **identity
  types** a `filter` template (+ `identityModel` / `identityFilter` for the model-swap);
  for **code-contributor types** an explicit `applicableFields`.
  `ScopeApplicabilityResolver` reads it (via `DataScopeTypeReader`) to answer "which
  scope types apply to model X" — for identity types deriving the applicable fields
  from the `filter` template's field references (`ScopeFilterTemplates.fieldRefs`), so
  applicability and compilation share one source. The role wizard in user-starter
  derives the same answer by reading `DataScopeType` by name, with no engine dependency.
- **Identity scopes → data-driven filter template** — `SELF` / `DIRECT_REPORTS` /
  `CREATED_BY_SELF` / `LEGAL_ENTITY` are all `<field> = <a value from the caller's
  identity context>`, expressed as a `filter` template whose leaf value is an
  `EnvConstant` placeholder (e.g. `["employeeId","=","USER_EMP_ID"]`).
  `IdentityScopeCompiler` emits that template as a `Filters`; **the placeholder is
  resolved at SQL-build time by `FilterUnitParser`** — the same path `CUSTOM` uses, so
  there is no bespoke `principalSource → value` switch. Model-swap uses `identityFilter`
  on the `identityModel` (e.g. `SELF` filters `id` on `Employee`, `employeeId`
  elsewhere). It fails closed (`new Filters()`) when a required identity value is
  absent — an `EMP_INFO` token (`USER_EMP_ID` / `USER_COMP_ID` / …) with no `EmpInfo`,
  or `USER_ID` with no userId (object-presence check keyed on `EnvConstant`; the value
  itself stays with `FilterUnitParser`). Adding such a type = **an enum value + one
  seed row, no compilation code** (see [Adding a scope type](#adding-a-scope-type)).
- **Code contributors** — types needing runtime I/O or arbitrary expressions keep a
  `ScopeContributor`: `DepartmentSubtree` / `ManagedDepartments` (resolve deptId →
  idPath via `DepartmentIdPathResolver`, build a subtree prefix filter) and `Custom`
  (evaluate an admin-authored `scopeExpr`). These read `Employee` / `Department` by
  model name via `ModelService`; `ModelManager.existModel` lets a non-HR app degrade.

`ScopeRuleCompiler` dispatches each rule: `ALL` → no filter → a registered
`ScopeContributor` → its `compile` → the `IdentityScopeCompiler` data path →
fail-closed. Fail-closed for an inapplicable / empty rule is `WHERE 1=0`
(`ScopeRuleCompiler.matchNone()`), never "no filter".

### Adding a scope type

**Identity-shaped** (`<field> = <a value from the caller's context>`) — no
compilation code, two declarations:

1. A `ScopeType` enum value (the stored `scopeType` string is validated by
   `ScopeType.valueOf`; unknown values are skipped, so this is required):
   ```java
   MY_PROJECTS("MyProjects", "Projects I lead")
   ```
2. A `DataScopeType.Builtin.json` seed row with a `filter` template:
   ```json
   { "id": "MY_PROJECTS", "name": "My Projects", "sortOrder": 35,
     "filter": ["projectLeadId", "=", "USER_EMP_ID"] }
   ```

A grant `{"scopeType":"MY_PROJECTS"}` on a model carrying `projectLeadId` then
compiles to `WHERE project_lead_id = <caller.empId>` via `IdentityScopeCompiler` +
`FilterUnitParser` — no contributor, no compiler change (applicability is derived
from the template's `projectLeadId` reference). Need a per-model anchor swap? add
`identityModel` + `identityFilter`. Need an admin-fixed value? author a `CUSTOM`
grant with the value baked into its `scopeExpr` (no longer a type-level column).

> **Why the enum too?** `parseScopeRules` validates the stored code via
> `ScopeType.valueOf` and `ScopeRuleCompiler` dispatches on an `EnumMap`, so a code
> with no enum value is silently dropped. Keeping the enum (rather than a plain
> String code — an intentionally-deferred design, see the split ADR's decision ④)
> is a deliberate trade: `DataScopeType` is **seed data (redeploy to refresh)**, so
> the enum value and the seed row are *both* build-time anyway — dropping the enum
> would save one line, not unlock runtime/no-deploy scope-type addition (that also
> needs `DataScopeType` writable at runtime). The enum buys compile-time safety for
> the code-referenced `ALL` / `CUSTOM` (special-cased in the compiler) and a
> canonical registry of the types the code knows.

The `filter` leaf placeholder must be one the framework already resolves — `USER_ID`
/ `USER_EMP_ID` / `USER_POSITION_ID` / `USER_DEPT_ID` / `USER_COMP_ID` / `NOW` /
`TODAY` / `YESTERDAY` (`EnvConstant` + `FilterUnitParser.convertEnvParameter`). A
**new** principal attribute needs a `Context` / `EmpInfo` field + an `EnvConstant`
token + a `FilterUnitParser` case first — one shared framework registry (also used by
`CUSTOM`), not a per-type switch.

**Anything else** (runtime DB lookup, subtree, arbitrary expression) — write a
`ScopeContributor` (`@Component`; `scopeType()` + `compile()`). The enum value is
still required; the `DataScopeType` row then carries only `applicableFields` (no
`filter`). See `DepartmentSubtreeScopeContributor`.

## Configuration

`PermissionInterceptorProperties` (prefix `permission`):

```yaml
permission:
  public-uri-patterns:            # bypass the interceptor entirely (login / health / …)
    - /login
    - /actuator/health
  authenticated-bypass-patterns:  # authenticated but no permission needed
    - /me/**                      # returns the caller's own data
```

Framework infrastructure (`/error`, `/actuator/**`, `/swagger-ui/**`,
`/v3/api-docs/**`, `/favicon.ico`) is excluded from the interceptor out of the
box.

## Route admission (`PermissionInterceptor.preHandle`)

1. Public URI → allow.
2. No authenticated user → reject.
3. Authenticated-bypass pattern → allow (caller's own data).
4. Super-admin → allow.
5. Endpoint → required permission via `EndpointIndex`; **unmapped endpoint → 403**
   (unknown URLs are denied, not opened).
6. Snapshot holds a required permission? allow, else 403.

Fine-grained enforcement (row scope + field masking + write guards) then runs in
the data plane via `PermissionService`, transparently inside `ModelServiceImpl`.
