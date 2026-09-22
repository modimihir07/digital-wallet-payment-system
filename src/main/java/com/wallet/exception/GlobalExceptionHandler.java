package com.wallet.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts domain exceptions to structured JSON errors.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static ResponseEntity<Map<String, Object>> body(
            HttpStatus status, String message, String path) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("timestamp", Instant.now().toString());
        m.put("status", status.value());
        m.put("error", status.getReasonPhrase());
        m.put("message", message);
        m.put("path", path);
        return ResponseEntity.status(status).body(m);
    }

    /** Maps insufficient balance to 400. */
    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficient(
            InsufficientBalanceException e, HttpServletRequest req) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage(), req.getRequestURI());
    }

    /** Maps frozen wallet to 403. */
    @ExceptionHandler(WalletFrozenException.class)
    public ResponseEntity<Map<String, Object>> handleFrozen(
            WalletFrozenException e, HttpServletRequest req) {
        return body(HttpStatus.FORBIDDEN, e.getMessage(), req.getRequestURI());
    }

    /** Maps duplicate txn to 409. */
    @ExceptionHandler(DuplicateTransactionException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(
            DuplicateTransactionException e, HttpServletRequest req) {
        return body(HttpStatus.CONFLICT, e.getMessage(), req.getRequestURI());
    }

    /** Maps missing resource to 404. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            ResourceNotFoundException e, HttpServletRequest req) {
        return body(HttpStatus.NOT_FOUND, e.getMessage(), req.getRequestURI());
    }

    /** Maps bean-validation failures to 400. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException e, HttpServletRequest req) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst().orElse("Validation failed");
        return body(HttpStatus.BAD_REQUEST, msg, req.getRequestURI());
    }

    /** Maps illegal arguments to 400. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegal(
            IllegalArgumentException e, HttpServletRequest req) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage(), req.getRequestURI());
    }

    /** Fallback 500. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(
            Exception e, HttpServletRequest req) {
        log.error("Unhandled error on {}", req.getRequestURI(), e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", req.getRequestURI());
    }
}
