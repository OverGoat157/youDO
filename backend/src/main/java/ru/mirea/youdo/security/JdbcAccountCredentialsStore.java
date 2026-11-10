package ru.mirea.youdo.security;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.youdo.error.ApiException;

/** Real persistence adapter, without account administration endpoints or duplicate JPA entities. */
@Repository
public class JdbcAccountCredentialsStore implements AccountCredentialsStore {
    private final JdbcTemplate jdbc;
    public JdbcAccountCredentialsStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private Optional<AccountCredentials> find(String clause, Object arg) {
        // Read roles and credentials from the same statement snapshot.
        return jdbc.query("""
            SELECT a.*, ARRAY(SELECT role FROM account_roles WHERE account_id = a.id) AS roles
            FROM user_accounts a WHERE
            """ + clause, (rs, n) -> new AccountCredentials(
                rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("login"),
                rs.getString("password_hash"), Set.of((String[]) rs.getArray("roles").getArray()),
                rs.getBoolean("active"), rs.getBoolean("must_change_password"), rs.getLong("credentials_version")
            ), arg).stream().findFirst();
    }
    @Override public Optional<AccountCredentials> findByLogin(String login) { return find("a.login = ?", login); }
    @Override public Optional<AccountCredentials> findById(UUID id) { return find("a.id = ?", id); }
    @Override
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = ApiException.class)
    public AccountCredentials lock(UUID id) {
        jdbc.query("SELECT id FROM user_accounts WHERE id = ? FOR UPDATE",
            (rs, n) -> rs.getObject("id", UUID.class), id).stream().findFirst().orElseThrow(ApiException::unauthorized);
        return findById(id).orElseThrow(ApiException::unauthorized);
    }
    @Override public void changePassword(UUID id, String hash) {
        jdbc.update("""
            UPDATE user_accounts SET password_hash = ?, must_change_password = false,
                credentials_version = credentials_version + 1, version = version + 1, updated_at = now()
            WHERE id = ?
            """, hash, id);
    }
    @Override @Transactional public void bootstrap(String login, String name, String hash) {
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update("insert into user_accounts(id,name,login,password_hash) values (?,?,?,?) on conflict (login) do nothing", id, name, login, hash);
        if (inserted == 1) jdbc.update("insert into account_roles(account_id,role) values (?, 'ADMIN')", id);
    }
}
