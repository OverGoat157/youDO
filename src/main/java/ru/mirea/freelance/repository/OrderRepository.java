package ru.mirea.freelance.repository;

import ru.mirea.freelance.exception.DatabaseException;
import ru.mirea.freelance.model.Order;
import ru.mirea.freelance.model.OrderCategory;
import ru.mirea.freelance.model.OrderStatus;
import ru.mirea.freelance.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Хранилище заказов в PostgreSQL. Весь SQL по таблице orders находится здесь. */
public class OrderRepository implements Repository<Order, Long> {

    private static final String SELECT = "SELECT id, title, description, category, budget, deadline, status, "
            + "customer_id, freelancer_id, created_at FROM orders";

    private final DatabaseManager db;

    public OrderRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public Order save(Order order) {
        String sql = "INSERT INTO orders (title, description, category, budget, deadline, status, customer_id, freelancer_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id, created_at";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, order.getTitle());
            ps.setString(2, order.getDescription());
            ps.setString(3, order.getCategory().name());
            ps.setBigDecimal(4, order.getBudget());
            ps.setDate(5, java.sql.Date.valueOf(order.getDeadline()));
            ps.setString(6, order.getStatus().name());
            ps.setLong(7, order.getCustomerId());
            setNullableLong(ps, 8, order.getFreelancerId());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                order.setId(rs.getLong("id"));
                order.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            }
            return order;
        } catch (SQLException e) {
            throw new DatabaseException("Не удалось сохранить заказ: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Order> findById(Long id) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT + " WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Не удалось найти заказ с ID " + id + ": " + e.getMessage(), e);
        }
    }

    @Override
    public List<Order> findAll() {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT + " ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            return readAll(rs);
        } catch (SQLException e) {
            throw new DatabaseException("Не удалось получить список заказов: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(Order order) {
        String sql = "UPDATE orders SET title = ?, description = ?, category = ?, budget = ?, deadline = ?, "
                + "status = ?, freelancer_id = ? WHERE id = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, order.getTitle());
            ps.setString(2, order.getDescription());
            ps.setString(3, order.getCategory().name());
            ps.setBigDecimal(4, order.getBudget());
            ps.setDate(5, java.sql.Date.valueOf(order.getDeadline()));
            ps.setString(6, order.getStatus().name());
            setNullableLong(ps, 7, order.getFreelancerId());
            ps.setLong(8, order.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Не удалось обновить заказ с ID " + order.getId() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteById(Long id) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM orders WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Не удалось удалить заказ с ID " + id + ": " + e.getMessage(), e);
        }
    }

    /** Поиск по части названия без учёта регистра. */
    public List<Order> findByTitleContaining(String text) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT + " WHERE title ILIKE ? ORDER BY id")) {
            ps.setString(1, "%" + text + "%");
            try (ResultSet rs = ps.executeQuery()) {
                return readAll(rs);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Не удалось найти заказы по названию: " + e.getMessage(), e);
        }
    }

    public List<Order> findByCustomerId(Long customerId) {
        return findByLongColumn("customer_id", customerId);
    }

    public List<Order> findByFreelancerId(Long freelancerId) {
        return findByLongColumn("freelancer_id", freelancerId);
    }

    public List<Order> findByStatus(OrderStatus status) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT + " WHERE status = ? ORDER BY id")) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                return readAll(rs);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Не удалось найти заказы по статусу: " + e.getMessage(), e);
        }
    }

    /** Сколько заказов пользователь имеет как заказчик или исполнитель в указанных статусах. Для правила 7. */
    public long countByUserIdAndStatusIn(Long userId, Collection<OrderStatus> statuses) {
        if (statuses.isEmpty()) {
            return 0;
        }
        String placeholders = statuses.stream().map(s -> "?").collect(Collectors.joining(", "));
        String sql = "SELECT count(*) FROM orders WHERE (customer_id = ? OR freelancer_id = ?) "
                + "AND status IN (" + placeholders + ")";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, userId);
            int i = 3;
            for (OrderStatus s : statuses) {
                ps.setString(i++, s.name());
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Не удалось посчитать заказы пользователя с ID " + userId + ": " + e.getMessage(), e);
        }
    }

    private List<Order> findByLongColumn(String column, Long value) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT + " WHERE " + column + " = ? ORDER BY id")) {
            ps.setLong(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return readAll(rs);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Не удалось найти заказы по " + column + ": " + e.getMessage(), e);
        }
    }

    private void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    private List<Order> readAll(ResultSet rs) throws SQLException {
        List<Order> orders = new ArrayList<>();
        while (rs.next()) {
            orders.add(mapRow(rs));
        }
        return orders;
    }

        /** Собирает Order из текущей строки ResultSet. */
    private Order mapRow(ResultSet rs) throws SQLException {
        long freelancerId = rs.getLong("freelancer_id");
        Long freelancerIdOrNull = rs.wasNull() ? null : freelancerId;
        return new Order(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("description"),
                OrderCategory.valueOf(rs.getString("category")),
                rs.getBigDecimal("budget"),
                rs.getDate("deadline").toLocalDate(),
                OrderStatus.valueOf(rs.getString("status")),
                rs.getLong("customer_id"),
                freelancerIdOrNull,
                rs.getTimestamp("created_at").toLocalDateTime());
    }
}