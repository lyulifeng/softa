package io.softa.starter.file.message;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.stereotype.Component;

import io.softa.starter.file.dto.ImportTemplateDTO;

/**
 * AsyncImportConsumer
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mq.topics.async-import.topic")
public class AsyncImportConsumer {

    @Autowired
    private AsyncImportHandler asyncImportHandler;

    @PulsarListener(topics = "${mq.topics.async-import.topic}", subscriptionName = "${mq.topics.async-import.sub}")
    public void onMessage(ImportTemplateDTO importTemplateDTO) {
        asyncImportHandler.handler(importTemplateDTO);
    }

}
