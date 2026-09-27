package ru.mirea.freelance.service;

import ru.mirea.freelance.exception.BusinessException;
import ru.mirea.freelance.exception.EntityNotFoundException;
import ru.mirea.freelance.exception.ValidationException;
import ru.mirea.freelance.model.User;
import ru.mirea.freelance.model.UserRole;
import ru.mirea.freelance.repository.OrderRepository;
import ru.mirea.freelance.repository.UserRepository;

import java.util.List;
import java.util.Objects;

/** Управление пользователями и проверка бизнес-правил независимо от способа хранения. */
public class UserService {
    private final UserRepository users;
    private final OrderRepository orders;

    public UserService(UserRepository users, OrderRepository orders) {
        this.users = users;
        this.orders = orders;
    }

    public User create(String name, String email, UserRole role) {
        validate(name, email, role, null);
        return users.save(new User(name.trim(), email.trim(), role));
    }

    public List<User> findAll() {
        return users.findAll();
    }

    public User getById(Long id) {
        if (id == null) {
            throw new EntityNotFoundException("Пользователь", id);
        }
        return users.findById(id).orElseThrow(() -> new EntityNotFoundException("Пользователь", id));
    }

    public User update(Long id, String name, String email, UserRole role) {
        User user = getById(id);
        validate(name, email, role, id);
        user.setName(name.trim());
        user.setEmail(email.trim());
        user.setRole(role);
        users.update(user);
        return user;
    }

    public void delete(Long id) {
        getById(id);
        if (orders.countActiveByUserId(id) > 0) {
            throw new BusinessException("Нельзя удалить пользователя с ID " + id + ": у него есть активные заказы");
        }
        users.deleteById(id);
    }

    private void validate(String name, String email, UserRole role, Long excludedId) {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Имя не может быть пустым: " + name);
        }
        if (email == null || !email.contains("@")) {
            throw new ValidationException("Email должен содержать @: " + email);
        }
        if (role == null) {
            throw new ValidationException("Недопустимая роль: " + role);
        }
        users.findByEmail(email.trim()).filter(user -> !Objects.equals(user.getId(), excludedId))
                .ifPresent(user -> {
                    throw new ValidationException("Пользователь с email " + email.trim() + " уже существует");
                });
    }
}
