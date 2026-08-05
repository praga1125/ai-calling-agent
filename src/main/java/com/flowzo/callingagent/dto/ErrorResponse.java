package com.flowzo.callingagent.dto;

public record ErrorResponse(String error, String message, int status) {
}
