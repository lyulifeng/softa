package io.softa.starter.studio.release.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.softa.framework.base.exception.IllegalArgumentException;
import io.softa.framework.base.utils.Assert;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.domain.Orders;
import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.studio.release.dto.DesignAppVersionDTO;
import io.softa.starter.studio.release.dto.ModelChangesDTO;
import io.softa.starter.studio.release.entity.DesignApp;
import io.softa.starter.studio.release.entity.DesignAppVersion;
import io.softa.starter.studio.release.entity.DesignWorkItem;
import io.softa.starter.studio.release.enums.DesignAppVersionStatus;
import io.softa.starter.studio.release.enums.DesignWorkItemStatus;
import io.softa.starter.studio.release.service.DesignAppService;
import io.softa.starter.studio.release.service.DesignAppVersionService;
import io.softa.starter.studio.release.service.DesignWorkItemService;
import io.softa.starter.studio.release.version.VersionControl;
import io.softa.starter.studio.release.version.VersionDdl;

/**
 * DesignWorkItem Model Service Implementation
 */
@Service
public class DesignWorkItemServiceImpl extends EntityServiceImpl<DesignWorkItem, Long> implements DesignWorkItemService {

    @Autowired
    private VersionControl versionControl;

    @Autowired
    private VersionDdl versionDdl;

    @Autowired
    @Lazy
    private DesignAppVersionService appVersionService;

    @Autowired
    private DesignAppService appService;

    /**
     * Mark the WorkItem as READY.
     * The WorkItem must be in IN_PROGRESS status.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void readyWorkItem(Long id) {
        DesignWorkItem workItem = this.getById(id)
                .orElseThrow(() -> new IllegalArgumentException("WorkItem does not exist! {0}", id));
        Assert.isEqual(workItem.getStatus(), DesignWorkItemStatus.IN_PROGRESS,
                "Only IN_PROGRESS WorkItems can be marked READY! Current status: {0}", workItem.getStatus());
        workItem.setStatus(DesignWorkItemStatus.READY);
        this.updateOne(workItem);
    }

    /**
     * Complete the WorkItem: set closedTime and transition status to DONE.
     * The WorkItem must be in IN_PROGRESS or READY status.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void doneWorkItem(Long id) {
        DesignWorkItem workItem = this.getById(id)
                .orElseThrow(() -> new IllegalArgumentException("WorkItem does not exist! {0}", id));
        Assert.isTrue(workItem.getStatus() == DesignWorkItemStatus.IN_PROGRESS
                        || workItem.getStatus() == DesignWorkItemStatus.READY,
                "Only IN_PROGRESS or READY WorkItems can be done! Current status: {0}", workItem.getStatus());
        workItem.setClosedTime(LocalDateTime.now());
        workItem.setStatus(DesignWorkItemStatus.DONE);
        this.updateOne(workItem);
    }

    /**
     * Cancel the WorkItem. Only allowed for IN_PROGRESS, READY, or DEFERRED.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelWorkItem(Long id) {
        DesignWorkItem workItem = this.getById(id)
                .orElseThrow(() -> new IllegalArgumentException("WorkItem does not exist! {0}", id));
        Assert.isTrue(workItem.getStatus() == DesignWorkItemStatus.IN_PROGRESS
                        || workItem.getStatus() == DesignWorkItemStatus.READY
                        || workItem.getStatus() == DesignWorkItemStatus.DEFERRED,
                "Only IN_PROGRESS, READY, or DEFERRED WorkItems can be cancelled! Current status: {0}",
                workItem.getStatus());
        workItem.setClosedTime(LocalDateTime.now());
        workItem.setStatus(DesignWorkItemStatus.CANCELLED);
        this.updateOne(workItem);
    }

    /**
     * Defer the WorkItem. Only allowed for IN_PROGRESS.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deferWorkItem(Long id) {
        DesignWorkItem workItem = this.getById(id)
                .orElseThrow(() -> new IllegalArgumentException("WorkItem does not exist! {0}", id));
        Assert.isEqual(workItem.getStatus(), DesignWorkItemStatus.IN_PROGRESS,
                "Only IN_PROGRESS WorkItems can be deferred! Current status: {0}", workItem.getStatus());
        workItem.setStatus(DesignWorkItemStatus.DEFERRED);
        this.updateOne(workItem);
    }

    /**
     * Reopen a DONE, CANCELLED, or DEFERRED WorkItem back to IN_PROGRESS.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reopenWorkItem(Long id) {
        DesignWorkItem workItem = this.getById(id)
                .orElseThrow(() -> new IllegalArgumentException("WorkItem does not exist! {0}", id));
        Assert.isTrue(workItem.getStatus() == DesignWorkItemStatus.DONE
                        || workItem.getStatus() == DesignWorkItemStatus.CANCELLED
                        || workItem.getStatus() == DesignWorkItemStatus.DEFERRED,
                "Only DONE, CANCELLED, or DEFERRED WorkItems can be reopened! Current status: {0}",
                workItem.getStatus());
        workItem.setClosedTime(null);
        workItem.setStatus(DesignWorkItemStatus.IN_PROGRESS);
        this.updateOne(workItem);
    }

    /**
     * Preview all metadata changes accumulated under this WorkItem,
     * queried from ES by correlationId.
     */
    @Override
    public List<ModelChangesDTO> previewWorkItemChanges(Long id) {
        DesignWorkItem workItem = this.getById(id)
                .orElseThrow(() -> new IllegalArgumentException("WorkItem does not exist! {0}", id));
        Assert.notNull(workItem.getAppId(), "WorkItem {0} has no appId set!", id);

        List<Long> workItemIds = List.of(id);
        return versionControl.collectModelChanges(workItem.getAppId(), workItemIds);
    }

