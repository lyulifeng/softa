package io.softa.starter.flow.service.impl;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.starter.flow.design.DesignFlowDefinition;
import io.softa.starter.flow.dto.FlowDesignSaveRequest;
import io.softa.starter.flow.entity.FlowBundle;
import io.softa.starter.flow.entity.FlowCcConfig;
import io.softa.starter.flow.entity.FlowDesign;
import io.softa.starter.flow.enums.FlowScenario;
import io.softa.starter.flow.service.FlowBundleService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * A design or CC configuration is read before it is changed, and the read may come back cut — a caller
 * reaching the model only by reference reads its label, every other field null. Each change writes only
 * what it sets, so those nulls never reach the database.
 */
class FlowWriteChangedFieldsTest {

    private static final String FLOW = "flow-1";

    /** The label of a published design: id and name, every other field cut. */
    private static FlowDesign labelOnlyDesign() {
        FlowDesign design = new FlowDesign();
        design.setId(FLOW);
        design.setFlowName("Leave approval");
        return design;
    }

    private static FlowDesignServiceImpl designService() {
        FlowDesignServiceImpl service = spy(new FlowDesignServiceImpl());
        doReturn(Optional.of(labelOnlyDesign())).when(service).getById(FLOW);
        doReturn(true).when(service).updateOne(any(FlowDesign.class));
        return service;
    }

    private static FlowDesign written(FlowDesignServiceImpl service) {
        ArgumentCaptor<FlowDesign> patch = ArgumentCaptor.forClass(FlowDesign.class);
        verify(service).updateOne(patch.capture());
        verify(service, never()).updateOne(any(FlowDesign.class), anyBoolean());
        return patch.getValue();
    }

    @Test
    void savingADraftWritesTheDocumentAndLeavesThePublishedRevisionAlone() {
        FlowDesignServiceImpl service = designService();
        DesignFlowDefinition document = DesignFlowDefinition.builder().build();

        service.saveDraft(FLOW, new FlowDesignSaveRequest(document, null, FlowScenario.APPROVAL, 3));

        FlowDesign patch = written(service);
        assertEquals(FLOW, patch.getId());
        assertEquals(3, patch.getVersion());
        assertEquals(FlowScenario.APPROVAL, patch.getScenario());
        assertSame(document, patch.getDesignJson());
        assertNull(patch.getFlowName(), "a name the request leaves out is not written");
        assertNull(patch.getPublishedRevision());
        assertNull(patch.getPublishedChecksum());
    }

    @Test
    void publishingWritesTheRevisionAndNeverTheDocument() {
        FlowDesignServiceImpl service = designService();

        service.upsertFromPublish(FLOW, 4);

        FlowDesign patch = written(service);
        assertEquals(4, patch.getPublishedRevision());
        assertNull(patch.getDesignJson(), "the document read as null must not be written back");
    }

    @Test
    void restoringABundleWritesOnlyItsDocument() {
        FlowDesignServiceImpl service = designService();
        FlowBundle bundle = new FlowBundle();
        bundle.setFlowId(FLOW);
        DesignFlowDefinition document = DesignFlowDefinition.builder().build();
        bundle.setDesignJson(document);
        FlowBundleService bundles = mock(FlowBundleService.class);
        when(bundles.findById(7L)).thenReturn(Optional.of(bundle));
        ReflectionTestUtils.setField(service, "bundleService", bundles);

        service.restoreFromBundle(FLOW, 7L);

        FlowDesign patch = written(service);
        assertSame(document, patch.getDesignJson());
        assertNull(patch.getPublishedRevision());
        assertNull(patch.getPublishedChecksum());
        assertNull(patch.getScenario());
    }

    @Test
    void deactivatingACcConfigurationWritesOnlyTheFlag() {
        FlowCcConfigServiceImpl service = spy(new FlowCcConfigServiceImpl());
        FlowCcConfig label = new FlowCcConfig();
        label.setId(5L);
        label.setCcName("Notify HR");
        doReturn(Optional.of(label)).when(service).getById(5L);
        doReturn(true).when(service).updateOne(any(FlowCcConfig.class));

        service.deactivateConfig(5L);

        ArgumentCaptor<FlowCcConfig> patch = ArgumentCaptor.forClass(FlowCcConfig.class);
        verify(service).updateOne(patch.capture());
        verify(service, never()).updateOne(any(FlowCcConfig.class), anyBoolean());
        assertEquals(5L, patch.getValue().getId());
        assertEquals(Boolean.FALSE, patch.getValue().getActive());
        assertNull(patch.getValue().getFlowId());
        assertNull(patch.getValue().getRecipientConfig());
    }
}
