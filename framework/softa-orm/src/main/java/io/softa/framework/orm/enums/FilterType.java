package io.softa.framework.orm.enums;

/**
 * Filter type:
 * <ul>
 *   <li>{@code EMPTY} — no predicate; dropped by combine/WhereBuilder.</li>
 *   <li>{@code NEVER} — guaranteed-false predicate; dominates AND-combine,
 *       absorbs into OR-combine, causes WhereBuilder to emit {@code 1=0}.
 *       Callers upstream (ModelServiceImpl) short-circuit to an empty result
 *       before reaching WhereBuilder; NEVER at the WhereBuilder layer is
 *       defense in depth.</li>
 *   <li>{@code TREE} — logical AND/OR of children.</li>
 *   <li>{@code LEAF} — single {@code FilterUnit}.</li>
 * </ul>
 */
public enum FilterType {
    EMPTY, NEVER, TREE, LEAF
}
