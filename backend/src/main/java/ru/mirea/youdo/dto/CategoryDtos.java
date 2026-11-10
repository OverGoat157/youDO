package ru.mirea.youdo.dto;

import jakarta.validation.constraints.*;
import ru.mirea.youdo.domain.Category;
import java.util.UUID;

public final class CategoryDtos {
    private CategoryDtos() {}
    public record Write(@NotBlank @Size(max=50) @Pattern(regexp="[A-Za-z][A-Za-z0-9_]*") String code,
                        @NotBlank @Size(max=100) String name, @NotNull Boolean active) {}
    public record Response(UUID id, String code, String name, boolean active) {
        public static Response of(Category c) { return new Response(c.getId(), c.getCode(), c.getName(), c.isActive()); }
    }
}
