package ru.mirea.freelance.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/** Заказ на фриланс-работу. Заказчик обязателен, исполнитель появляется после назначения. */
public class Order {

    private Long id;
    private String title;
    private String description;
    private OrderCategory category;
    private BigDecimal budget;
    private LocalDate deadline;
    private OrderStatus status;
    private Long customerId;
    private Long freelancerId;
    private LocalDateTime createdAt;

    /** Новый заказ открыт и ещё не имеет исполнителя; id назначит репозиторий. */
    public Order(String title, String description, OrderCategory category,
                 BigDecimal budget, LocalDate deadline, Long customerId) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.budget = budget;
        this.deadline = deadline;
        this.status = OrderStatus.OPEN;
        this.customerId = customerId;
        this.createdAt = LocalDateTime.now();
    }

    /** Для чтения из базы: все поля известны. */
    public Order(Long id, String title, String description, OrderCategory category,
                 BigDecimal budget, LocalDate deadline, OrderStatus status,
                 Long customerId, Long freelancerId, LocalDateTime createdAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.category = category;
        this.budget = budget;
        this.deadline = deadline;
        this.status = status;
        this.customerId = customerId;
        this.freelancerId = freelancerId;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public OrderCategory getCategory() { return category; }
    public void setCategory(OrderCategory category) { this.category = category; }

    public BigDecimal getBudget() { return budget; }
    public void setBudget(BigDecimal budget) { this.budget = budget; }

    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public Long getFreelancerId() { return freelancerId; }
    public void setFreelancerId(Long freelancerId) { this.freelancerId = freelancerId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Order{id=" + id + ", title='" + title + "', category=" + category
                + ", budget=" + budget + ", deadline=" + deadline + ", status=" + status
                + ", customerId=" + customerId + ", freelancerId=" + freelancerId + '}';
    }
}
