package ru.mirea.youdo.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "categories", uniqueConstraints = @UniqueConstraint(name = "uk_category_code", columnNames = "code"))
public class Category {
    @Id private UUID id;
    @Column(nullable = false, length = 50) private String code;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false) private boolean active;
    @Version @Column(nullable = false) private long version;
    protected Category() {}
    public Category(String code, String name, boolean active) {
        id = UUID.randomUUID(); edit(code, name, active);
    }
    public void edit(String code, String name, boolean active) {
        this.code = code; this.name = name; this.active = active;
    }
    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
}
