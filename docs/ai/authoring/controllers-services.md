# REST Controllers & Services

Part of the [Softa app authoring guide](../README.md). Once you've defined an
entity ([entities.md](entities.md)), this is how you expose it over REST and add
business logic. For request/response payload shapes see [queries.md](queries.md).

The pattern is **three small classes per entity**: a controller, a service
interface, and a service implementation. You write almost no code — the base
classes provide full CRUD.

---

## The three classes

For an entity `EmpInfo` (PK type `Long`):

**1. Service interface** — `io.acme.myapp.service`
```java
import io.softa.framework.orm.service.EntityService;

public interface EmpInfoService extends EntityService<EmpInfo, Long> {
    // add custom business method signatures here (optional)
}
```

**2. Service implementation** — `io.acme.myapp.service.impl`
```java
import org.springframework.stereotype.Service;
import io.softa.framework.orm.service.impl.EntityServiceImpl;

@Service
public class EmpInfoServiceImpl
        extends EntityServiceImpl<EmpInfo, Long>
        implements EmpInfoService {
    // inherits all CRUD; add/override methods for custom logic
}
```

**3. Controller** — `io.acme.myapp.controller`
```java
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.softa.framework.web.controller.EntityController;

@RestController
@RequestMapping("/EmpInfo")
public class EmpInfoController
        extends EntityController<EmpInfoService, EmpInfo, Long> {
    // inherits all CRUD endpoints under /EmpInfo/*
}
```

That's it — `EmpInfo` now has a full CRUD REST API. The base classes:

| Class | Package | Generic signature |
|---|---|---|
| `EntityController` | `io.softa.framework.web.controller` | `<S extends EntityService<T,K>, T extends AbstractModel, K extends Serializable>` |
| `EntityService` (interface) | `io.softa.framework.orm.service` | `<T extends AbstractModel, K extends Serializable>` |
| `EntityServiceImpl` (abstract) | `io.softa.framework.orm.service.impl` | `<T, K>` |

Convention: service impls go under `service/impl` and are annotated `@Service`;
the controller's `@RequestMapping` path is conventionally the model name.

---

## Endpoints you get for free

With `@RequestMapping("/EmpInfo")`, all of these are exposed (all `POST` unless
noted). See [queries.md](queries.md) for the request/response bodies.

| Endpoint | Purpose |
|---|---|
| `/EmpInfo/createOne` · `/createOneAndFetch` | create one row (optionally return it fully populated) |
| `/EmpInfo/createList` · `/createListAndFetch` | create many |
| `/EmpInfo/getById` · `/getByIds` | fetch by id(s) |
| `/EmpInfo/updateOne` · `/updateOneAndFetch` | update one |
| `/EmpInfo/updateList` · `/updateListAndFetch` | update many |
| `/EmpInfo/updateByFilter` | batch update rows matching a filter |
| `/EmpInfo/deleteById` · `/deleteByIds` | delete |
| `/EmpInfo/copyById` · `/copyByIds` | duplicate row(s) (honors `@Field(copyable)`) |
| `/EmpInfo/getCopyableFields` (GET) | fields that would be carried on a copy |
| `/EmpInfo/searchPage` | paginated search |
| `/EmpInfo/searchList` | non-paginated search (all matches) |

---

## Service methods (programmatic use)

Inject your service anywhere and call it directly. Key methods on
`EntityService<T, K>`:

```java
K            createOne(T entity);              // returns new id
T            createOneAndFetch(T entity);
List<K>      createList(List<T> entities);

Optional<T>  getById(K id);
Optional<T>  getById(K id, Collection<String> fields);   // partial fetch
List<T>      getByIds(List<K> ids);

boolean      updateOne(T entity);
T            updateOneAndFetch(T entity);
boolean      updateList(List<T> entities);

boolean      deleteById(K id);
boolean      deleteByIds(List<K> ids);

Optional<T>  searchOne(Filters filters);
List<T>      searchList(Filters filters);
Page<T>      searchPage(FlexQuery flexQuery, Page<T> page);
long         count(Filters filters);

List<K>      copyByIds(List<K> ids);
```

Prefer these `EntityService` methods over hand-written JDBC — they apply the
metadata-defined defaults, validation, and relation handling. `Filters` and
`FlexQuery` are documented in [queries.md](queries.md).

> `ModelService` (string/map-based, e.g. `modelService.getById("EmpInfo", id)`)
> is the framework's internal layer that `EntityServiceImpl` delegates to.
> Downstream apps use the typed `EntityService`; reach for `ModelService` only
> for genuinely dynamic, model-name-driven code.

---

## Adding custom business logic

There are **no** separate `beforeCreate` / `afterUpdate` callbacks. To inject
logic, **override the CRUD method** in your impl and call `super`:

```java
@Service
public class EmpInfoServiceImpl
        extends EntityServiceImpl<EmpInfo, Long>
        implements EmpInfoService {

    @Override
    public EmpInfo createOneAndFetch(EmpInfo entity) {
        validateDepartment(entity);            // pre-create logic
        EmpInfo created = super.createOneAndFetch(entity);
        notifyOnboarding(created);             // post-create logic
        return created;
    }
}
```

- Field-level validation: use standard Bean Validation (`@NotNull` etc.) on the
  entity, or check inside the override.
- Cross-entity or multi-step logic: put it in the service impl (that's the
  "Service layer"), keep controllers thin.

## Adding custom endpoints

Add ordinary Spring MVC methods to your controller alongside the inherited CRUD.
Return `ApiResponse<T>`:

```java
@GetMapping("/activeCount")
public ApiResponse<Long> activeCount() {
    return ApiResponse.success(empInfoService.count(
        Filters.of("status", "=", "ACTIVE")));
}
```

## Field linkage (onChange handlers)

`POST /{modelName}/onChange/{fieldName}` powers the frontend's remote field
linkage (`Field.onChange`). Don't write controller endpoints for it — implement
the `FieldOnChangeHandler` SPI (`io.softa.framework.web.onchange`) as a Spring
bean; the framework dispatches the request to the handler registered for the
(model, field) pair, and two handlers claiming the same pair fail the boot.

```java
@Component
public class OvertimeRequestOnChange implements FieldOnChangeHandler {

    @Override
    public String model() {
        return "OvertimeRequest";
    }

    @Override
    public Set<String> fields() {
        return Set.of("employeeId", "overtimeDate");
    }

    @Override
    public OnChangeResponse onChange(OnChangeContext context) {
        // context: id (null when creating), value (new value of the changed
        // field), values (companion fields the client declared to send along)
        return OnChangeResponse.builder()
                .values(Map.of("compensationType", "TIME_OFF"))
                .readonly(List.of("compensationType"))
                .build();
    }
}
```

Handler rules:

- onChange is an advisory, read-only computation — never persist from a handler.
- `values` patches only the returned keys; a null value clears the field.
- `readonly` / `required` are complete field-name lists, not patches: name every
  field in that state for the current trigger value, and return an empty list
  (or leave it null) to lift the rules the handler set earlier.
- Every field named in the response must exist on the model; unknown names are
  rejected.

---

## Cross-app models (RPC)

If a model is owned by a **different** Softa app, the framework transparently
routes ORM calls to the owning app over HTTP — you keep calling the service
normally. Routing is automatic (keyed on the model's app), you just configure the
target once in `application.yml`. See the RPC section of the softa-web reference
for the `rpc.services.*` config. You don't annotate anything.
