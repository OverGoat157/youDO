package ru.mirea.freelance.model;

import java.time.LocalDate;
import java.util.Objects;

/** Участник биржи: заказчик или фрилансер. */
public class User {

    private Long id;
    private String name;
    private String email;
    private UserRole role;
    private double rating;
    private LocalDate registeredAt;

    /** Для создания нового пользователя: id и дату регистрации назначит база. */
    public User(String name, String email, UserRole role) {
        this.name = name;
        this.email = email;
        this.role = role;
    }

    /** Для чтения из базы: все поля известны. */
    public User(Long id, String name, String email, UserRole role, double rating, LocalDate registeredAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.rating = rating;
        this.registeredAt = registeredAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }

    public LocalDate getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDate registeredAt) { this.registeredAt = registeredAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", name='" + name + "', email='" + email
                + "', role=" + role + ", rating=" + rating + ", registeredAt=" + registeredAt + '}';
    }
}