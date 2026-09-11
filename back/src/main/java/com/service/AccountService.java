package com.service;

import com.model.AccountIdentity;
import com.utils.TeachingPasswords;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Login and token lifecycle for the unified account schema. */
@Service
public class AccountService {

    public static final String ADMIN_ROLE = "ADMIN";

    public static final String TEACHER_ROLE = "TEACHER";

    private static final int TOKEN_LIFETIME_HOURS = 1;

    private final JdbcTemplate jdbc;

    public AccountService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns null when the username, password or selected role does not match. */
    @Transactional
    public String login(String username, String password, String role) {
        if (username == null || password == null) {
            return null;
        }
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        "SELECT id,password_hash FROM account WHERE username=? AND role=? FOR"
                                + " UPDATE",
                        username,
                        role);
        if (rows.isEmpty()
                || !TeachingPasswords.matches(
                        password, (String) rows.get(0).get("password_hash"))) {
            return null;
        }
        long accountId = ((Number) rows.get(0).get("id")).longValue();
        String token =
                UUID.randomUUID().toString().replace("-", "")
                        + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("DELETE FROM token WHERE account_id=?", accountId);
        jdbc.update(
                "INSERT INTO token(account_id,token,expires_at) VALUES (?,?,?)",
                accountId,
                token,
                Timestamp.from(Instant.now().plus(TOKEN_LIFETIME_HOURS, ChronoUnit.HOURS)));
        return token;
    }

    /** Returns null for a missing, expired or revoked token. Role is read fresh from account. */
    public AccountIdentity findIdentity(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        List<AccountIdentity> rows =
                jdbc.query(
                        "SELECT a.id,a.username,a.role FROM token t JOIN account a ON"
                                + " a.id=t.account_id WHERE t.token=? AND"
                                + " t.expires_at>CURRENT_TIMESTAMP",
                        (row, index) ->
                                new AccountIdentity(
                                        row.getLong("id"),
                                        row.getString("username"),
                                        row.getString("role")),
                        token);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void logout(String token) {
        jdbc.update("DELETE FROM token WHERE token=?", token);
    }
}
