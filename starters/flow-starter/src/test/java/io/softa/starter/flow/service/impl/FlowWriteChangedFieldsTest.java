package io.softa.starter.flow.service.impl;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.orm.domain.Filters;
import io.softa.starter.flow.design.DesignFlowDefinition;
import io.softa.starter.flow.dto.FlowDesignSaveRequest;
import io.softa.starter.flow.entity.FlowBundle;
import io.softa.starter.flow.entity.FlowCcConfig;
import io.softa.starter.flow.entity.FlowDelegation;
import io.softa.starter.flow.entity.FlowDesign;
import io.softa.starter.flow.enums.FlowScenario;
import io.softa.starter.flow.service.FlowBundleService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * A design, CC configuration, delegation or bundle is read before it is changed, and the read may come back cut — a caller
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

    /** The label of a delegation: id and the owner the cancel check reads, every other field cut. */
    private static FlowDelegationServiceImpl delegationService() {
        FlowDelegation label = new FlowDelegation();
        label.setId(9L);
        label.setDelegatorId("emp-1");
        FlowDelegationServiceImpl service = spy(new FlowDelegationServiceImpl());
        doReturn(Optional.of(label)).when(service).getById(9L);
        doReturn(true).when(service).updateOne(any(FlowDelegation.class));
        return service;
    }

    private static FlowDelegation writtenDelegation(FlowDelegationServiceImpl service) {
        ArgumentCaptor<FlowDelegation> patch = ArgumentCaptor.forClass(FlowDelegation.class);
        verify(service).updateOne(patch.capture());
        verify(service, never()).updateOne(any(FlowDelegation.class), anyBoolean());
        assertEquals(9L, patch.getValue().getId());
        assertNull(patch.getValue().getDelegateId(), "the delegate read as null must not be written back");
        assertNull(patch.getValue().getStartTime());
        return patch.getValue();
    }

    @Test
    void cancellingADelegationWritesOnlyTheFlag() {
        FlowDelegationServiceImpl service = delegationService();

        service.cancelDelegation(9L, "emp-1");

        assertEquals(Boolean.FALSE, writtenDelegation(service).getActive());
    }

    @Test
    void recordingADelegatedTaskWritesOnlyTheUsage() {
        FlowDelegationServiceImpl service = delegationService();

        service.recordDelegationUsage(9L);

        FlowDelegation patch = writtenDelegation(service);
        assertEquals(1, patch.getDelegatedTaskCount());
        assertNotNull(patch.getLastDelegationTime());
        assertNull(patch.getActive());
    }

    @Test
    void activatingARevisionWritesOnlyTheFlags() {
        FlowBundle current = new FlowBundle();
        current.setId(1L);
        current.setFlowId(FLOW);
        current.setRevision(1);
        current.setActive(true);
        FlowBundle target = new FlowBundle();
        target.setId(2L);
        target.setFlowId(FLOW);
        target.setRevision(2);
        target.setActive(false);
        FlowBundleServiceImpl service = spy(new FlowBundleServiceImpl());
        doReturn(List.of(current)).when(service).searchList(any(Filters.class));
        doReturn(Optional.of(target)).when(service).searchOne(any(Filters.class));
        doReturn(true).when(service).updateOne(any(FlowBundle.class));

        service.markAsActive(FLOW, 2);

        ArgumentCaptor<FlowBundle> patch = ArgumentCaptor.forClass(FlowBundle.class);
        verify(service, times(2)).updateOne(patch.capture());
        verify(service, never()).updateOne(any(FlowBundle.class), anyBoolean());
        List<FlowBundle> patches = patch.getAllValues();
        assertEquals(1L, patches.get(0).getId());
        assertEquals(Boolean.FALSE, patches.get(0).getActive());
        assertEquals(2L, patches.get(1).getId());
        assertEquals(Boolean.TRUE, patches.get(1).getActive());
        patches.forEach(p -> {
            assertNull(p.getCompiledJson(), "the compiled definition must not be written back");
            assertNull(p.getRevision());
            assertNull(p.getFlowId());
        });
    }
}
