package com.centroalerce.gestion.utils;

public class ValidationResult {
    private boolean isValid;
    private String errorMessage;
    private String errorCode;

    public ValidationResult(boolean isValid, String errorMessage, String errorCode) {
        this.isValid = isValid;
        this.errorMessage = errorMessage;
        this.errorCode = errorCode;
    }

    public static ValidationResult success() {
        return new ValidationResult(true, null, null);
    }

    public static ValidationResult error(String message, String code) {
        return new ValidationResult(false, message, code);
    }

    // Getters
    public boolean isValid() { return isValid; }
    public String getErrorMessage() { return errorMessage; }
    public String getErrorCode() { return errorCode; }
}