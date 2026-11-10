package ru.mirea.youdo.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import ru.mirea.youdo.dto.CategoryDtos.*;
import ru.mirea.youdo.service.CategoryService;
import java.net.URI;
import java.util.*;

@RestController @RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService categories;
    public CategoryController(CategoryService categories) { this.categories = categories; }
    @GetMapping @PreAuthorize("isAuthenticated()")
    public List<Response> list(@RequestParam(required=false) Boolean active) { return categories.list(active); }
    @PostMapping @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Response> create(@Valid @RequestBody Write input) {
        Response result = categories.create(input);
        return ResponseEntity.created(URI.create("/api/categories/" + result.id())).body(result);
    }
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public Response update(@PathVariable UUID id, @Valid @RequestBody Write input) { return categories.update(id, input); }
}
