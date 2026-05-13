package com.medibook.auth.dto;

import jakarta.validation.constraints.*;

public class ChangePasswordRequest {
	@NotBlank(message = "Current password is required")
    private String currentPassword;
 
    @NotBlank(message = "New password is required")
    @Size(min = 8, message = "New password must be at least 8 characters")
    @Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@$!%*#?&]{8,}$",
        message = "Password must contain at least one letter and one number"
    )
    private String newPassword;
 
    public String getCurrentPassword() { return currentPassword; }
    public String getNewPassword()      { return newPassword; }
}

