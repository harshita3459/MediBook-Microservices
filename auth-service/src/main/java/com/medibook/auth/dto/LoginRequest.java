package com.medibook.auth.dto;

import com.medibook.auth.entity.User;
import jakarta.validation.constraints.*;
import lombok.*;

public class LoginRequest {
		 
	    @Email(message = "Invalid email format")
	    @NotBlank(message = "Email is required")
	    private String email;
	 
	    @NotBlank(message = "Password is required")
	    private String password;
	 
	    public String getEmail()    { 
	    	return email; 
	    }
	    
	    public String getPassword() { 
	    	return password; 
	    }
}
