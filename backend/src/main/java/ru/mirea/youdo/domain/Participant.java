package ru.mirea.youdo.domain;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
@Entity @Table(name="users")
public class Participant {
    public enum Role { CUSTOMER, FREELANCER }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=100) private String name;
    @Column(nullable=false,length=150,unique=true) private String email;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Role role;
    @Column(nullable=false,precision=3,scale=2) private BigDecimal rating;
    @Column(nullable=false) private LocalDate registeredAt;
    protected Participant() {}
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public Role getRole() { return role; }
}
