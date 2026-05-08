package com.medibook.record;

import org.springframework.boot.SpringApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MediBook Record Service — port 8088
 * Database: record_db
 *
 * Responsibilities:
 *   - Providers create Electronic Medical Records after COMPLETED appointments
 *   - Stores diagnosis, prescription, clinical notes, attachment URL
 *   - Tracks follow-up dates and triggers reminder notifications
 *   - Patients view their own records; providers view records they created
 *   - Admin has read-only audit access
 *
 * Depends on:
 *   - appointment-service (validate appointment is COMPLETED before record creation)
 *   - notification-service (send follow-up reminders on followUpDate)
 *
 * @EnableScheduling activates the daily follow-up date check scheduler
 */
@EnableFeignClients
@SpringBootApplication
@EnableScheduling
public class RecordServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RecordServiceApplication.class, args);
    }
}
