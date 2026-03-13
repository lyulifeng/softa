package io.softa.starter.file.message;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Component;

import io.softa.starter.file.dto.ImportTemplateDTO;

/**
 * AsyncImportProducer
 */
@Slf4j
@Component
public class AsyncImportProducer {

    @Value("${mq.topics.async-import.topic:}")
    private String importTopic;

    @Autowired
    private PulsarTemplate<ImportTemplateDTO> pulsarTemplate;

    @Autowired
    private AsyncImportHandler asyncImportHandler;

    /**
     * Send asynchronous import task message.
     */
    public void sendAsyncImport(ImportTemplateDTO message) {
        if (StringUtils.isBlank(importTopic)) {
            log.info("Async import topic is not configured, the import task will be executed asynchronously by @Async.");
            asyncImportHandler.asyncHandler(message);
        } else {
            pulsarTemplate.sendAsync(importTopic, message).whenComplete((_, ex) -> {
                if (ex == null) {
                    log.debug("The asynchronous import message was successfully sent: {}", message);
                } else {
                    log.error("Failed to send an asynchronous import message: {}", message, ex);
                }
            });
        }
    }

}
