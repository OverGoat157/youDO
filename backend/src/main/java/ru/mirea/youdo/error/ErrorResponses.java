package ru.mirea.youdo.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.mirea.youdo.security.CorrelationIdFilter;

@Component
public class ErrorResponses {
    private final ObjectMapper mapper;
    public ErrorResponses(ObjectMapper mapper) { this.mapper = mapper; }
    public ApiError body(HttpServletRequest req, int status, String code, String message,
                         List<ApiError.FieldError> fields) {
        return new ApiError(status, code, message, fields,
            (String) req.getAttribute(CorrelationIdFilter.HEADER), Instant.now());
    }
    public void write(HttpServletRequest req, HttpServletResponse res, int status, String code,
                      String message) throws IOException {
        res.setStatus(status); res.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(res.getOutputStream(), body(req, status, code, message, List.of()));
    }
}
