package ru.mirea.youdo.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import ru.mirea.youdo.dto.AccountDtos.*;
import ru.mirea.youdo.service.AccountService;
import java.net.URI;
import java.util.UUID;

@RestController @RequestMapping("/api/admin/users") @PreAuthorize("hasRole('ADMIN')")
public class AdminUsersController {
    private final AccountService accounts;
    public AdminUsersController(AccountService accounts) { this.accounts = accounts; }
    @GetMapping public PageResponse<Response> list(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return accounts.list(page, size);
    }
    @PostMapping public ResponseEntity<Response> create(@Valid @RequestBody Create input) {
        Response result = accounts.create(input);
        return ResponseEntity.created(URI.create("/api/admin/users/" + result.id())).body(result);
    }
    @PutMapping("/{id}") public Response update(@PathVariable UUID id, @Valid @RequestBody Update input) { return accounts.update(id, input); }
    @PostMapping("/{id}/reset-password") public Response reset(@PathVariable UUID id, @Valid @RequestBody Reset input) {
        return accounts.resetPassword(id, input.temporaryPassword());
    }
}
