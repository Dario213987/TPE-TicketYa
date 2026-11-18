package org.tudai.ticketya.core;

/**
 * Operaciones críticas que se registran en el log de operaciones ({@link OperationLogger}).
 */
public enum Operation {
    PURCHASE, REFUND, EVENT_CANCEL, ACCESS
}
