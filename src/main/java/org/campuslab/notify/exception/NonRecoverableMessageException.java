package org.campuslab.notify.exception;

/**
 * Error que NO se arregla reintentando: payload inválido, dato obligatorio ausente o
 * regla de negocio violada. Los consumidores lo mandan directo a la DLQ, sin reintentos.
 *
 * Cualquier otra excepción se considera recuperable (falla transitoria de un servicio
 * externo, timeout, etc.) y se reintenta con backoff antes de ir a la DLQ.
 */
public class NonRecoverableMessageException extends RuntimeException {

    public NonRecoverableMessageException(String message) {
        super(message);
    }

    public NonRecoverableMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
