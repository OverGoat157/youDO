package ru.mirea.freelance.repository;

import ru.mirea.freelance.model.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** Хранилище пользователей для тестов без подключения к базе. */
public class InMemoryUserRepository implements UserRepository {
    private final Map<Long, User> store = new HashMap<>();
    private final AtomicLong seq = new AtomicLong();

    @Override
    public User save(User user) {
        user.setId(seq.incrementAndGet());
        store.put(user.getId(), user);
        return user;
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<User> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void update(User user) {
        store.replace(user.getId(), user);
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return store.values().stream().filter(user -> Objects.equals(user.getEmail(), email)).findFirst();
    }

    @Override
    public boolean existsById(Long id) {
        return store.containsKey(id);
    }
}
