package com.medibook.schedule.service;

import com.medibook.schedule.entity.AvailabilitySlot;
import com.medibook.schedule.exception.SlotAlreadyBookedException;
import com.medibook.schedule.exception.SlotConflictException;
import com.medibook.schedule.exception.SlotNotFoundException;
import com.medibook.schedule.repository.SlotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleServiceImpl Tests")
class ScheduleServiceImplTest {

    @Mock SlotRepository slotRepository;
    @InjectMocks ScheduleServiceImpl scheduleService;

    private static final LocalDate TOMORROW  = LocalDate.now().plusDays(1);
    private static final LocalTime T_1000    = LocalTime.of(10, 0);
    private static final LocalTime T_1030    = LocalTime.of(10, 30);

    private AvailabilitySlot buildSlot(Long id, Long providerId,
                                        boolean booked, boolean blocked) {
        return AvailabilitySlot.builder()
                .slotId(id)
                .providerId(providerId)
                .slotDate(TOMORROW)
                .startTime(T_1000)
                .endTime(T_1030)
                .durationMinutes(30)
                .isBooked(booked)
                .isBlocked(blocked)
                .version(0L)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // addSlot
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("addSlot()")
    class AddSlotTests {

        @Test
        @DisplayName("✅ creates slot when no overlap and future date")
        void addSlot_noConflict_savesSlot() {
            given(slotRepository.existsOverlappingSlot(
                    eq(1L), eq(TOMORROW), any(), any())).willReturn(false);
            AvailabilitySlot saved = buildSlot(1L, 1L, false, false);
            given(slotRepository.save(any())).willReturn(saved);

            AvailabilitySlot result = scheduleService.addSlot(
                    1L, TOMORROW, T_1000, T_1030, "NONE");

            assertThat(result).isNotNull();
            assertThat(result.isBooked()).isFalse();
            assertThat(result.isBlocked()).isFalse();
        }

        @Test
        @DisplayName("❌ throws SlotConflictException when overlap detected")
        void addSlot_overlap_throwsConflict() {
            given(slotRepository.existsOverlappingSlot(
                    eq(1L), eq(TOMORROW), any(), any())).willReturn(true);

            assertThatThrownBy(() -> scheduleService.addSlot(
                    1L, TOMORROW, T_1000, T_1030, "NONE"))
                    .isInstanceOf(SlotConflictException.class);

            then(slotRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("❌ throws IllegalArgumentException for past date")
        void addSlot_pastDate_throwsIllegalArgument() {
            LocalDate yesterday = LocalDate.now().minusDays(1);

            assertThatThrownBy(() -> scheduleService.addSlot(
                    1L, yesterday, T_1000, T_1030, "NONE"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("past");
        }

        @Test
        @DisplayName("❌ throws IllegalArgumentException when end ≤ start")
        void addSlot_endBeforeStart_throwsIllegalArgument() {
            LocalTime start = LocalTime.of(10, 30);
            LocalTime end   = LocalTime.of(10, 0);  // before start

            assertThatThrownBy(() -> scheduleService.addSlot(
                    1L, TOMORROW, start, end, "NONE"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("End time");
        }

        @Test
        @DisplayName("✅ duration is auto-calculated and stored on the slot")
        void addSlot_durationCalculatedCorrectly() {
            given(slotRepository.existsOverlappingSlot(anyLong(), any(), any(), any()))
                    .willReturn(false);

            AvailabilitySlot saved = buildSlot(1L, 1L, false, false);
            given(slotRepository.save(any())).willReturn(saved);

            scheduleService.addSlot(1L, TOMORROW, LocalTime.of(9, 0), LocalTime.of(9, 45), "NONE");

            then(slotRepository).should().save(argThat(s ->
                    s.getDurationMinutes() == 45
            ));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // bookSlot — with optimistic locking
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("bookSlot() — optimistic locking")
    class BookSlotTests {

        @Test
        @DisplayName("✅ books available slot successfully")
        void bookSlot_availableSlot_setsBooked() {
            AvailabilitySlot slot = buildSlot(1L, 1L, false, false);
            given(slotRepository.findByIdForBooking(1L)).willReturn(Optional.of(slot));
            given(slotRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            AvailabilitySlot result = scheduleService.bookSlot(1L, 42L);

            assertThat(result.isBooked()).isTrue();
            assertThat(result.getAppointmentId()).isEqualTo(42L);
        }

        @Test
        @DisplayName("❌ throws SlotAlreadyBookedException when slot already booked")
        void bookSlot_alreadyBooked_throwsException() {
            AvailabilitySlot slot = buildSlot(1L, 1L, true, false); // already booked
            given(slotRepository.findByIdForBooking(1L)).willReturn(Optional.of(slot));

            assertThatThrownBy(() -> scheduleService.bookSlot(1L, 99L))
                    .isInstanceOf(SlotAlreadyBookedException.class)
                    .hasMessageContaining("1");
        }

        @Test
        @DisplayName("❌ throws SlotAlreadyBookedException when slot is blocked")
        void bookSlot_blocked_throwsException() {
            AvailabilitySlot slot = buildSlot(1L, 1L, false, true); // blocked
            given(slotRepository.findByIdForBooking(1L)).willReturn(Optional.of(slot));

            assertThatThrownBy(() -> scheduleService.bookSlot(1L, 99L))
                    .isInstanceOf(SlotAlreadyBookedException.class)
                    .hasMessageContaining("blocked");
        }

        @Test
        @DisplayName("❌ optimistic lock collision → SlotAlreadyBookedException")
        void bookSlot_optimisticLockCollision_throwsException() {
            AvailabilitySlot slot = buildSlot(1L, 1L, false, false);
            given(slotRepository.findByIdForBooking(1L)).willReturn(Optional.of(slot));
            given(slotRepository.save(any()))
                    .willThrow(new ObjectOptimisticLockingFailureException(AvailabilitySlot.class, 1L));

            assertThatThrownBy(() -> scheduleService.bookSlot(1L, 99L))
                    .isInstanceOf(SlotAlreadyBookedException.class)
                    .hasMessageContaining("just booked by another patient");
        }

        @Test
        @DisplayName("❌ throws SlotNotFoundException for non-existent slotId")
        void bookSlot_notFound_throwsSlotNotFoundException() {
            given(slotRepository.findByIdForBooking(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> scheduleService.bookSlot(999L, 1L))
                    .isInstanceOf(SlotNotFoundException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // releaseSlot
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("releaseSlot()")
    class ReleaseSlotTests {

        @Test
        @DisplayName("✅ releases booked slot — isBooked=false, appointmentId=null")
        void release_bookedSlot_clearsBooking() {
            AvailabilitySlot slot = buildSlot(1L, 1L, true, false);
            slot.setAppointmentId(42L);
            given(slotRepository.findById(1L)).willReturn(Optional.of(slot));
            given(slotRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            AvailabilitySlot result = scheduleService.releaseSlot(1L);

            assertThat(result.isBooked()).isFalse();
            assertThat(result.getAppointmentId()).isNull();
        }

        @Test
        @DisplayName("✅ releasing already-unbooked slot is a no-op (idempotent)")
        void release_unbookedSlot_isNoOp() {
            AvailabilitySlot slot = buildSlot(1L, 1L, false, false); // already free
            given(slotRepository.findById(1L)).willReturn(Optional.of(slot));

            AvailabilitySlot result = scheduleService.releaseSlot(1L);

            assertThat(result.isBooked()).isFalse();
            then(slotRepository).should(never()).save(any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // blockSlot / unblockSlot
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("blockSlot() / unblockSlot()")
    class BlockTests {

        @Test
        @DisplayName("✅ blocks an available slot")
        void block_availableSlot_setsBlocked() {
            AvailabilitySlot slot = buildSlot(1L, 1L, false, false);
            given(slotRepository.findById(1L)).willReturn(Optional.of(slot));
            given(slotRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            AvailabilitySlot result = scheduleService.blockSlot(1L);

            assertThat(result.isBlocked()).isTrue();
        }

        @Test
        @DisplayName("❌ cannot block a slot that already has an active booking")
        void block_bookedSlot_throwsIllegalState() {
            AvailabilitySlot slot = buildSlot(1L, 1L, true, false);
            given(slotRepository.findById(1L)).willReturn(Optional.of(slot));

            assertThatThrownBy(() -> scheduleService.blockSlot(1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cancel the appointment first");
        }

        @Test
        @DisplayName("✅ unblocks a blocked slot")
        void unblock_blockedSlot_setsUnblocked() {
            AvailabilitySlot slot = buildSlot(1L, 1L, false, true);
            given(slotRepository.findById(1L)).willReturn(Optional.of(slot));
            given(slotRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            AvailabilitySlot result = scheduleService.unblockSlot(1L);

            assertThat(result.isBlocked()).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // generateRecurringSlots
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("generateRecurringSlots()")
    class RecurringSlotTests {

        @Test
        @DisplayName("✅ WEEKDAYS pattern skips Saturday and Sunday")
        void recurring_weekdays_skipsSaturdays() {
            // Set a range covering at least one weekend
            LocalDate start = LocalDate.of(2026, 5, 4);  // Monday
            LocalDate end   = LocalDate.of(2026, 5, 10); // Sunday (5 weekdays + 2 weekend days)

            // Assume no overlaps
            given(slotRepository.existsOverlappingSlot(anyLong(), any(), any(), any()))
                    .willReturn(false);
            given(slotRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            List<AvailabilitySlot> result = scheduleService.generateRecurringSlots(
                    1L, start, end, T_1000, T_1030, 30, "WEEKDAYS");

            // 5 weekdays × 1 slot each (30-min window exactly fills one 30-min slot)
            assertThat(result).hasSize(5);
            // None should be on Saturday or Sunday
            result.forEach(s -> assertThat(s.getSlotDate().getDayOfWeek().getValue())
                    .isBetween(1, 5));
        }

        @Test
        @DisplayName("✅ DAILY pattern generates slot for every day")
        void recurring_daily_generatesEveryDay() {
            LocalDate start = LocalDate.now().plusDays(1);
            LocalDate end   = LocalDate.now().plusDays(3); // 3 days

            given(slotRepository.existsOverlappingSlot(anyLong(), any(), any(), any()))
                    .willReturn(false);
            given(slotRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            List<AvailabilitySlot> result = scheduleService.generateRecurringSlots(
                    1L, start, end, T_1000, T_1030, 30, "DAILY");

            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("❌ throws IllegalArgumentException when endDate before startDate")
        void recurring_endBeforeStart_throwsException() {
            LocalDate start = LocalDate.now().plusDays(5);
            LocalDate end   = LocalDate.now().plusDays(1); // before start

            assertThatThrownBy(() -> scheduleService.generateRecurringSlots(
                    1L, start, end, T_1000, T_1030, 30, "DAILY"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("End date");
        }

        @Test
        @DisplayName("❌ throws IllegalArgumentException for zero duration")
        void recurring_zeroDuration_throwsException() {
            assertThatThrownBy(() -> scheduleService.generateRecurringSlots(
                    1L, TOMORROW, TOMORROW.plusDays(1), T_1000, T_1030, 0, "DAILY"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Duration");
        }

        @Test
        @DisplayName("✅ overlapping slots within range are skipped, others saved")
        void recurring_someOverlap_skipsConflictingSaves() {
            LocalDate d1 = LocalDate.now().plusDays(1);
            LocalDate d2 = LocalDate.now().plusDays(2);

            given(slotRepository.existsOverlappingSlot(anyLong(), eq(d1), any(), any()))
                    .willReturn(true);  // day 1 conflicts — skip
            given(slotRepository.existsOverlappingSlot(anyLong(), eq(d2), any(), any()))
                    .willReturn(false); // day 2 is fine

            AvailabilitySlot saved = buildSlot(1L, 1L, false, false);
            saved.setSlotDate(d2);
            given(slotRepository.save(any())).willReturn(saved);

            List<AvailabilitySlot> result = scheduleService.generateRecurringSlots(
                    1L, d1, d2, T_1000, T_1030, 30, "DAILY");

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("✅ multiple slots per day generated when window > duration")
        void recurring_multipleSlotsSameDay_generatesCorrectCount() {
            // 09:00–11:00, 30 min slots → 4 slots
            LocalTime start = LocalTime.of(9, 0);
            LocalTime end   = LocalTime.of(11, 0);

            given(slotRepository.existsOverlappingSlot(anyLong(), any(), any(), any()))
                    .willReturn(false);
            given(slotRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            List<AvailabilitySlot> result = scheduleService.generateRecurringSlots(
                    1L, TOMORROW, TOMORROW, start, end, 30, "DAILY");

            assertThat(result).hasSize(4); // 9:00, 9:30, 10:00, 10:30
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // deleteSlot
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("deleteSlot()")
    class DeleteSlotTests {

        @Test
        @DisplayName("✅ deletes unbooked slot")
        void delete_unbookedSlot_callsDeleteById() {
            AvailabilitySlot slot = buildSlot(1L, 1L, false, false);
            given(slotRepository.findById(1L)).willReturn(Optional.of(slot));

            scheduleService.deleteSlot(1L);

            then(slotRepository).should().deleteById(1L);
        }

        @Test
        @DisplayName("❌ cannot delete a booked slot — must cancel appointment first")
        void delete_bookedSlot_throwsIllegalState() {
            AvailabilitySlot slot = buildSlot(1L, 1L, true, false);
            given(slotRepository.findById(1L)).willReturn(Optional.of(slot));

            assertThatThrownBy(() -> scheduleService.deleteSlot(1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cancel the appointment first");
        }
    }
}