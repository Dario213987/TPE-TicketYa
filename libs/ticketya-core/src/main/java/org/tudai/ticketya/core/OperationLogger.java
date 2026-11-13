package org.tudai.ticketya.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Log de operaciones críticas (compra, devolución, cancelación de evento e ingreso).
 * <p>
 * Escribe una línea por operación en el logger {@value #LOGGER_NAME}, con la operación, el
 * usuario, la entidad, su id, el resultado y, si falló o se compensó, el motivo. Los datos van
 * también al MDC, así que en el archivo JSON quedan como campos propios
 * ({@code operation}, {@code userId}, {@code entity}, {@code entityId}, {@code result},
 * {@code reason}).
 */
public final class OperationLogger {

    public static final String LOGGER_NAME = "ticketya.operations";
    public static final String OK = "OK";
    public static final String FAILED = "FAILED";
    public static final String COMPENSATED = "COMPENSATED";

    private static final int MAX_REASON_LENGTH = 255;
    private static final String MESSAGE = "{} {} user={} {}={}";
    private static final String MESSAGE_WITH_REASON = MESSAGE + " reason={}";
    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    private OperationLogger() {}

    /** Registra una operación que terminó bien. */
    public static void ok(Operation operation, Object userId, String entity, Object entityId) {
        write(operation, OK, userId, entity, entityId, null);
    }

    /** Registra una operación que falló, con su motivo. */
    public static void failed(Operation operation, Object userId, String entity, Object entityId,
                              String reason) {
        write(operation, FAILED, userId, entity, entityId, reason);
    }

    /** Registra una operación que se deshizo con una compensación, con su motivo. */
    public static void compensated(Operation operation, Object userId, String entity, Object entityId,
                                   String reason) {
        write(operation, COMPENSATED, userId, entity, entityId, reason);
    }

    private static void write(Operation operation, String result, Object userId, String entity,
                              Object entityId, String reason) {
        String user = RequestLogger.sanitizeUser(userId == null ? null : String.valueOf(userId));
        String id = entityId == null ? "" : String.valueOf(entityId);
        String motivo = sanitizeReason(reason);

        MDC.put(MdcKeys.OPERATION, operation.name());
        MDC.put(MdcKeys.USER_ID, user);
        MDC.put(MdcKeys.ENTITY, entity);
        MDC.put(MdcKeys.ENTITY_ID, id);
        MDC.put(MdcKeys.RESULT, result);
        if (motivo != null) {
            MDC.put(MdcKeys.REASON, motivo);
        }
        try {
            if (OK.equals(result)) {
                log.info(MESSAGE, operation, result, user, entity, id);
            } else {
                log.warn(MESSAGE_WITH_REASON, operation, result, user, entity, id,
                        motivo == null ? "" : motivo);
            }
        } finally {
            MDC.remove(MdcKeys.OPERATION);
            MDC.remove(MdcKeys.USER_ID);
            MDC.remove(MdcKeys.ENTITY);
            MDC.remove(MdcKeys.ENTITY_ID);
            MDC.remove(MdcKeys.RESULT);
            MDC.remove(MdcKeys.REASON);
        }
    }

    private static String sanitizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String clean = reason.trim().replaceAll("\\p{Cntrl}", " ");
        return clean.length() > MAX_REASON_LENGTH ? clean.substring(0, MAX_REASON_LENGTH) : clean;
    }
}
