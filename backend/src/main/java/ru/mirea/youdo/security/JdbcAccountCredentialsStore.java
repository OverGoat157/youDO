package ru.mirea.youdo.security;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.youdo.error.ApiException;

/** Real persistence adapter, without account administration endpoints or duplicate JPA entities. */
@Repository
public class JdbcAccountCredentialsStore implements AccountCredentialsStore {
    private final JdbcTemplate jdbc;
    public JdbcAccountCredentialsStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private Optional<AccountCredentials> find(String clause, Object arg) {
        return jdbc.query("select * from user_accounts where " + clause, (rs, n) -> {
            UUID id = rs.getObject("id", UUID.class);
            var roles = new HashSet<>(jdbc.queryForList("select role from account_roles where account_id=?", String.class, id));
            return new AccountCredentials(id, rs.getString("name"), rs.getString("login"),
                rs.getString("password_hash"), roles, rs.getBoolean("active"),
                rs.getBoolean("must_change_password"), rs.getLong("credentials_version"));
        }, arg).stream().findFirst();
    }
    @Override public Optional<AccountCredentials> findByLogin(String login) { return find("login = ?", login); }
    @Override public Optional<AccountCredentials> findById(UUID id) { return find("id = ?", id); }
    @Override public AccountCredentials lock(UUID id) { return find("id = ? for update", id).orElseThrow(ApiException::unauthorized); }
    @Override public void changePassword(UUID id, String hash) {
        jdbc.update("update user_accounts set password_hash=?, must_change_password=false, credentials_version=credentials_version+1, updated_at=now() where id=?", hash, id);
    }
    @Override @Transactional public void bootstrap(String login, String name, String hash) {
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update("insert into user_accounts(id,name,login,password_hash) values (?,?,?,?) on conflict (login) do nothing", id, name, login, hash);
        if (inserted == 1) jdbc.update("insert into account_roles(account_id,role) values (?, 'ADMIN')", id);
    }
}
