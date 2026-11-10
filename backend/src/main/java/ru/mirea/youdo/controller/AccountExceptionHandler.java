package ru.mirea.youdo.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import ru.mirea.youdo.service.AccountProblem;
import java.time.Instant;
import java.util.*;

/** Scoped to this module; infrastructure can reuse the shape in its global/security handler. */
@RestControllerAdvice(assignableTypes={AdminUsersController.class, CategoryController.class})
public class AccountExceptionHandler {
    public record FieldError(String field, String message) {}
    public record ErrorResponse(int status, String code, String message, List<FieldError> fieldErrors, String correlationId, Instant timestamp) {}
    @ExceptionHandler(AccountProblem.class)
    public ResponseEntity<ErrorResponse> problem(AccountProblem ex, HttpServletRequest request) {
        return reply(ex.status(), ex.code(), ex.getMessage(), ex.field() == null ? List.of() : List.of(new FieldError(ex.field(), ex.getMessage())), request);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        // Rejected values (especially passwords) must never be serialized.
        var fields = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> new FieldError(e.getField(), "Проверьте значение поля")).distinct().toList();
        return reply(400, "VALIDATION_ERROR", "Проверьте заполнение формы", fields, request);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> malformed(Exception ex, HttpServletRequest request) {
        return reply(400, "INVALID_REQUEST", "Неверный формат запроса", List.of(), request);
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> conflict(Exception ex, HttpServletRequest request) {
        return reply(409, "DATA_CONFLICT", "Значение уже используется или нарушает ограничения данных", List.of(), request);
    }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> stale(Exception ex, HttpServletRequest request) {
        return reply(409, "VERSION_CONFLICT", "Запись уже изменена. Обновите данные", List.of(), request);
    }
    private ResponseEntity<ErrorResponse> reply(int status, String code, String message, List<FieldError> fields, HttpServletRequest request) {
        Object value = request.getAttribute("correlationId");
        String correlationId = value instanceof String s && s.matches("[A-Za-z0-9_-]{1,64}") ? s : UUID.randomUUID().toString();
        return ResponseEntity.status(status).header("X-Correlation-ID", correlationId)
            .body(new ErrorResponse(status, code, message, fields, correlationId, Instant.now()));
    }
}
