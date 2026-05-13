package com.medibook.schedule.scheduler;

import com.medibook.schedule.repository.SlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * SlotCleanupScheduler — automated background job that runs nightly.
 *
 * WHY this is needed:
 *   Providers create hundreds of slots in advance.
 *   If a slot passes its date without any booking, it just sits in the DB
 *   forever — wasting space and polluting queries.
 *   This job deletes those stale slots automatically.
 *
 * What it deletes:
 *   - Slots whose slotDate is BEFORE today
 *   - AND isBooked = false (booked slots stay for appointment records)
 *   - AND isBlocked = false (blocked slots stay to show provider history)
 *
 * Schedule: runs every day at 2:00 AM (low-traffic time)
 *   Cron: "0 0 2 * * *"
 *         │ │ │ │ │ └─ every day of week
 *         │ │ │ │ └─── every month
 *         │ │ │ └───── every day of month
 *         │ │ └─────── 2 = 2am
 *         │ └───────── 0 = minute 0
 *         └─────────── 0 = second 0
 */
@Component
@RequiredArgsConstructor
@Slf4j
@EnableScheduling // enables Spring's @Scheduled annotation processing in this service
public class SlotCleanupScheduler {

    private final SlotRepository slotRepository;

    /**
     * Runs at 2:00 AM every day.
     * Bulk deletes all expired unbooked slots in a single SQL DELETE statement.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void purgeExpiredSlots() {
        LocalDate today = LocalDate.now();
        log.info("SlotCleanupScheduler starting — deleting slots before {}", today);

        // Single bulk DELETE query — much faster than loading entities and deleting individually
        int deleted = slotRepository.deleteExpiredUnbookedSlots(today);

        log.info("SlotCleanupScheduler completed — {} expired slots deleted", deleted);
    }

    /**
     * Runs every hour to count and log slot statistics.
     * Useful for monitoring dashboards — alerts if slot count drops unexpectedly.
     * "0 0 * * * *" = top of every hour
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional(readOnly = true)
    public void logSlotStats() {
        long totalSlots   = slotRepository.count();
        long expiredSlots = slotRepository.findExpiredUnbookedSlots(LocalDate.now()).size();
        log.info("Slot stats — total: {}, expiring (unbooked past slots): {}",
            totalSlots, expiredSlots);
    }
}