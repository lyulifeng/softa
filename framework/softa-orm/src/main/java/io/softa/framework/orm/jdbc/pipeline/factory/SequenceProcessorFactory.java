package io.softa.framework.orm.jdbc.pipeline.factory;

import io.softa.framework.orm.enums.AccessType;
import io.softa.framework.orm.jdbc.pipeline.processor.FieldProcessor;
import io.softa.framework.orm.jdbc.pipeline.processor.SequenceProcessor;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.sequence.SequenceFieldRegistry;
import io.softa.framework.orm.sequence.SequenceFieldRegistryHolder;

/**
 * Factory for the sequence-fill processor. Active only when:
 * <ul>
 *   <li>{@code accessType == CREATE} — sequence allocation runs only on insert</li>
 *   <li>The {@link SequenceFieldRegistry} is present and enabled (i.e. a
 *       sequence implementation starter is on the classpath)</li>
 *   <li>{@code metaField.isAutoSequence() == true} — populated at startup by
 *       the registry initializer based on sys_sequence rows</li>
 * </ul>
 * Returns {@code null} otherwise; the chain skips this factory.
 *
 * <p>Designed to be inserted before {@code NormalProcessorFactory} in
 * {@code DataCreatePipeline.buildFieldProcessorChain}, so the sequence-filled
 * value beats any static {@code defaultValue} fallback.
 */
public class SequenceProcessorFactory implements FieldProcessorFactory {

    @Override
    public FieldProcessor createProcessor(MetaField metaField, AccessType accessType) {
        if (!AccessType.CREATE.equals(accessType)) {
            return null;
        }
        SequenceFieldRegistry registry = SequenceFieldRegistryHolder.get();
        if (registry == null || !registry.isEnabled()) {
            return null;
        }
        if (!metaField.isAutoSequence()) {
            return null;
        }
        return new SequenceProcessor(metaField, accessType, registry.getService());
    }
}
