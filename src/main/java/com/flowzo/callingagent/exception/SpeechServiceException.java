package com.flowzo.callingagent.exception;

public class SpeechServiceException extends RuntimeException {

    public SpeechServiceException(String message) {
        super(message);
    }

    public SpeechServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
