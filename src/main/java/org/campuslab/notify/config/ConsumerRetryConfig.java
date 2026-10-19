package org.campuslab.notify.config;

import org.campuslab.notify.exception.NonRecoverableMessageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;

/**
 * Política de reintentos de los consumidores: backoff exponencial (1 s, 2 s, ... hasta max-interval).
 *
 * Se hace a mano en vez de con spring.rabbitmq.listener.simple.retry porque los
 * consumidores usan ack manual: en ese modo Spring no hace nack cuando se agotan
 * los reintentos y el mensaje quedaría sin confirmar. Aquí, al agotarse, el
 * consumidor hace basicNack(requeue=false) y el mensaje pasa a la DLQ.
 *
 * Los errores NO recuperables ({@link NonRecoverableMessageException}: payload
 * inválido, regla de negocio violada) no se reintentan: reintentar un mensaje
 * que nunca va a funcionar solo retrasa su llegada a la DLQ.
 */
@Configuration
public class ConsumerRetryConfig {

    private static final Logger log = LoggerFactory.getLogger(ConsumerRetryConfig.class);

    @Bean
    public RetryTemplate consumerRetryTemplate(
            @Value("${campuslab.notify.retry.max-attempts:3}") int maxAttempts,
            @Value("${campuslab.notify.retry.initial-interval:1000}") long initialInterval,
            @Value("${campuslab.notify.retry.multiplier:2.0}") double multiplier,
            @Value("${campuslab.notify.retry.max-interval:10000}") long maxInterval) {
        return RetryTemplate.builder()
                .maxAttempts(maxAttempts)
                .exponentialBackoff(initialInterval, multiplier, maxInterval)
                .notRetryOn(NonRecoverableMessageException.class)
                .withListener(new RetryListener() {
                    @Override
                    public <T, E extends Throwable> void onError(
                            RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
                        log.warn("[REINTENTO] intento {}/{} fallido: {}",
                                context.getRetryCount(), maxAttempts, throwable.getMessage());
                    }
                })
                .build();
    }
}
