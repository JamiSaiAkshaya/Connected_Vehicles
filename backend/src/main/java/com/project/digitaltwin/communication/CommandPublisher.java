package com.project.digitaltwin.communication;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
public class CommandPublisher {

    private static final Logger log = LoggerFactory.getLogger(CommandPublisher.class);

    private final MessageChannel mqttOutboundChannel;
    private final ObjectMapper objectMapper;
    private final int qos;

    public CommandPublisher(@Qualifier("mqttOutboundChannel") MessageChannel mqttOutboundChannel,
                             ObjectMapper objectMapper,
                             @Value("${digitaltwin.mqtt.qos}") int qos) {
        this.mqttOutboundChannel = mqttOutboundChannel;
        this.objectMapper = objectMapper;
        this.qos = qos;
    }

    public void publishCommand(String targetVehicleId, CommandMessage command) {
        try {
            String payload = objectMapper.writeValueAsString(command);
            String topic = "vehicles/" + targetVehicleId + "/command";
            Message<String> message = MessageBuilder.withPayload(payload)
                    .setHeader(MqttHeaders.TOPIC, topic)
                    .setHeader(MqttHeaders.QOS, qos)
                    .build();
            mqttOutboundChannel.send(message);
            log.info("Published {} command to {}: {}", command.commandType, targetVehicleId, command.reason);
        } catch (Exception e) {
            // FAULT TOLERANCE: a failed command publish must never crash the
            // ingest pipeline that triggered it.
            log.error("Failed to publish command to {}: {}", targetVehicleId, e.getMessage(), e);
        }
    }
}
