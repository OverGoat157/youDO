package ru.mirea.freelance.repository;

import ru.mirea.freelance.exception.DatabaseException;
import ru.mirea.freelance.model.User;
import ru.mirea.freelance.model.UserRole;
import ru.mirea.freelance.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC-реализация UserRepository для PostgreSQL. Весь SQL по таблице users находится здесь. */
public class JdbcUserRepository implements UserRepository {

    private final DatabaseManager db;

    public JdbcUserRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public User save(User user) {
        String sql = "INSERT INTO users (name, email, role) VALUES (?, ?, ?) RETURNING id, rating, registered_at";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, user.getName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getRole().name());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                user.setId(rs.getLong("id"));
                user.setRating(rs.getDouble("rating"));
                user.setRegisteredAt(rs.getDate("registered_at").toLocalDate());
            }
            return user;
        } catch (SQLException e) {
            throw databaseError("Не удалось сохранить пользователя.", e);
        }
    }

    @Override
    public Optional<User> findById(Long id) {
        String sql = "SELECT id, name, email, role, rating, registered_at FROM users WHERE id = ?";

        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw databaseError("Не удалось найти пользователя с ID " + id, e);

        }
    }

    @Override
    public List<User> findAll() {
        String sql = "SELECT id, name, email, role, rating, registered_at FROM users ORDER BY id";
        List<User> users = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(mapRow(rs));
            }
            return users;
        } catch (SQLException e) {
            throw databaseError("Не удалось получить всех пользователей.", e);
        }
    }

    @Override
    public void update(User user) {
        String sql = "UPDATE users SET name = ?, email = ?, role = ?, rating = ? WHERE id = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, user.getName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getRole().name());
            ps.setDouble(4, user.getRating());
            ps.setLong(5, user.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw databaseError("Не удалось обновить пользователя с ID " + user.getId(), e);
        }
    }

    @Override
    public void deleteById(Long id) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw databaseError("Не удалось удалить пользователя с ID " + id, e);
        }
    }

    @Override
    public Optional<User> findByEmail(String email) {
        String sql = "SELECT id, name, email, role, rating, registered_at FROM users WHERE email = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw databaseError("Не удалось найти пользователя по email " + email, e);
        }
    }

    @Override
    public boolean existsById(Long id) {
        return findById(id).isPresent();
    }

    private DatabaseException databaseError(String action, SQLException error) {
        String message = switch (String.valueOf(error.getSQLState())) {
            case "23505" -> "Пользователь с таким email уже существует.";
            case "23503" -> "Нельзя удалить пользователя: на него ссылаются заказы, в том числе завершённые.";
            case "23514" -> "Данные пользователя нарушают ограничения базы. Проверьте роль и рейтинг.";
            default -> action;
        };
        return new DatabaseException(message, error);
    }

    /** Собирает User из текущей строки ResultSet. Единственное место, где колонки превращаются в поля. */
    private User mapRow(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("email"),
                UserRole.valueOf(rs.getString("role")),
                rs.getDouble("rating"),
                rs.getDate("registered_at").toLocalDate());
    }
}