    /**
     * Preview the DDL SQL generated from the metadata changes of this WorkItem.
     */
    @Override
    public String previewWorkItemDDL(Long id) {
        DesignWorkItem workItem = this.getById(id)
                .orElseThrow(() -> new IllegalArgumentException("WorkItem does not exist! {0}", id));
        List<ModelChangesDTO> changes = previewWorkItemChanges(id);
        return versionDdl.generateDDL(appService.getFieldValue(workItem.getAppId(), DesignApp::getDatabaseType), changes);
    }

    /**
     * Merge a DONE WorkItem into the latest DRAFT version of the same App.
     * If no DRAFT version exists, one is automatically created.
     *
     * @param id WorkItem ID
     * @return the Version ID that the WorkItem was merged into
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long mergeToLatestVersion(Long id) {
        DesignWorkItem workItem = this.getById(id)
                .orElseThrow(() -> new IllegalArgumentException("WorkItem does not exist! {0}", id));
        Assert.isEqual(workItem.getStatus(), DesignWorkItemStatus.DONE,
                "Only DONE WorkItems can be merged into a version! Current status: {0}", workItem.getStatus());
        Assert.notNull(workItem.getAppId(), "WorkItem {0} has no appId set!", id);

        // Find the latest DRAFT version for the same App
        Filters draftFilter = new Filters()
                .eq(DesignAppVersion::getAppId, workItem.getAppId())
                .eq(DesignAppVersion::getStatus, DesignAppVersionStatus.DRAFT);
        FlexQuery query = new FlexQuery(draftFilter);
        query.setOrders(Orders.ofDesc(DesignAppVersion::getCreatedTime));
        DesignAppVersion draftVersion = appVersionService.searchOne(query).orElse(null);

        if (draftVersion == null) {
            DesignAppVersionDTO appVersionDTO = new DesignAppVersionDTO();
            appVersionDTO.setAppId(workItem.getAppId());
            appVersionDTO.setName("Auto-created version");
            Long versionId = appVersionService.createOne(appVersionDTO);
            draftVersion = appVersionService.getById(versionId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Auto-created version does not exist! {0}", versionId));
        }

        // Add the WorkItem to the version
        appVersionService.addWorkItem(draftVersion.getId(), id);
        return draftVersion.getId();
    }

}
