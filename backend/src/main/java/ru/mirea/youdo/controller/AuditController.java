package ru.mirea.youdo.controller;

import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.mirea.youdo.dto.AccountDtos.PageResponse;
import ru.mirea.youdo.dto.AuditDtos.AuditQuery;
import ru.mirea.youdo.dto.AuditDtos.AuditResponse;
import ru.mirea.youdo.service.AuditQueryService;

/** Просмотр журнала аудита — только администратор. */
@RestController @RequestMapping("/api/admin/audit") @PreAuthorize("hasRole('ADMIN')")
public class AuditController {
    private final AuditQueryService audit;

    public AuditController(AuditQueryService audit) { this.audit = audit; }

    /** from/to — ISO-8601 (2026-11-21T10:00:00Z), интервал [from, to). */
    @GetMapping public PageResponse<AuditResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "occurredAt,desc") String sort) {
        return audit.search(new AuditQuery(q, action, entityType, entityId, from, to), page, size, sort);
    }
}
