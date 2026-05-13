package com.medibook.provider.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProviderRequest {
	 
	    // All fields optional — only provided fields will be updated (PATCH-style)
	    @Size(max = 100)
	    private String specialization;
	 
	    @Size(max = 300)
	    private String qualification;
	 
	    @Min(0) @Max(60)
	    private Integer experienceYears;
	 
	    @Size(max = 2000)
	    private String bio;
	 
	    private String clinicName;
	    private String clinicAddress;
	 
	    @Size(max = 100)
	    private String city;
	 
	    @DecimalMin("0.0")
	    private Double consultationFee;
	 
	    private String profilePicUrl;
	    private String languagesSpoken;
}
