package com.medibook.auth.dto;
 
import com.medibook.auth.entity.User;
import jakarta.validation.constraints.*;
import lombok.*;

class RegisterRequest {
 
    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String fullName;
 
    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String email;
 
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@$!%*#?&]{8,}$",
        message = "Password must contain at least one letter and one number"
    )
    private String password;
 
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid Indian phone number")
    private String phone;
 
    @NotNull(message = "Role is required")
    private User.Role role;
 
    public String getFullName()  { 
    	return fullName; 
    }
    public String getEmail()     { return email; }
    public String getPassword()  { return password; }
    public String getPhone()     { return phone; }
    public User.Role getRole()   { return role; }
}