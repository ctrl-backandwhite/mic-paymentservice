package com.backandwhite.infrastructure.message.kafka.producer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backandwhite.common.constants.AppConstants;
import java.util.concurrent.CompletableFuture;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class KafkaPaymentEventAdapterTest {

    @Mock
    private KafkaTemplate<String, SpecificRecord> kafkaTemplate;

    @InjectMocks
    private KafkaPaymentEventAdapter adapter;

    @BeforeEach
    void stubSend() {
        lenient().when(kafkaTemplate.send(any(String.class), any(String.class), any(SpecificRecord.class)))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    SendResult<String, SpecificRecord> sr = mock(SendResult.class);
                    RecordMetadata meta = new RecordMetadata(new TopicPartition("t", 0), 0L, 0, 0L, 0, 0);
                    when(sr.getRecordMetadata()).thenReturn(meta);
                    return CompletableFuture.completedFuture(sr);
                });
    }

    private void stubFailingFuture(String topic, String key) {
        CompletableFuture<SendResult<String, SpecificRecord>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("kafka boom"));
        when(kafkaTemplate.send(eq(topic), eq(key), any(SpecificRecord.class))).thenReturn(future);
    }

    @Test
    void publishPaymentInitiated_sendsToCorrectTopic() {
        adapter.publishPaymentInitiated("p1", "o1", "u1", "10.00", "USD", "CARD", "STRIPE");

        verify(kafkaTemplate).send(eq(AppConstants.KAFKA_TOPIC_PAYMENT_INITIATED), eq("o1"), any(SpecificRecord.class));
    }

    @Test
    void publishPaymentConfirmed_sendsToCorrectTopic() {
        adapter.publishPaymentConfirmed("p1", "o1", "u1", "e@e.com", "10.00", "USD", "CARD", "STRIPE", "tx-1");

        verify(kafkaTemplate).send(eq(AppConstants.KAFKA_TOPIC_PAYMENT_CONFIRMED), eq("o1"), any(SpecificRecord.class));
    }

    @Test
    void publishPaymentFailed_sendsToCorrectTopic() {
        adapter.publishPaymentFailed("p1", "o1", "u1", "e@e.com", "10.00", "card-declined", "STRIPE");

        verify(kafkaTemplate).send(eq(AppConstants.KAFKA_TOPIC_PAYMENT_FAILED), eq("o1"), any(SpecificRecord.class));
    }

    @Test
    void publishRefundInitiated_sendsToCorrectTopic() {
        adapter.publishRefundInitiated("p1", "r1", "o1", "u1", "10.00", "user-request");

        verify(kafkaTemplate).send(eq(AppConstants.KAFKA_TOPIC_PAYMENT_REFUND_INITIATED), eq("o1"),
                any(SpecificRecord.class));
    }

    @Test
    void publishRefundCompleted_sendsToCorrectTopic() {
        adapter.publishRefundCompleted("p1", "r1", "o1", "u1", "10.00");

        verify(kafkaTemplate).send(eq(AppConstants.KAFKA_TOPIC_PAYMENT_REFUND_COMPLETED), eq("o1"),
                any(SpecificRecord.class));
    }

    @Test
    void send_whenFutureCompletesExceptionally_logsErrorAndDoesNotThrow() {
        stubFailingFuture(AppConstants.KAFKA_TOPIC_PAYMENT_INITIATED, "o1");

        adapter.publishPaymentInitiated("p1", "o1", "u1", "10.00", "USD", "CARD", "STRIPE");

        verify(kafkaTemplate).send(eq(AppConstants.KAFKA_TOPIC_PAYMENT_INITIATED), eq("o1"), any(SpecificRecord.class));
    }
}
