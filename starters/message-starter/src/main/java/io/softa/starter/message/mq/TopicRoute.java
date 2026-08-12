package io.softa.starter.message.mq;

import io.softa.framework.base.annotation.OptionItem;
import io.softa.framework.base.annotation.OptionSet;

/**
 * Logical topic identifier resolved to a physical topic name via
 * {@link MqTopicsProperties}. Held as an enum so call sites cannot typo
 * a topic string.
 */
@OptionSet
public enum TopicRoute {
    @OptionItem(label = "Mail send topic")
    MAIL_SEND,
    @OptionItem(label = "SMS send topic")
    SMS_SEND
}
