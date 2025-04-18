package com.yang.ratingsystem.config;

import com.yang.ratingsystem.listener.thumb.msg.ThumbEvent;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.Producer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * User:小小星仔
 * Date:2025-04-18
 * Time:11:53
 */

@Configuration
public class PulsarConfig {

    @Value("${pulsar.service-url}")
    private String serviceUrl;

    @Value("${pulsar.tenant:public}")
    private String tenant;

    @Value("${pulsar.namespace:default}")
    private String namespace;
    
    @Value("${pulsar.topic:thumb-topic}")
    private String topic;

    @Bean
    public PulsarClient pulsarClient() throws PulsarClientException {
        return PulsarClient.builder()
                .serviceUrl(serviceUrl)
                .operationTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    @Bean
    public Producer<ThumbEvent> thumbEventProducer(PulsarClient pulsarClient) throws PulsarClientException {
        return pulsarClient.newProducer(Schema.JSON(ThumbEvent.class))
                .topic(topic)
                .producerName("thumb-producer")
                .sendTimeout(2, TimeUnit.SECONDS)
                .blockIfQueueFull(true)
                .create();
    }
}