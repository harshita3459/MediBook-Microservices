package com.medibook.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MediBook Payment Service — port 8085
 * Database: payment_db
 *
 * Responsibilities:
 *   - Process appointment payments (CARD / UPI / WALLET / CASH)
 *   - Handle refunds for cancelled appointments
 *   - Generate invoices for completed appointments
 *   - Provide provider earnings aggregation
 *
 * Called by: appointment-service (on booking + cancellation)
 * Calls:     notification-service (payment receipt dispatch)
 */
@SpringBootApplication
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
