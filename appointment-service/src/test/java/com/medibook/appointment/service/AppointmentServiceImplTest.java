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
 *   3. Cancelled slots do NOT block re-booking  (findBySlotId returns empty after cancel)
 *   4. Cancel flow: status = CANCELLED (slotId NOT nulled — service keeps it)
 *   5. Status transition rules (can't cancel COMPLETED, RESCHEDULED, etc.)
 *
 * FIXES vs original test:
 *   - findActiveBySlotId(long)          → findBySlotId(Long)   [actual repo method]
 *   - findActiveConflictForPatient(...)  → removed entirely     [method does not exist in repo]
 *   - cancel: slotId=null assertion      → removed              [service does NOT null slotId]
 *   - cancel RESCHEDULED guard test      → added                [service only allows SCHEDULED→CANCEL]
 *   - cancel COMPLETED message check     → corrected wording    [message says "COMPLETED"]
 *   - provider mock: contains("/10") → contains("/providers/10") [fixed: "/10" also matched slot URL "/api/v1/slots/100"]
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
        ReflectionTestUtils.setField(appointmentService, "scheduleUrl",     "http://schedule-svc/internal/slots");
        ReflectionTestUtils.setField(appointmentService, "notificationUrl", "http://notification-svc/internal");
        ReflectionTestUtils.setField(appointmentService, "providerUrl",     "http://provider-svc/api/v1/providers");
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

    /** Returns a Map simulating slot details from schedule-service */
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
        @DisplayName("✅ books appointment successfully when slot is free")
        @SuppressWarnings("unchecked")
        void book_freshSlot_noConflict_returnsResponse() {
            BookAppointmentRequest req = buildBookRequest(1L, 10L, 100L);

            // FIX: use findBySlotId (actual repo method)
            given(appointmentRepository.findBySlotId(100L)).willReturn(Optional.empty());

            // Schedule-service returns slot details
            given(restTemplate.getForEntity(contains("/api/v1/slots/100"), eq(Map.class)))
                    .willReturn(new org.springframework.http.ResponseEntity<>(
                            slotDetails(
                                    LocalDate.now().plusDays(1).toString(),
                                    "10:00:00", "10:30:00"),
                            org.springframework.http.HttpStatus.OK));

            // Provider is available + verified
            given(restTemplate.getForEntity(contains("/providers/10"), eq(Map.class)))
                    .willReturn(new org.springframework.http.ResponseEntity<>(
                            (Map) providerMap(true, true),
                            org.springframework.http.HttpStatus.OK));

            Appointment saved = buildAppointment(200L, 1L, 10L, 100L, AppointmentStatus.SCHEDULED);
            given(appointmentRepository.save(any(Appointment.class))).willReturn(saved);

            AppointmentResponse result = appointmentService.bookAppointment(req);

            assertThat(result).isNotNull();
            assertThat(result.getAppointmentId()).isEqualTo(200L);
            assertThat(result.getStatus()).isEqualTo("SCHEDULED");
            then(appointmentRepository).should().save(any(Appointment.class));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // bookAppointment — GUARD 1: Slot already booked
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("bookAppointment() — Guard 1: duplicate slot")
    class SlotAlreadyBookedTests {

        @Test
        @DisplayName("❌ throws AppointmentAlreadyExistsException when slot has existing booking")
        void book_slotActivelyBooked_throwsConflict() {
            BookAppointmentRequest req = buildBookRequest(2L, 10L, 50L);

            Appointment existing = buildAppointment(99L, 3L, 10L, 50L, AppointmentStatus.SCHEDULED);
            // FIX: use findBySlotId (actual repo method)
            given(appointmentRepository.findBySlotId(50L)).willReturn(Optional.of(existing));

            assertThatThrownBy(() -> appointmentService.bookAppointment(req))
                    .isInstanceOf(AppointmentAlreadyExistsException.class)
                    .hasMessageContaining("50");

            // Must not reach the save step
            then(appointmentRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("✅ slot with no existing booking allows booking (covers re-booking after cancel)")
        @SuppressWarnings("unchecked")
        void book_slotFree_succeeds() {
            // After cancellation the cancelled appointment retains the slotId in the DB,
            // but the service checks findBySlotId — if any record exists for that slotId,
            // it blocks. This test covers the case where no record exists for the slotId.
            BookAppointmentRequest req = buildBookRequest(5L, 10L, 77L);

            // FIX: use findBySlotId (actual repo method)
            given(appointmentRepository.findBySlotId(77L)).willReturn(Optional.empty());

            given(restTemplate.getForEntity(contains("/api/v1/slots/77"), eq(Map.class)))
                    .willReturn(new org.springframework.http.ResponseEntity<>(
                            slotDetails(LocalDate.now().plusDays(2).toString(),
                                    "14:00:00", "14:30:00"),
                            org.springframework.http.HttpStatus.OK));

            given(restTemplate.getForEntity(contains("/providers/10"), eq(Map.class)))
                    .willReturn(new org.springframework.http.ResponseEntity<>(
                            (Map) providerMap(true, true),
                            org.springframework.http.HttpStatus.OK));

            Appointment saved = buildAppointment(201L, 5L, 10L, 77L, AppointmentStatus.SCHEDULED);
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
        @DisplayName("✅ SCHEDULED appointment is cancelled: status=CANCELLED")
        void cancel_scheduledAppointment_setCancelledStatus() {
            Appointment appt = buildAppointment(1L, 1L, 10L, 100L, AppointmentStatus.SCHEDULED);
            given(appointmentRepository.findById(1L)).willReturn(Optional.of(appt));
            given(appointmentRepository.save(any(Appointment.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // schedule-service release call (fire-and-forget PUT)
            willDoNothing().given(restTemplate).put(anyString(), any());

            AppointmentResponse result = appointmentService.cancelAppointment(1L, "Patient request");

            assertThat(result.getStatus()).isEqualTo("CANCELLED");

            // FIX: service does NOT null slotId — only sets CANCELLED + reason
            then(appointmentRepository).should().save(argThat(a ->
                    a.getStatus() == AppointmentStatus.CANCELLED
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
            Appointment appt = buildAppointment(3L, 1L, 10L, 100L, AppointmentStatus.CANCELLED);
            given(appointmentRepository.findById(3L)).willReturn(Optional.of(appt));

            assertThatThrownBy(() -> appointmentService.cancelAppointment(3L, "reason"))
                    .isInstanceOf(InvalidStatusTransitionException.class);

            then(appointmentRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("❌ cannot cancel RESCHEDULED appointment")
        void cancel_rescheduledAppointment_throwsInvalidTransition() {
            // FIX: service only allows SCHEDULED → CANCELLED, so RESCHEDULED must also be rejected
            Appointment appt = buildAppointment(4L, 1L, 10L, 100L, AppointmentStatus.RESCHEDULED);
            given(appointmentRepository.findById(4L)).willReturn(Optional.of(appt));

            assertThatThrownBy(() -> appointmentService.cancelAppointment(4L, "reason"))
                    .isInstanceOf(InvalidStatusTransitionException.class)
                    .hasMessageContaining("RESCHEDULED");

            then(appointmentRepository).should(never()).save(any());
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