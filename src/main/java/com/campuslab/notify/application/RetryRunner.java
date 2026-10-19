package com.campuslab.notify.application;

import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

/**
 * Ejecuta el procesamiento de un mensaje con reintentos y backoff exponencial.
 * Si tras el último intento sigue fallando, relanza la excepción para que el
 * consumidor haga nack (sin requeue) y el mensaje pase a la DLQ.
 */
@Component
public class RetryRunner {

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    private final RetryTemplate retryTemplate;

    public RetryRunner(RetryTemplate consumerRetryTemplate) {
        this.retryTemplate = consumerRetryTemplate;
    }

    public void run(ThrowingRunnable task) throws Exception {
        retryTemplate.<Void, Exception>execute(context -> {
            task.run();
            return null;
        });
    }
}
