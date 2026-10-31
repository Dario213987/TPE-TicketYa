package org.tudai.ticketya.core.exception;

public class RemoteServiceException extends RuntimeException {

    private final int remoteStatus;

    public RemoteServiceException(int remoteStatus, String message) {
        super(message);
        this.remoteStatus = remoteStatus;
    }

    public int getRemoteStatus() {
        return remoteStatus;
    }
}