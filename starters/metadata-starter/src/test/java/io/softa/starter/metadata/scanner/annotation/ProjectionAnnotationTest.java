package io.softa.starter.metadata.scanner.annotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Index;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.StorageType;
import io.softa.starter.metadata.entity.SysModel;

/**
 * {@code @Model(projection = true)} marks a read-only model over a table it does not
 * own (another model's table, or one created externally). Parse-time contract:
 * <ul>
 *   <li>the flag is materialized into {@code SysModel.projection};</li>
 *   <li>one table has ONE DDL owner — two non-projection models resolving to the same
 *       table fail at parse (an undeclared share would race CREATEs on a fresh database
 *       and silently merge unrelated tables);</li>
 *   <li>a projection declaring {@code @Index} fails (indexes belong to the owner);</li>
 *   <li>a projection with a non-RDBMS storage type fails (it reads a physical table).</li>
 * </ul>
 */
class ProjectionAnnotationTest {

    private final AnnotationParser parser = new AnnotationParser();

    @Model
    static class Employee extends AuditableModel {
        @Field
        private Long id;

        @Override
        public Serializable getId() {
            return id;
        }

        @Field(length = 128)
        private String fullName;
    }

    /** Read projection over Employee's table: repeats a shared column + adds a dynamic one. */
    @Model(projection = true, tableName = "employee")
    static class BirthdayCountdown extends AuditableModel {
        @Field
        private Long id;

        @Override
        public Serializable getId() {
            return id;
        }

        @Field(length = 128)
        private String fullName;

        @Field(length = 11, computed = true, dynamic = true, expression = "1")
        private Integer daysUntilBirthday;
    }

    @Test
    void projectionParsesAndSharesTheOwnersTable() {
        List<SysModel> models = parser
                .parse(List.of(Employee.class, BirthdayCountdown.class), List.of())
                .models();

        SysModel owner = models.stream()
                .filter(m -> "Employee".equals(m.getModelName())).findFirst().orElseThrow();
        SysModel projection = models.stream()
                .filter(m -> "BirthdayCountdown".equals(m.getModelName())).findFirst().orElseThrow();
        assertEquals(Boolean.FALSE, owner.getProjection());
        assertEquals(Boolean.TRUE, projection.getProjection());
        assertEquals("employee", projection.getTableName());
    }

    /** Unspecified tableName on a projection derives as usual (e.g. an external BI table). */
    @Model(projection = true)
    static class BiRevenueDaily extends AuditableModel {
        @Field
        private Long id;

        @Override
        public Serializable getId() {
            return id;
        }
    }

    @Test
    void projectionTableNameIsInferredWhenUnspecified() {
        SysModel projection = parser.parse(List.of(BiRevenueDaily.class), List.of())
                .models().getFirst();
        assertEquals("bi_revenue_daily", projection.getTableName());
        assertEquals(Boolean.TRUE, projection.getProjection());
    }

    /** Second OWNER of the employee table (projection not declared). */
    @Model(tableName = "employee")
    static class EmployeeReport extends AuditableModel {
        @Field
        private Long id;

        @Override
        public Serializable getId() {
            return id;
        }
    }

    @Test
    void rejectsTwoOwnersOfOneTable() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> parser.parse(List.of(Employee.class, EmployeeReport.class), List.of()));
        assertTrue(ex.getMessage().contains("ONE DDL owner"), ex.getMessage());
        assertTrue(ex.getMessage().contains("Employee") && ex.getMessage().contains("EmployeeReport"),
                ex.getMessage());
    }

    @Model(projection = true, tableName = "employee")
    @Index(fields = {"code"})
    static class IndexedProjection extends AuditableModel {
        @Field
        private Long id;

        @Override
        public Serializable getId() {
            return id;
        }

        @Field(length = 32)
        private String code;
    }

    @Test
    void rejectsIndexOnProjection() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> parser.parse(List.of(IndexedProjection.class), List.of()));
        assertTrue(ex.getMessage().contains("@Index"), ex.getMessage());
    }

    @Model(projection = true, storageType = StorageType.ES)
    static class EsProjection extends AuditableModel {
        @Field
        private Long id;

        @Override
        public Serializable getId() {
            return id;
        }
    }

    @Test
    void rejectsNonRdbmsProjection() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> parser.parse(List.of(EsProjection.class), List.of()));
        assertTrue(ex.getMessage().contains("RDBMS-only"), ex.getMessage());
    }
}
