package com.medibook.appointment.service;

import com.medibook.appointment.dto.AppointmentResponse;
import com.medibook.appointment.dto.BookAppointmentRequest;
import com.medibook.appointment.entity.Appointment;
import com.medibook.appointment.entity.Appointment.AppointmentStatus;
import com.medibook.appointment.exception.AppointmentAlreadyExistsException;
import com.medibook.appointment.exception.AppointmentNotFoundException;
import com.medibook.appointment.exception.InvalidStatusTransitionException;
import com.medibook.appointment.repository.AppointmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for AppointmentServiceImpl — the most critical service in the system.
 *
 * Key scenarios tested:
 *   1. Successful booking (happy path)
 *   2. Slot already booked by another patient → 409
 *   3. Same patient, same time, different doctor → 409 (our new cross-provider fix)
 *   4. Cancelled slots do NOT block re-booking
 *   5. Cancel flow: slotId nullified + status = CANCELLED
 *   6. Status transition rules (can't cancel COMPLETED, etc.)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AppointmentServiceImpl Tests")
class AppointmentServiceImplTest {

    @Mock AppointmentRepository appointmentRepository;
    @Mock RestTemplate          restTemplate;

    @InjectMocks
    AppointmentServiceImpl appointmentService;

    @BeforeEach
    void injectUrls() {
        ReflectionTestUtils.setField(appointmentService, "scheduleUrl",      "http://schedule-svc/internal/slots");
        ReflectionTestUtils.setField(appointmentService, "notificationUrl",  "http://notification-svc/internal");
        ReflectionTestUtils.setField(appointmentService, "providerUrl",      "http://provider-svc/api/v1/providers");
    }

    // ── Fixtures ──────────────────────────────────────────────────────────

    private BookAppointmentRequest buildBookRequest(Long patientId, Long providerId, Long slotId) {
        return BookAppointmentRequest.builder()
                .patientId(patientId)
                .providerId(providerId)
                .slotId(slotId)
                .serviceType("CONSULTATION")
                .modeOfConsultation("IN_PERSON")
                .patientNotes("Chest pain")
                .build();
    }

    /** Returns a Map simulating the slot details from schedule-service */
    private Map<String, Object> slotDetails(String date, String start, String end) {
        return Map.of(
                "slotDate",  date,
                "startTime", start,
                "endTime",   end,
                "isBooked",  false,
                "isBlocked", false
        );
    }

    /** Returns a Map simulating a verified + available provider from provider-service */
    private Map<?, ?> providerMap(boolean available, boolean verified) {
        return Map.of("isAvailable", available, "isVerified", verified);
    }

    private Appointment buildAppointment(Long id, Long patientId, Long providerId,
                                          Long slotId, AppointmentStatus status) {
        return Appointment.builder()
                .appointmentId(id)
                .patientId(patientId)
                .providerId(providerId)
                .slotId(slotId)
                .appointmentDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .status(status)
                .serviceType("CONSULTATION")
                .modeOfConsultation("IN_PERSON")
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // bookAppointment — HAPPY PATH
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("bookAppointment() — happy path")
    class BookAppointmentHappyPathTests {

        @Test
        @DisplayName("✅ books appointment successfully when slot is free and no time conflict")
        @SuppressWarnings("unchecked")
        void book_freshSlot_noConflict_returnsResponse() {
            // Arrange
            BookAppointmentRequest req = buildBookRequest(1L, 10L, 100L);

            // Slot not yet booked
            given(appointmentRepository.findActiveBySlotId(100L)).willReturn(Optional.empty());

            // Schedule-service returns slot details
            given(restTemplate.getForEntity(anyString(), eq(Map.class)))
                    .willReturn(new org.springframework.http.ResponseEntity<>(
                            slotDetails(
                                    LocalDate.now().plusDays(1).toString(),
                                    "10:00:00", "10:30:00"),
                            org.springframework.http.HttpStatus.OK));

            // No patient time conflict
            given(appointmentRepository.findActiveConflictForPatient(
                    eq(1L), any(LocalDate.class), any(LocalTime.class)))
                    .willReturn(Optional.empty());

            // Provider is available + verified
            given(restTemplate.getForEntity(contains("/10"), eq(Map.class)))
                    .willReturn(new org.springframework.http.ResponseEntity<>(
                            (Map) providerMap(true, true),
                            org.springframework.http.HttpStatus.OK));

            // Repository save
            Appointment saved = buildAppointment(200L, 1L, 10L, 100L, AppointmentStatus.SCHEDULED);
            given(appointmentRepository.save(any(Appointment.class))).willReturn(saved);

            // Act
            AppointmentResponse result = appointmentService.bookAppointment(req);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getAppointmentId()).isEqualTo(200L);
            assertThat(result.getStatus()).isEqualTo("SCHEDULED");
            then(appointmentRepository).should().save(any(Appointment.class));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // bookAppointment — GUARD 1: Slot already actively booked
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("bookAppointment() — Guard 1: duplicate slot")
    class SlotAlreadyBookedTests {

        @Test
        @DisplayName("❌ throws AppointmentAlreadyExistsException when slot has active booking")
        void book_slotActivelyBooked_throwsConflict() {
            BookAppointmentRequest req = buildBookRequest(2L, 10L, 50L);

            Appointment existing = buildAppointment(99L, 3L, 10L, 50L, AppointmentStatus.SCHEDULED);
            given(appointmentRepository.findActiveBySlotId(50L)).willReturn(Optional.of(existing));

            assertThatThrownBy(() -> appointmentService.bookAppointment(req))
                    .isInstanceOf(AppointmentAlreadyExistsException.class)
                    .hasMessageContaining("50");

            // Must not reach the save step
            then(appointmentRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("✅ CANCELLED booking for same slot does NOT block re-booking (CRITICAL FIX)")
        @SuppressWarnings("unchecked")
        void book_cancelledSlot_doesNotBlock() {
            // After cancellation, findActiveBySlotId returns EMPTY because
            // cancelled appointments have slotId = null now, and the query
            // filters only SCHEDULED/RESCHEDULED
            BookAppointmentRequest req = buildBookRequest(5L, 10L, 77L);
            given(appointmentRepository.findActiveBySlotId(77L)).willReturn(Optional.empty()); // ← the fix

            given(restTemplate.getForEntity(anyString(), eq(Map.class)))
                    .willReturn(new org.springframework.http.ResponseEntity<>(
                            slotDetails(LocalDate.now().plusDays(2).toString(),
                                    "14:00:00", "14:30:00"),
                            org.springframework.http.HttpStatus.OK));

            given(appointmentRepository.findActiveConflictForPatient(
                    anyLong(), any(LocalDate.class), any(LocalTime.class)))
                    .willReturn(Optional.empty());

            given(restTemplate.getForEntity(contains("/10"), eq(Map.class)))
                    .willReturn(new org.springframework.http.ResponseEntity<>(
                            (Map) providerMap(true, true),
                            org.springframework.http.HttpStatus.OK));

            Appointment saved = buildAppointment(201L, 5L, 10L, 77L, AppointmentStatus.SCHEDULED);
            given(appointmentRepository.save(any())).willReturn(saved);

            // Should succeed — cancelled slots are free
            assertThatCode(() -> appointmentService.bookAppointment(req))
                    .doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // bookAppointment — GUARD 2: Same patient, same time, different provider
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("bookAppointment() — Guard 2: patient time conflict")
    class PatientTimeConflictTests {

        @Test
        @DisplayName("❌ same patient cannot book two doctors at same date+time")
        @SuppressWarnings("unchecked")
        void book_patientAlreadyBookedSameTime_throwsConflict() {
            BookAppointmentRequest req = buildBookRequest(1L, 20L, 88L); // different provider

            given(appointmentRepository.findActiveBySlotId(88L)).willReturn(Optional.empty());

            // Slot details — same date+time as existing appointment
            given(restTemplate.getForEntity(anyString(), eq(Map.class)))
                    .willReturn(new org.springframework.http.ResponseEntity<>(
                            slotDetails(LocalDate.now().plusDays(1).toString(),
                                    "10:00:00", "10:30:00"),
                            org.springframework.http.HttpStatus.OK));

            // Patient already has appointment at 10:00 with another provider
            Appointment conflict = buildAppointment(111L, 1L, 10L, 50L, AppointmentStatus.SCHEDULED);
            given(appointmentRepository.findActiveConflictForPatient(
                    eq(1L), any(LocalDate.class), any(LocalTime.class)))
                    .willReturn(Optional.of(conflict));

            assertThatThrownBy(() -> appointmentService.bookAppointment(req))
                    .isInstanceOf(AppointmentAlreadyExistsException.class)
                    .hasMessageContaining("10:00")
                    .hasMessageContaining("111"); // appointment ID in message
        }

        @Test
        @DisplayName("✅ CANCELLED appointment at same time does NOT block new booking")
        @SuppressWarnings("unchecked")
        void book_cancelledConflict_doesNotBlock() {
            BookAppointmentRequest req = buildBookRequest(1L, 20L, 88L);
            given(appointmentRepository.findActiveBySlotId(88L)).willReturn(Optional.empty());

            given(restTemplate.getForEntity(anyString(), eq(Map.class)))
                    .willReturn(new org.springframework.http.ResponseEntity<>(
                            slotDetails(LocalDate.now().plusDays(1).toString(),
                                    "10:00:00", "10:30:00"),
                            org.springframework.http.HttpStatus.OK));

            // No ACTIVE conflict (cancelled appointments not returned by query)
            given(appointmentRepository.findActiveConflictForPatient(
                    anyLong(), any(LocalDate.class), any(LocalTime.class)))
                    .willReturn(Optional.empty());

            given(restTemplate.getForEntity(contains("/20"), eq(Map.class)))
                    .willReturn(new org.springframework.http.ResponseEntity<>(
                            (Map) providerMap(true, true),
                            org.springframework.http.HttpStatus.OK));

            Appointment saved = buildAppointment(202L, 1L, 20L, 88L, AppointmentStatus.SCHEDULED);
            given(appointmentRepository.save(any())).willReturn(saved);

            assertThatCode(() -> appointmentService.bookAppointment(req))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("✅ same patient can book different time slots on same day")
        @SuppressWarnings("unchecked")
        void book_samePatientDifferentTime_succeeds() {
            BookAppointmentRequest req = buildBookRequest(1L, 10L, 99L);
            given(appointmentRepository.findActiveBySlotId(99L)).willReturn(Optional.empty());

            given(restTemplate.getForEntity(anyString(), eq(Map.class)))
                    .willReturn(new org.springframework.http.ResponseEntity<>(
                            slotDetails(LocalDate.now().plusDays(1).toString(),
                                    "14:00:00", "14:30:00"), // DIFFERENT time
                            org.springframework.http.HttpStatus.OK));

            // No conflict because time is different
            given(appointmentRepository.findActiveConflictForPatient(
                    anyLong(), any(LocalDate.class), any(LocalTime.class)))
                    .willReturn(Optional.empty());

            given(restTemplate.getForEntity(contains("/10"), eq(Map.class)))
                    .willReturn(new org.springframework.http.ResponseEntity<>(
                            (Map) providerMap(true, true),
                            org.springframework.http.HttpStatus.OK));

            Appointment saved = buildAppointment(203L, 1L, 10L, 99L, AppointmentStatus.SCHEDULED);
            given(appointmentRepository.save(any())).willReturn(saved);

            assertThatCode(() -> appointmentService.bookAppointment(req))
                    .doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // cancelAppointment
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("cancelAppointment()")
    class CancelAppointmentTests {

        @Test
        @DisplayName("✅ SCHEDULED appointment is cancelled: status=CANCELLED, slotId=null")
        void cancel_scheduledAppointment_setsNullSlotAndCancelledStatus() {
            Appointment appt = buildAppointment(1L, 1L, 10L, 100L, AppointmentStatus.SCHEDULED);
            given(appointmentRepository.findById(1L)).willReturn(Optional.of(appt));
            given(appointmentRepository.save(any(Appointment.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // Mock schedule-service release call (fire-and-forget)
            willDoNothing().given(restTemplate).put(anyString(), any());

            AppointmentResponse result = appointmentService.cancelAppointment(1L, "Patient request");

            assertThat(result.getStatus()).isEqualTo("CANCELLED");
            // slotId is cleared after cancellation so the slot can be re-booked
            then(appointmentRepository).should().save(argThat(a ->
                    a.getStatus() == AppointmentStatus.CANCELLED
                    && a.getSlotId() == null              // ← CRITICAL: slot released
                    && "Patient request".equals(a.getCancellationReason())
            ));
        }

        @Test
        @DisplayName("❌ cannot cancel COMPLETED appointment")
        void cancel_completedAppointment_throwsInvalidTransition() {
            Appointment appt = buildAppointment(2L, 1L, 10L, 100L, AppointmentStatus.COMPLETED);
            given(appointmentRepository.findById(2L)).willReturn(Optional.of(appt));

            assertThatThrownBy(() -> appointmentService.cancelAppointment(2L, "reason"))
                    .isInstanceOf(InvalidStatusTransitionException.class)
                    .hasMessageContaining("COMPLETED");

            then(appointmentRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("❌ cannot cancel CANCELLED appointment")
        void cancel_alreadyCancelledAppointment_throwsInvalidTransition() {
            Appointment appt = buildAppointment(3L, 1L, 10L, null, AppointmentStatus.CANCELLED);
            given(appointmentRepository.findById(3L)).willReturn(Optional.of(appt));

            assertThatThrownBy(() -> appointmentService.cancelAppointment(3L, "reason"))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        @DisplayName("❌ cancelling non-existent appointment throws AppointmentNotFoundException")
        void cancel_notFound_throwsNotFoundException() {
            given(appointmentRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> appointmentService.cancelAppointment(999L, "reason"))
                    .isInstanceOf(AppointmentNotFoundException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // completeAppointment
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("completeAppointment()")
    class CompleteAppointmentTests {

        @Test
        @DisplayName("✅ SCHEDULED → COMPLETED transition succeeds")
        void complete_scheduled_setsCompleted() {
            Appointment appt = buildAppointment(1L, 1L, 10L, 100L, AppointmentStatus.SCHEDULED);
            given(appointmentRepository.findById(1L)).willReturn(Optional.of(appt));
            given(appointmentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            AppointmentResponse result = appointmentService.completeAppointment(1L);

            assertThat(result.getStatus()).isEqualTo("COMPLETED");
        }

        @Test
        @DisplayName("✅ RESCHEDULED → COMPLETED transition succeeds")
        void complete_rescheduled_setsCompleted() {
            Appointment appt = buildAppointment(2L, 1L, 10L, 100L, AppointmentStatus.RESCHEDULED);
            given(appointmentRepository.findById(2L)).willReturn(Optional.of(appt));
            given(appointmentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            AppointmentResponse result = appointmentService.completeAppointment(2L);

            assertThat(result.getStatus()).isEqualTo("COMPLETED");
        }

        @Test
        @DisplayName("❌ cannot complete CANCELLED appointment")
        void complete_cancelled_throwsInvalidTransition() {
            Appointment appt = buildAppointment(3L, 1L, 10L, null, AppointmentStatus.CANCELLED);
            given(appointmentRepository.findById(3L)).willReturn(Optional.of(appt));

            assertThatThrownBy(() -> appointmentService.completeAppointment(3L))
                    .isInstanceOf(InvalidStatusTransitionException.class)
                    .hasMessageContaining("CANCELLED");
        }

        @Test
        @DisplayName("❌ cannot complete already COMPLETED appointment")
        void complete_alreadyCompleted_throwsInvalidTransition() {
            Appointment appt = buildAppointment(4L, 1L, 10L, 100L, AppointmentStatus.COMPLETED);
            given(appointmentRepository.findById(4L)).willReturn(Optional.of(appt));

            assertThatThrownBy(() -> appointmentService.completeAppointment(4L))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // getById / getByPatient / getByProvider
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Read operations")
    class ReadTests {

        @Test
        @DisplayName("✅ getById returns appointment response for existing ID")
        void getById_existing_returnsResponse() {
            Appointment appt = buildAppointment(5L, 1L, 10L, 100L, AppointmentStatus.SCHEDULED);
            given(appointmentRepository.findById(5L)).willReturn(Optional.of(appt));

            AppointmentResponse result = appointmentService.getById(5L);

            assertThat(result.getAppointmentId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("❌ getById throws AppointmentNotFoundException for missing ID")
        void getById_notFound_throwsException() {
            given(appointmentRepository.findById(404L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> appointmentService.getById(404L))
                    .isInstanceOf(AppointmentNotFoundException.class);
        }

        @Test
        @DisplayName("✅ getByPatient returns ordered list")
        void getByPatient_returnsListFromRepo() {
            List<Appointment> appts = List.of(
                    buildAppointment(1L, 1L, 10L, 100L, AppointmentStatus.SCHEDULED),
                    buildAppointment(2L, 1L, 10L, 101L, AppointmentStatus.COMPLETED)
            );
            given(appointmentRepository.findByPatientIdOrderByAppointmentDateDesc(1L))
                    .willReturn(appts);

            List<AppointmentResponse> result = appointmentService.getByPatient(1L);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("✅ countByProvider returns correct count")
        void countByProvider_returnsCount() {
            given(appointmentRepository.countByProviderId(7L)).willReturn(15L);

            assertThat(appointmentService.countByProvider(7L)).isEqualTo(15L);
        }
    }
}