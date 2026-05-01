package org.ticketing.reservation.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka 토픽을 애플리케이션 기동 시 자동으로 생성/검증한다.
 *
 * <p>reservation-service 가 발행하는 토픽만 여기에 정의한다.
 * 소비하는 토픽(payment.completed 등)은 발행 측 서비스가 관리한다.
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${kafka.topic.partitions:3}")
    private int partitions;

    @Value("${kafka.topic.replicas:1}")
    private short replicas;

    @Value("${topics.reservation.confirmation.failed:reservation.confirmation.failed}")
    private String reservationConfirmationFailedTopic;

    @Bean
    public NewTopic reservationConfirmationFailedTopic() {
        return TopicBuilder.name(reservationConfirmationFailedTopic)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }
}
