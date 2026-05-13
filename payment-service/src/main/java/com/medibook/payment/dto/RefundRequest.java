package com.medibook.payment.dto;

import lombok.*;

/** Request body for POST /api/v1/payments/{id}/refund */
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class RefundRequest {
    /** Optional reason stored for audit trail */
    private String reason;
}
