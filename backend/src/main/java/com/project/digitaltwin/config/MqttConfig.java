package com.project.digitaltwin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.digitaltwin.communication.TelemetryMessage;
import com.project.digitaltwin.twin.DigitalTwinService;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

@Configuration
public class MqttConfig {

    private static final Logger log = LoggerFactory.getLogger(MqttConfig.class);

    @Value("${digitaltwin.mqtt.broker-url}")
    private String brokerUrl;

    @Value("${digitaltwin.mqtt.client-id}")
    private String clientId;

    @Value("${digitaltwin.mqtt.telemetry-topic-filter}")
    private String topicFilter;

    @Value("${digitaltwin.mqtt.qos}")
    private int qos;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{brokerUrl});
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        factory.setConnectionOptions(options);
        return factory;
    }

    // ---- Inbound (vehicle telemetry -> backend), from Phase 0/1 ----

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageProducer inbound(MqttPahoClientFactory mqttClientFactory) {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId + "-inbound", mqttClientFactory, topicFilter);
        adapter.setCompletionTimeout(5000);
        adapter.setQos(qos);
        adapter.setOutputChannel(mqttInputChannel());
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public org.springframework.messaging.MessageHandler telemetryHandler(
            DigitalTwinService digitalTwinService, ObjectMapper objectMapper) {
        return (Message<?> message) -> {
            try {
                String payload = message.getPayload().toString();
                TelemetryMessage telemetry = objectMapper.readValue(payload, TelemetryMessage.class);
                digitalTwinService.ingest(telemetry);
            } catch (Exception e) {
                // FAULT TOLERANCE: never let one malformed message crash the adapter thread.
                log.error("Failed to process incoming MQTT telemetry message: {}", e.getMessage(), e);
            }
        };
    }

    // ---- Outbound (backend -> vehicle commands, e.g. REROUTE), new in Phase 2 ----

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public org.springframework.messaging.MessageHandler mqttOutbound(MqttPahoClientFactory mqttClientFactory) {
        MqttPahoMessageHandler handler = new MqttPahoMessageHandler(clientId + "-outbound", mqttClientFactory);
        handler.setAsync(true);
        handler.setDefaultQos(qos);
        return handler;
    }
}
