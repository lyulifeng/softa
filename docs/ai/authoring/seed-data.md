# Shipping Seed Data

Part of the [Softa app authoring guide](../README.md). How to ship reference data
(countries, currencies, default categories, …) as JSON files alongside your app.

These files are loaded **on demand** by an operator calling
`POST /SysPreData/loadPreSystemData` — they are **not** auto-loaded at boot. The
*table structure* comes from your entity annotations ([entities.md](entities.md));
this is just the *rows*.

Place files in your module's resources:
- `src/main/resources/data-system/*.json` — shared/platform data (all tenants)
- `src/main/resources/data-tenant/*.json` — per-tenant defaults (loaded via `/SysPreData/loadPreTenantData`)

---

## File naming

`<EntityName>.<Variant>.json`, e.g. `CountryRegion.AllCountries.json`,
`Currency.AllCurrencies.json`, `Department.Default.json`.

- `<EntityName>` **must** match a `@Model` class's simple name — the loader
  resolves rows to that entity.
- `<Variant>` is a free label to distinguish files; the loader ignores it.

## JSON format

Top-level object keyed by the model name, value is an array of row objects:

```json
{
  "CountryRegion": [
    { "id": "AD", "name": "Andorra", "alpha3Code": "AND",
      "dialCode": "376", "currencyCode": "EUR", "continent": "EU" },
    { "id": "AE", "name": "United Arab Emirates", "alpha3Code": "ARE",
      "dialCode": "971", "currencyCode": "AED", "continent": "AS" }
  ]
}
```

Rules:
- **Field names are the Java `@Field` names (camelCase)** — `dialCode`, not
  `dial_code`. The loader maps JSON → entity, then the ORM maps to columns.
- **Enum values use the `@JsonValue` code**, not the constant name:
  `"continent": "EU"` ✓, `"continent": "EUROPE"` ✗.
- **`id` is the seed `preId`, always a string.** The loader upserts by
  `(model, preId)` **within the loading scope** — system scope for
  `data-system` files, the loading tenant for `data-tenant` files — in
  `SysPreData`, not by `@Model.businessKey`. For
  `EXTERNAL_ID` models (code-as-id masters such as `CountryRegion` / `Currency`)
  the same value is also the business row's primary key. For generated-id models
  it is tracking-only: the loader removes it before insert, lets the ID strategy
  create the row id, then records the `preId -> rowId` mapping.
- **References use seed ids, resolved in the referenced model's scope.** For
  `MANY_TO_ONE` / `ONE_TO_ONE` / `MANY_TO_MANY` fields, put the referenced row's
  seed `id` (`preId`) in the JSON. The loader resolves it to the actual row id
  before writing, looking the binding up in the scope of the model being
  referenced — not the scope of the load. References may cross the tenancy
  boundary **in either direction**: a tenant seed pointing at a shared model (a
  currency, a country, a navigation), and a shared platform-side table pointing
  at a tenant's row (a ledger, a log, a record not exposed to tenants).
  One combination has no answer and is rejected: a **system-scope load cannot
  resolve a multi-tenant model's `preId`** — those bindings exist once per
  tenant and the load has no tenant to pick. Reference such a row by its
  **actual id** instead; a raw id needs no binding and is written through as-is.
  Consequence: **load system seeds before the tenant seeds that reference
  them** — a not-yet-loaded reference fails the whole file.
- **Required fields**: if `@Field(required = true)` and the row omits it, the load
  fails with a NOT NULL error.
- **Omit audit fields** (`createdTime`, `createdBy`, `updatedTime`, …) — the
  loader fills them.

---

## Recipes

**Add a row** — insert it in the right file (keep stable `id` order), validate,
build:
```json
{ "id": "SS", "name": "South Sudan", "alpha3Code": "SSD",
  "dialCode": "211", "currencyCode": "SSP", "continent": "AF" }
```
If it references data that doesn't exist yet (currency `SSP`), add that **first**
in its own file — there's no DB FK, so a dangling reference loads silently.

**Update a row** — edit the fields, keep `id` unchanged. Changing `id` changes
the seed `preId`, so the loader treats it as a different seed row. Reload applies
the UPDATE to the row currently bound to that `preId`.

**Per-tenant defaults** — put them in `data-tenant/` (not `data-system/`); they
load per tenant via `/SysPreData/loadPreTenantData`, stamping the caller's tenant.
Each tenant gets its own `preId` bindings, so the same file loads once per tenant
without touching other tenants' rows. When multi-tenancy is enabled, **what a file
may seed** is enforced in both directions — `data-tenant` files may only seed
`multiTenant = true` models, `data-system` files only shared models, and the loader
rejects a mismatch and rolls the file back. **What a row may reference** is not
restricted by tenancy at all — only by what can be resolved: a `preId` of a
multi-tenant model is unresolvable from a system-scope load (no tenant to pick),
so point at those rows by actual id.

---

## Verify

```bash
# JSON validity
for f in src/main/resources/data-system/*.json; do jq . "$f" >/dev/null && echo "OK: $f" || echo "FAIL: $f"; done
# duplicate seed preIds in a file
jq '.CountryRegion[].id' CountryRegion.AllCountries.json | sort | uniq -d
```
Then, after deploy, an operator loads the file(s) and checks the row count.

---

## Common mistakes

1. **`snake_case` field names in JSON** — use camelCase (the Java field name).
2. **`Enum.name()` instead of `@JsonValue`** — `"EUROPE"` won't deserialize; use `"EU"`.
3. **Including audit fields** — omit them; the loader manages them.
4. **Duplicate seed ids in one file** — inconsistent upsert; check with `jq`.
5. **Changing a seed `id` during an update** — this creates a new `preId`
   binding instead of updating the existing one.
6. **Referencing not-yet-loaded data** — add the referenced rows first.
7. **Editing JSON without re-validating** — a trailing comma is silent until load time.
8. **Wrong directory for the model's tenancy** — a `multiTenant = true` model in
   `data-system/`, or a shared model in `data-tenant/`, is rejected at load time
   when multi-tenancy is enabled.
9. **Loading tenant seeds before the system seeds they reference** — the
   reference resolves in the referenced model's scope, so the shared row has to
   exist first; otherwise the file fails with "preIDs … do not exist".
10. **A system seed naming a multi-tenant model's `preId`** — unresolvable, since
    that binding exists once per tenant; use the row's actual id.

Not for: test fixtures (use `src/test/resources/`), or data that needs
transformation from an old schema (write a service-layer migration job instead).
