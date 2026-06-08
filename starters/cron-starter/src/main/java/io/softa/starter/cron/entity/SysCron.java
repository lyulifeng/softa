package io.softa.starter.cron.entity;

import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.starter.cron.enums.TenantJobMode;

/**
 * SysCron Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "System Cron",
        activeControl = true,
        description = "Carries the task ownership time during execution and supports "
                + "automatic compensation for missed tasks."
)
public class SysCron extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Cron Job Name", length = 64)
    private String name;

    @Field(label = "Cron Expression", length = 64,
            description = "Use Quartz seven-field format with seconds and years.")
    private String cronExpression;

    @Field(label = "Semantic Description", length = 64,
            computed = true, expression = "Toolkit.cronSemantic(cronExpression)")
    private String cronSemantic;

    @Field(label = "Limit the Execution Times")
    private Boolean limitExecution;

    @Field(label = "Remaining Execution Times", defaultValue = "-1",
            description = "Subtract 1 after each execution; when below 1, stop, clear the next "
                    + "execution time and disable.")
    private Integer remainingCount;

    @Field(label = "Next Execution Time",
            description = "Recalculated after each successful execution; allows rollback compensation runs.")
    private LocalDateTime nextExecTime;

    @Field(label = "Last Execution Time",
            description = "Records the execution start time after each successful execution.")
    private LocalDateTime lastExecTime;

    @Field(label = "Redo Missed Task",
            description = "No compensation by default; when true, compensate immediately once.")
    private Boolean redoMisfire;

    @Field(label = "Priority", defaultValue = "1",
            description = "Smaller numbers indicate higher priority, from 0 to 10.")
    private Integer priority;

    @Field(label = "Tenant Job Mode")
    private TenantJobMode tenantJobMode;

    @Field(label = "Description", length = 256)
    private String description;

    @Field(label = "Active")
    private Boolean active;
}
