-- =============================================================================
-- SysSequence: per-(tenant, code) counter for generated business numbers.
-- Allocation goes through SequenceService (port in softa-orm, impl in this
-- starter). See `corehr/docs/design/employee-code-sequence.md` for the full
-- design; the SQL inside SequenceServiceImpl uses MySQL's
-- LAST_INSERT_ID(expr) idiom plus an InnoDB row lock on this table.
-- =============================================================================
CREATE TABLE sys_sequence
(
    id              BIGINT(32)   NOT NULL AUTO_INCREMENT COMMENT 'ID',
    tenant_id       BIGINT(32)   COMMENT 'Tenant ID',
    code            VARCHAR(64)  NOT NULL DEFAULT '' COMMENT 'Sequence code (business key, e.g. Employee.code)',
    template        VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Format template, e.g. EMP-{yyyy}-{seq:5}',
    start_value     BIGINT(32)   NOT NULL DEFAULT 1 COMMENT 'First value after each reset',
    increment_step  INT          NOT NULL DEFAULT 1 COMMENT 'Step size; v1 enforces 1',
    current_value   BIGINT(32)   NOT NULL DEFAULT 0 COMMENT 'Last allocated value; next = current_value + step',
    reset_cadence   VARCHAR(16)  NOT NULL DEFAULT 'NONE' COMMENT 'NONE / YEARLY / MONTHLY / DAILY',
    last_reset_key  VARCHAR(16)  COMMENT 'Period key for reset detection: 2026 / 2026-04 / 2026-04-24',
    mode            VARCHAR(16)  NOT NULL DEFAULT 'NO_GAP' COMMENT 'NO_GAP (counter-bound to business tx) / ALLOW_GAP (REQUIRES_NEW)',
    status          VARCHAR(16)  NOT NULL DEFAULT 'Active' COMMENT 'Active / Disabled',
    description     VARCHAR(256) COMMENT 'Description',
    version         INT          NOT NULL DEFAULT 0 COMMENT 'Optimistic lock for config changes (counter uses row lock)',
    created_time    DATETIME     COMMENT 'Created Time',
    created_by      VARCHAR(64)  COMMENT 'Created By',
    created_id      BIGINT(32)   COMMENT 'Created ID',
    updated_time    DATETIME     COMMENT 'Updated Time',
    updated_id      BIGINT(32)   COMMENT 'Updated ID',
    updated_by      VARCHAR(64)  COMMENT 'Updated By',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_sequence_tenant_code (tenant_id, code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = 'Sequence generator configuration and counter';

ALTER TABLE sys_sequence ADD INDEX idx_tenant (tenant_id);
