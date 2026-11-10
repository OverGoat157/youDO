package ru.mirea.youdo.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "user_accounts", uniqueConstraints = @UniqueConstraint(name = "uk_account_login", columnNames = "login"))
public class UserAccount {
    @Id private UUID id;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, length = 150) private String login;
    @JsonIgnore @Column(name = "password_hash", nullable = false, length = 60) private String passwordHash;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "account_roles", joinColumns = @JoinColumn(name = "account_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_account_role", columnNames = {"account_id", "role"}))
    @Enumerated(EnumType.STRING) @Column(name = "role", nullable = false, length = 20)
    private Set<AccessRole> roles = new HashSet<>();
    @Column(nullable = false) private boolean active;
    @Column(name = "must_change_password", nullable = false) private boolean mustChangePassword;
    @Column(name = "credentials_version", nullable = false) private long credentialsVersion;
    @Version @Column(nullable = false) private long version;

    protected UserAccount() {}
    public UserAccount(String name, String login, String hash, Set<AccessRole> roles) {
        this.id = UUID.randomUUID(); this.name = name; this.login = login;
        this.passwordHash = hash; this.roles = new HashSet<>(roles);
        this.active = true; this.mustChangePassword = true;
    }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getLogin() { return login; }
    @JsonIgnore public String getPasswordHash() { return passwordHash; }
    public Set<AccessRole> getRoles() { return Set.copyOf(roles); }
    public boolean isActive() { return active; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public long getCredentialsVersion() { return credentialsVersion; }
    public void edit(String name, String login, Set<AccessRole> roles, boolean active) {
        this.name = name; this.login = login; this.roles = new HashSet<>(roles); this.active = active;
    }
    public void replacePassword(String hash, boolean temporary) {
        passwordHash = hash; mustChangePassword = temporary;
    }
    public void invalidateCredentials() { credentialsVersion = Math.incrementExact(credentialsVersion); }
}
