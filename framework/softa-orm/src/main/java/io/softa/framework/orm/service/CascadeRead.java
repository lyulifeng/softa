package io.softa.framework.orm.service;

import java.util.Objects;
import java.util.Set;

/**
 * The fields of a related model that a read resolves for the cascaded fields declared on a model
 * referring to it.
 *
 * <p>A model that declares {@code @Field(cascadedField = "employeeId.jobPositionId")} shows that value
 * as its own: the related row is read for it, and a caller who reads the related model only as its label
 * would otherwise get the value cut away with everything else past the label. Binding the read here lets
 * the permission layer keep those fields while still cutting the rest of the row. Sensitive fields stay
 * masked whatever is bound.
 *
 * <p>Outside any binding nothing is exposed, so a direct read of the related model is judged as always.
 */
public final class CascadeRead {

    private record Binding(String model, Set<String> fields) {}

    private static final ScopedValue<Binding> CURRENT = ScopedValue.newInstance();

    private CascadeRead() {
    }

    /** The fields of {@code model} the current read resolves for declared cascaded fields; empty when none. */
    public static Set<String> exposedOn(String model) {
        if (!CURRENT.isBound()) return Set.of();
        Binding binding = CURRENT.get();
        return binding.model().equals(model) ? binding.fields() : Set.of();
    }

    /**
     * Call the operation as the read of {@code fields} of {@code model} for declared cascaded fields.
     *
     * @param model  the related model read
     * @param fields its fields the declared cascaded fields read
     * @param op     the operation
     * @return the operation's result
     * @throws X if the operation throws
     */
    public static <T, X extends Throwable> T callExposing(String model, Set<String> fields,
                                                         ScopedValue.CallableOp<T, X> op) throws X {
        Objects.requireNonNull(model, "Model must not be null");
        return ScopedValue.where(CURRENT, new Binding(model, Set.copyOf(fields))).call(op);
    }
}
