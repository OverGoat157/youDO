package ru.mirea.youdo.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final ErrorResponses errors;
    public ApiExceptionHandler(ErrorResponses errors) { this.errors = errors; }
    private ResponseEntity<ApiError> response(HttpServletRequest req, int status, String code, String message) {
        return ResponseEntity.status(status).body(errors.body(req, status, code, message, List.of()));
    }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException e, HttpServletRequest r) { return response(r, e.status(), e.code(), e.getMessage()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException e, HttpServletRequest r) {
        var fields = e.getBindingResult().getFieldErrors().stream()
            .map(f -> new ApiError.FieldError(f.getField(), f.getDefaultMessage())).toList();
        return ResponseEntity.badRequest().body(errors.body(r, 400, "VALIDATION_ERROR", "Проверьте поля запроса", fields));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
        ConstraintViolationException.class, org.springframework.web.bind.MissingServletRequestParameterException.class})
    ResponseEntity<ApiError> invalid(Exception e, HttpServletRequest r) { return response(r, 400, "INVALID_REQUEST", "Неверный формат запроса"); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(Exception e, HttpServletRequest r) { return response(r, 403, "FORBIDDEN", "Недостаточно прав"); }
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> missing(Exception e, HttpServletRequest r) { return response(r, 404, "NOT_FOUND", "Ресурс не найден"); }
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiError> version(Exception e, HttpServletRequest r) { return response(r, 409, "VERSION_CONFLICT", "Запись уже изменена"); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> integrity(Exception e, HttpServletRequest r) { return response(r, 409, "DATA_CONFLICT", "Изменение нарушает уникальность или связи данных"); }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method(Exception e, HttpServletRequest r) { return response(r, 405, "METHOD_NOT_ALLOWED", "Метод не поддерживается"); }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> media(Exception e, HttpServletRequest r) { return response(r, 415, "UNSUPPORTED_MEDIA_TYPE", "Требуется поддерживаемый Content-Type"); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> upload(Exception e, HttpServletRequest r) { return response(r, 413, "FILE_TOO_LARGE", "Превышен размер файла"); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest r) {
        // Do not log exception messages: JDBC and request errors can contain submitted secrets.
        LOG.error("Unhandled exception type={} correlationId={}", e.getClass().getName(), r.getAttribute("X-Correlation-ID"));
        return response(r, 500, "INTERNAL_ERROR", "Внутренняя ошибка сервера");
    }
}
