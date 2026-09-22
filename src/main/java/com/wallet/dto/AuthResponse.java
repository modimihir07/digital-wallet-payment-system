package com.wallet.dto;

import java.util.List;

/** Returned after register/login. */
public class AuthResponse {
    private String token;
    private String tokenType = "Bearer";
    private long userId;
    private String email;
    private List<String> roles;

    public AuthResponse() {
    }

    /** Builds a full auth response. */
    public AuthResponse(String token, long userId, String email, List<String> roles) {
        this.token = token;
        this.userId = userId;
        this.email = email;
        this.roles = roles;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }
    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }
}
