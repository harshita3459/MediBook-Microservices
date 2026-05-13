package com.medibook.auth.dto;

import lombok.*;

public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private long expiresIn;
    private UserSummary user;
 
    public LoginResponse(String accessToken, String refreshToken,
                         long expiresIn, UserSummary user) {
        this.accessToken  = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn    = expiresIn;
        this.user         = user;
    }
 
    public String getAccessToken()  { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public String getTokenType()    { return tokenType; }
    public long   getExpiresIn()    { return expiresIn; }
    public UserSummary getUser()    { return user; }
 
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class UserSummary {
        private Long   userId;
        private String fullName;
        private String email;
        private String role;
        private String profilePicUrl;
    }
}