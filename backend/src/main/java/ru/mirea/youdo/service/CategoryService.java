package ru.mirea.youdo.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import ru.mirea.youdo.domain.Category;
import ru.mirea.youdo.dto.CategoryDtos.*;
import ru.mirea.youdo.repository.CategoryRepository;
import ru.mirea.youdo.service.AccountIntegrations.AuditWriter;
import java.util.*;

@Service @Transactional(readOnly = true)
public class CategoryService {
    private final CategoryRepository categories;
    private final AuditWriter audit;
    public CategoryService(CategoryRepository categories, AuditWriter audit) { this.categories = categories; this.audit = audit; }
    @PreAuthorize("isAuthenticated()")
    public List<Response> list(Boolean active) {
        return (active == null ? categories.findAllByOrderByNameAscIdAsc() : categories.findByActiveOrderByNameAscIdAsc(active))
            .stream().map(Response::of).toList();
    }
    public Category findById(UUID id) { return categories.findById(id).orElseThrow(() -> AccountProblem.missing("Категория")); }
    /** Existing inactive references can be kept, but cannot be assigned to another/new order. */
    public Category requireAssignable(UUID requestedId, UUID previousId) {
        Category category = findById(requestedId);
        if (!category.isActive() && !category.getId().equals(previousId)) throw AccountProblem.invalid("categoryId", "Категория отключена");
        return category;
    }
    @Transactional @PreAuthorize("hasRole('ADMIN')")
    public Response create(Write input) {
        String code = validate(input);
        if (categories.existsByCode(code)) throw AccountProblem.duplicate("code");
        Category category = categories.saveAndFlush(new Category(code, input.name().strip(), input.active()));
        audit.record("CATEGORY_CREATED", "CATEGORY", category.getId(), "Создана категория");
        return Response.of(category);
    }
    @Transactional @PreAuthorize("hasRole('ADMIN')")
    public Response update(UUID id, Write input) {
        String code = validate(input);
        Category category = findById(id);
        if (categories.existsByCodeAndIdNot(code, id)) throw AccountProblem.duplicate("code");
        category.edit(code, input.name().strip(), input.active());
        categories.flush();
        audit.record("CATEGORY_UPDATED", "CATEGORY", id, "Изменена категория");
        return Response.of(category);
    }
    private String validate(Write input) {
        if (input.code() == null || !input.code().matches("[A-Za-z][A-Za-z0-9_]{0,49}")) throw AccountProblem.invalid("code", "Код: до 50 латинских букв, цифр или подчёркиваний; начало — буква");
        if (input.name() == null || input.name().isBlank() || input.name().strip().length() > 100) throw AccountProblem.invalid("name", "Укажите имя до 100 символов");
        if (input.active() == null) throw AccountProblem.invalid("active", "Укажите активность");
        return input.code().toUpperCase(Locale.ROOT);
    }
}
