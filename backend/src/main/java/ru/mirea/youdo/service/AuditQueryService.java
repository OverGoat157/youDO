package ru.mirea.youdo.service;

import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.youdo.domain.AuditEvent;
import ru.mirea.youdo.dto.AccountDtos.PageResponse;
import ru.mirea.youdo.dto.AuditDtos.AuditQuery;
import ru.mirea.youdo.dto.AuditDtos.AuditResponse;
import ru.mirea.youdo.error.ApiException;
import ru.mirea.youdo.filter.AuditSpecifications;
import ru.mirea.youdo.repository.AuditQueryRepository;

/** Чтение журнала аудита: поиск, фильтры, сортировка и пагинация. */
@Service
public class AuditQueryService {
    static final int MAX_PAGE_SIZE = 100;
    // Имя параметра sort → поле сущности; другие поля сортировки не принимаются
    private static final Map<String, String> SORT_FIELDS = Map.of(
        "occurredAt", "occurredAt", "action", "action", "entityType", "entityType", "actorLogin", "actorLogin");

    private final AuditQueryRepository events;

    public AuditQueryService(AuditQueryRepository events) {
        this.events = events;
    }

    @Transactional(readOnly = true) @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<AuditResponse> search(AuditQuery query, int page, int size, String sort) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw invalid("page ≥ 0, size от 1 до " + MAX_PAGE_SIZE);
        }
        if (query.from() != null && query.to() != null && !query.from().isBefore(query.to())) {
            throw invalid("from должен быть раньше to");
        }
        Page<AuditEvent> result = events.findAll(AuditSpecifications.matching(query),
            PageRequest.of(page, size, parseSort(sort)));
        return new PageResponse<>(result.map(AuditResponse::of).getContent(), page, size,
            result.getTotalElements(), result.getTotalPages());
    }

    /** «поле,asc|desc». По умолчанию — новые события первыми; id добавлен для стабильного порядка страниц. */
    static Sort parseSort(String sort) {
        String value = sort == null || sort.isBlank() ? "occurredAt,desc" : sort.trim();
        String[] parts = value.split(",");
        String field = SORT_FIELDS.get(parts[0].trim());
        if (field == null || parts.length > 2) {
            throw invalid("sort: поле occurredAt, action, entityType или actorLogin, направление asc или desc");
        }
        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length == 2) {
            String dir = parts[1].trim();
            if (dir.equalsIgnoreCase("asc")) direction = Sort.Direction.ASC;
            else if (!dir.equalsIgnoreCase("desc")) {
                throw invalid("sort: направление asc или desc");
            }
        }
        return Sort.by(direction, field).and(Sort.by(direction, "id"));
    }

    private static ApiException invalid(String message) {
        return new ApiException(400, "VALIDATION_ERROR", message);
    }
}
