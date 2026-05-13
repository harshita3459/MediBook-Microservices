package com.medibook.auth.dto;

import lombok.*;
import jakarta.validation.constraints.*;


public class UpdateProfileRequest {
	@Size(min = 2, max = 100)
    private String fullName;
 
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid phone number")
    private String phone;
 
    private String profilePicUrl;
 
    public String getFullName()      { return fullName; }
    public String getPhone()         { return phone; }
    public String getProfilePicUrl() { return profilePicUrl; }
}
