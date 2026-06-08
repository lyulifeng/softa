package io.softa.starter.cron.entity;

import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.starter.cron.enums.CronStatus;

/**
 * SysCronLog Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(label = "System Cron Log")
public class SysCronLog extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Cron ID")
    private Long cronId;

    @Field(label = "Cron Job Name", length = 64)
    private String cronName;

    @Field(label = "Cron Execution State")
    private CronStatus status;

    @Field(label = "Execution Start Time", description = "Update when execution begins.")
    private LocalDateTime startTime;

    @Field(label = "Execution End Time", description = "Update after execution")
    private LocalDateTime endTime;

    @Field(label = "Total Execution Time (s)")
    private Double totalTime;

    @Field(label = "Error Message", length = 256)
    private String errorMessage;

}
