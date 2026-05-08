package com.medibook.review;

import org.springframework.boot.SpringApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MediBook Review Service — port 8086
 * Database: review_db
 *
 * Responsibilities:
 *   - Accept one patient review per completed appointment
 *   - Compute average provider rating
 *   - Update provider-service avgRating after each review
 *   - Admin moderation (delete inappropriate reviews)
 */
@EnableFeignClients
@SpringBootApplication
public class ReviewServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReviewServiceApplication.class, args);
    }
}
