// src/main/java/com/moniq/api/auth/exception/ApiErrorResponse.java
package com.moniq.api.auth.exception;

import java.time.Instant;
import java.util.List;

public class ApiErrorResponse {
    private Instant timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private String requestId;
    private List<FieldErrorItem> fieldErrors;

    public ApiErrorResponse() {}

    public ApiErrorResponse(Instant timestamp, int status, String error, String message,
                            String path, String requestId, List<FieldErrorItem> fieldErrors) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.requestId = requestId;
        this.fieldErrors = fieldErrors;
    }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public List<FieldErrorItem> getFieldErrors() { return fieldErrors; }
    public void setFieldErrors(List<FieldErrorItem> fieldErrors) { this.fieldErrors = fieldErrors; }

    public static class FieldErrorItem {
        private String field;
        private String message;

        public FieldErrorItem() {}

        public FieldErrorItem(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}