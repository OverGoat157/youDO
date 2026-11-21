package ru.mirea.youdo.filter;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import ru.mirea.youdo.domain.AuditEvent;
import ru.mirea.youdo.dto.AuditDtos.AuditQuery;

/**
 * Условия поиска по журналу через Criteria API. Значения пользователя передаются в запрос
 * параметрами, SQL из строки не собирается.
 */
public final class AuditSpecifications {
    private AuditSpecifications() {}

    public static Specification<AuditEvent> matching(AuditQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> all = new ArrayList<>();
            if (hasText(query.q())) {
                String like = "%" + escapeLike(query.q().trim().toLowerCase(Locale.ROOT)) + "%";
                // Свободный текст: логин, код действия, тип сущности, описание, correlation ID
                all.add(cb.or(
                    cb.like(cb.lower(root.get("actorLogin")), like, '\\'),
                    cb.like(cb.lower(root.get("action")), like, '\\'),
                    cb.like(cb.lower(root.get("entityType")), like, '\\'),
                    cb.like(cb.lower(root.get("summary")), like, '\\'),
                    cb.like(cb.lower(root.get("correlationId")), like, '\\')));
            }
            if (hasText(query.action())) {
                all.add(cb.equal(root.get("action"), query.action().trim().toUpperCase(Locale.ROOT)));
            }
            if (hasText(query.entityType())) {
                all.add(cb.equal(root.get("entityType"), query.entityType().trim().toUpperCase(Locale.ROOT)));
            }
            if (query.entityId() != null) {
                all.add(cb.equal(root.get("entityId"), query.entityId()));
            }
            if (query.from() != null) {
                all.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), query.from()));
            }
            if (query.to() != null) {
                all.add(cb.lessThan(root.get("occurredAt"), query.to()));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** «%» и «_» во вводе ищутся как обычные символы, а не как шаблон LIKE. */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
