package com.model;

/** Authenticated account data; password hashes never leave the authentication service. */
public final class AccountIdentity {

    private final long accountId;

    private final String username;

    private final String role;

    public AccountIdentity(long accountId, String username, String role) {
        this.accountId = accountId;
        this.username = username;
        this.role = role;
    }

    public long getAccountId() {
        return accountId;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }
}
