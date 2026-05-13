package com.medibook.payment.dto;

import lombok.*;

/** Provider earnings summary — returned by GET /api/v1/payments/provider/{id}/earnings */
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class EarningsSummary {
    private Long providerId;
    private Double totalCollected;    // PAID + CASH
    private Double totalRefunded;     // REFUNDED
    private Double pendingAmount;     // PENDING
    private Long totalTransactions;
}
