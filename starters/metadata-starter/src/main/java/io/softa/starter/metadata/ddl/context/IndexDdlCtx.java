package io.softa.starter.metadata.ddl.context;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Index-level DDL context passed to templates.
 */
@Data
public class IndexDdlCtx {
    private String indexName;
    private String oldIndexName;
    private boolean renamed;
    private boolean definitionChanged;
    private List<String> columns = new ArrayList<>();
    private boolean unique;
    /**
     * {@link io.softa.framework.orm.enums.IndexMethod} name as a template-comparable
     * string ("BTREE" / "SEARCH" / "PREFIX"). Defaults to BTREE so builders that
     * predate the field (studio's DdlContextBuilder) keep rendering plain indexes.
     */
    private String method = "BTREE";
}
