package com.medibook.auth.service;

import com.medibook.auth.entity.User;
import com.medibook.auth.exception.UserAlreadyExistsException;

import java.util.Map;

public interface AuthService {

    User register(String fullName, String email,
                  String password, String phone, User.Role role) throws UserAlreadyExistsException;

    Map<String, Object> login(String email, String password);

    void logout(String token);

    boolean validateToken(String token);

    String refreshToken(String refreshToken);

    User getUserByEmail(String email);

    User getUserById(Long userId);

    User updateProfile(Long userId, String fullName,
                       String phone, String profilePicUrl);

    void changePassword(Long userId,
                        String currentPassword, String newPassword);

    void deactivateAccount(Long userId);

    User processOAuthUser(String email, String fullName,
                          String providerId, User.AuthProvider provider);
}