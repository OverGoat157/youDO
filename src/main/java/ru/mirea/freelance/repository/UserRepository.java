
  package ru.mirea.freelance.repository;

  import ru.mirea.freelance.model.User;

  import java.util.Optional;

  /** Хранилище пользователей. Реализации: JDBC в приложении, in-memory в тестах. */
  public interface UserRepository extends Repository<User, Long> {

      Optional<User> findByEmail(String email);

      boolean existsById(Long id);
  }