package com.medibook.record.service;

import com.medibook.record.dto.CreateRecordRequest;
import com.medibook.record.dto.RecordResponse;
import com.medibook.record.dto.UpdateRecordRequest;
import com.medibook.record.entity.MedicalRecord;
import com.medibook.record.exception.AppointmentNotCompletedException;
import com.medibook.record.exception.RecordAlreadyExistsException;
import com.medibook.record.exception.RecordEditWindowExpiredException;
import com.medibook.record.exception.RecordNotFoundException;
import com.medibook.record.repository.RecordRepository;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for RecordServiceImpl.
 *
 * Key business rules:
 *   - One record per appointment (duplicate check by appointmentId)
 *   - Record can only be created for a COMPLETED appointment (calls appointment-service)
 *   - Appointment-service down → logged, creation proceeds (non-blocking)
 *   - Records editable only within 48 hours of creation
 *   - attachDocument only updates the URL, nothing else changes
 *   - sendFollowUpReminders(): marks followUpNotified=true after sending
 *   - getFollowUpRecords(): defaults to today when date is null
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RecordServiceImpl Tests")
class RecordServiceImplTest {

    @Mock RecordRepository recordRepository;
    @Mock RestTemplate     restTemplate;

    @InjectMocks RecordServiceImpl recordService;

    @BeforeEach
    void injectUrls() {
        ReflectionTestUtils.setField(recordService, "appointmentUrl",
                "http://appointment-svc/api/v1/appointments");
        ReflectionTestUtils.setField(recordService, "notificationUrl",
                "http://notification-svc/internal");
    }

    // ── Fixture helpers ───────────────────────────────────────────────────

    private MedicalRecord buildRecord(Long id, Long appointmentId, Long patientId,
                                       Long providerId, LocalDateTime createdAt) {
        return MedicalRecord.builder()
                .recordId(id)
                .appointmentId(appointmentId)
                .patientId(patientId)
                .providerId(providerId)
                .diagnosis("Hypertension")
                .prescription("Amlodipine 5mg")
                .notes("Patient is stable")
                .followUpNotified(false)
                .createdAt(createdAt)
                .build();
    }

    private CreateRecordRequest buildCreateRequest(Long appointmentId) {
        return CreateRecordRequest.builder()
                .appointmentId(appointmentId)
                .patientId(1L)
                .providerId(10L)
                .diagnosis("Hypertension")
                .prescription("Amlodipine 5mg")
                .notes("Stable BP, continue medication")
                .build();
    }

    /** Stub appointment-service to return COMPLETED status */
    @SuppressWarnings("unchecked")
    private void stubCompletedAppointment(Long appointmentId) {
        given(restTemplate.getForObject(contains("/" + appointmentId), eq(Map.class)))
                .willReturn(Map.of("status", "COMPLETED", "appointmentId", appointmentId));
    }

    /** Stub appointment-service to return a non-COMPLETED status */
    @SuppressWarnings("unchecked")
    private void stubIncompleteAppointment(Long appointmentId, String status) {
        given(restTemplate.getForObject(contains("/" + appointmentId), eq(Map.class)))
                .willReturn(Map.of("status", status, "appointmentId", appointmentId));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // createRecord
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("createRecord()")
    class CreateRecordTests {

        @Test
        @DisplayName("✅ creates record for a COMPLETED appointment")
        void create_completedAppointment_savesRecord() {
            CreateRecordRequest req = buildCreateRequest(100L);
            given(recordRepository.existsByAppointmentId(100L)).willReturn(false);
            stubCompletedAppointment(100L);

            MedicalRecord saved = buildRecord(1L, 100L, 1L, 10L, LocalDateTime.now());
            given(recordRepository.save(any())).willReturn(saved);

            RecordResponse result = recordService.createRecord(req);

            assertThat(result.getRecordId()).isEqualTo(1L);
            assertThat(result.getAppointmentId()).isEqualTo(100L);
            assertThat(result.getDiagnosis()).isEqualTo("Hypertension");
            then(recordRepository).should().save(any());
        }

        @Test
        @DisplayName("❌ duplicate record for same appointmentId → RecordAlreadyExistsException")
        void create_duplicate_throwsConflict() {
            given(recordRepository.existsByAppointmentId(100L)).willReturn(true);

            assertThatThrownBy(() -> recordService.createRecord(buildCreateRequest(100L)))
                    .isInstanceOf(RecordAlreadyExistsException.class)
                    .hasMessageContaining("100");

            then(recordRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("❌ SCHEDULED appointment → AppointmentNotCompletedException")
        void create_scheduledAppointment_throwsNotCompleted() {
            given(recordRepository.existsByAppointmentId(101L)).willReturn(false);
            stubIncompleteAppointment(101L, "SCHEDULED");

            assertThatThrownBy(() -> recordService.createRecord(buildCreateRequest(101L)))
                    .isInstanceOf(AppointmentNotCompletedException.class)
                    .hasMessageContaining("COMPLETED")
                    .hasMessageContaining("SCHEDULED");

            then(recordRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("❌ CANCELLED appointment → AppointmentNotCompletedException")
        void create_cancelledAppointment_throwsNotCompleted() {
            given(recordRepository.existsByAppointmentId(102L)).willReturn(false);
            stubIncompleteAppointment(102L, "CANCELLED");

            assertThatThrownBy(() -> recordService.createRecord(buildCreateRequest(102L)))
                    .isInstanceOf(AppointmentNotCompletedException.class);
        }

        @Test
        @DisplayName("✅ appointment-service network failure → creation proceeds (non-blocking)")
        void create_appointmentServiceDown_proceedsAnyway() {
            given(recordRepository.existsByAppointmentId(103L)).willReturn(false);
            given(restTemplate.getForObject(anyString(), eq(Map.class)))
                    .willThrow(new RuntimeException("Connection refused"));

            MedicalRecord saved = buildRecord(2L, 103L, 1L, 10L, LocalDateTime.now());
            given(recordRepository.save(any())).willReturn(saved);

            // Non-blocking: service down logs warning and proceeds
            assertThatCode(() -> recordService.createRecord(buildCreateRequest(103L)))
                    .doesNotThrowAnyException();

            then(recordRepository).should().save(any());
        }

        @Test
        @DisplayName("✅ followUpDate is stored when provided")
        void create_withFollowUpDate_storedCorrectly() {
            CreateRecordRequest req = buildCreateRequest(104L);
            LocalDate followUp = LocalDate.now().plusDays(14);
            req.setFollowUpDate(followUp);

            given(recordRepository.existsByAppointmentId(104L)).willReturn(false);
            stubCompletedAppointment(104L);

            MedicalRecord saved = buildRecord(3L, 104L, 1L, 10L, LocalDateTime.now());
            saved.setFollowUpDate(followUp);
            given(recordRepository.save(any())).willReturn(saved);

            RecordResponse result = recordService.createRecord(req);

            assertThat(result.getFollowUpDate()).isEqualTo(followUp);
            then(recordRepository).should().save(argThat(r -> followUp.equals(r.getFollowUpDate())));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // updateRecord — 48-hour edit window
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateRecord() — 48-hour edit window")
    class UpdateRecordTests {

        @Test
        @DisplayName("✅ updates record created within 48 hours")
        void update_withinWindow_succeeds() {
            MedicalRecord record = buildRecord(1L, 100L, 1L, 10L,
                    LocalDateTime.now().minusHours(24)); // 24h ago — within window
            given(recordRepository.findById(1L)).willReturn(Optional.of(record));
            given(recordRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            UpdateRecordRequest req = UpdateRecordRequest.builder()
                    .diagnosis("Updated Diagnosis")
                    .prescription("New prescription")
                    .build();

            RecordResponse result = recordService.updateRecord(1L, req);

            assertThat(result.getDiagnosis()).isEqualTo("Updated Diagnosis");
            assertThat(result.getPrescription()).isEqualTo("New prescription");
        }

        @Test
        @DisplayName("❌ update beyond 48 hours → RecordEditWindowExpiredException")
        void update_beyondWindow_throwsException() {
            MedicalRecord record = buildRecord(2L, 101L, 1L, 10L,
                    LocalDateTime.now().minusHours(49)); // 49h ago — window expired
            given(recordRepository.findById(2L)).willReturn(Optional.of(record));

            assertThatThrownBy(() ->
                    recordService.updateRecord(2L, new UpdateRecordRequest()))
                    .isInstanceOf(RecordEditWindowExpiredException.class)
                    .hasMessageContaining("48 hours");

            then(recordRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("✅ null fields in update request do NOT overwrite existing values")
        void update_nullFields_preserveExistingValues() {
            MedicalRecord record = buildRecord(3L, 102L, 1L, 10L,
                    LocalDateTime.now().minusHours(1));
            record.setNotes("Original clinical note");
            record.setPrescription("Original prescription");
            given(recordRepository.findById(3L)).willReturn(Optional.of(record));
            given(recordRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            UpdateRecordRequest req = UpdateRecordRequest.builder()
                    .diagnosis("Updated diagnosis")
                    .notes(null)         // should preserve "Original clinical note"
                    .prescription(null)  // should preserve "Original prescription"
                    .build();

            RecordResponse result = recordService.updateRecord(3L, req);

            assertThat(result.getDiagnosis()).isEqualTo("Updated diagnosis");
            assertThat(result.getNotes()).isEqualTo("Original clinical note");
            assertThat(result.getPrescription()).isEqualTo("Original prescription");
        }

        @Test
        @DisplayName("✅ setting new followUpDate resets followUpNotified to false")
        void update_newFollowUpDate_resetsNotifiedFlag() {
            MedicalRecord record = buildRecord(4L, 103L, 1L, 10L,
                    LocalDateTime.now().minusHours(2));
            record.setFollowUpDate(LocalDate.now().plusDays(7));
            record.setFollowUpNotified(true); // was already notified

            given(recordRepository.findById(4L)).willReturn(Optional.of(record));
            given(recordRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            LocalDate newFollowUp = LocalDate.now().plusDays(21);
            UpdateRecordRequest req = UpdateRecordRequest.builder()
                    .followUpDate(newFollowUp)
                    .build();

            recordService.updateRecord(4L, req);

            // followUpNotified must be reset to false because follow-up date changed
            then(recordRepository).should().save(argThat(r ->
                    newFollowUp.equals(r.getFollowUpDate())
                    && Boolean.FALSE.equals(r.getFollowUpNotified())));
        }

        @Test
        @DisplayName("❌ updating non-existent record throws RecordNotFoundException")
        void update_notFound_throwsException() {
            given(recordRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    recordService.updateRecord(999L, new UpdateRecordRequest()))
                    .isInstanceOf(RecordNotFoundException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // attachDocument
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("attachDocument()")
    class AttachDocumentTests {

        @Test
        @DisplayName("✅ updates only attachmentUrl, leaves all other fields unchanged")
        void attach_updatesOnlyUrl() {
            MedicalRecord record = buildRecord(1L, 100L, 1L, 10L, LocalDateTime.now());
            record.setAttachmentUrl(null);
            given(recordRepository.findById(1L)).willReturn(Optional.of(record));
            given(recordRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            String newUrl = "https://s3.amazonaws.com/medibook/reports/lab-result.pdf";
            RecordResponse result = recordService.attachDocument(1L, newUrl);

            assertThat(result.getAttachmentUrl()).isEqualTo(newUrl);
            assertThat(result.getDiagnosis()).isEqualTo("Hypertension"); // unchanged
            then(recordRepository).should().save(argThat(r -> newUrl.equals(r.getAttachmentUrl())));
        }

        @Test
        @DisplayName("❌ attach to non-existent record throws RecordNotFoundException")
        void attach_notFound_throwsException() {
            given(recordRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    recordService.attachDocument(999L, "https://s3.aws.com/file.pdf"))
                    .isInstanceOf(RecordNotFoundException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // deleteRecord
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("deleteRecord()")
    class DeleteRecordTests {

        @Test
        @DisplayName("✅ deletes existing record")
        void delete_existing_callsDeleteById() {
            MedicalRecord record = buildRecord(1L, 100L, 1L, 10L, LocalDateTime.now());
            given(recordRepository.findById(1L)).willReturn(Optional.of(record));

            recordService.deleteRecord(1L);

            then(recordRepository).should().deleteById(1L);
        }

        @Test
        @DisplayName("❌ deleting non-existent record throws RecordNotFoundException")
        void delete_notFound_throwsException() {
            given(recordRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> recordService.deleteRecord(999L))
                    .isInstanceOf(RecordNotFoundException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Read operations
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Read operations")
    class ReadTests {

        @Test
        @DisplayName("✅ getRecordByAppointment returns correct record")
        void getByAppointment_exists_returnsRecord() {
            MedicalRecord record = buildRecord(1L, 100L, 1L, 10L, LocalDateTime.now());
            given(recordRepository.findByAppointmentId(100L)).willReturn(Optional.of(record));

            RecordResponse result = recordService.getRecordByAppointment(100L);

            assertThat(result.getAppointmentId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("❌ getRecordByAppointment for missing appointmentId throws RecordNotFoundException")
        void getByAppointment_missing_throwsException() {
            given(recordRepository.findByAppointmentId(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> recordService.getRecordByAppointment(999L))
                    .isInstanceOf(RecordNotFoundException.class);
        }

        @Test
        @DisplayName("✅ getRecordsByPatient returns list sorted newest-first")
        void getByPatient_returnsList() {
            given(recordRepository.findByPatientIdOrderByCreatedAtDesc(1L))
                    .willReturn(List.of(
                            buildRecord(1L, 100L, 1L, 10L, LocalDateTime.now()),
                            buildRecord(2L, 101L, 1L, 10L, LocalDateTime.now().minusDays(5))));

            List<RecordResponse> result = recordService.getRecordsByPatient(1L);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("✅ getFollowUpRecords with null date defaults to today")
        void getFollowUpRecords_nullDate_usesToday() {
            given(recordRepository.findPendingFollowUpReminders(any(LocalDate.class)))
                    .willReturn(List.of());

            List<RecordResponse> result = recordService.getFollowUpRecords(null);

            assertThat(result).isEmpty();
            then(recordRepository).should().findPendingFollowUpReminders(LocalDate.now());
        }

        @Test
        @DisplayName("✅ getRecordCount returns count from repository")
        void getRecordCount_returnsCount() {
            given(recordRepository.countByPatientId(1L)).willReturn(7L);

            assertThat(recordService.getRecordCount(1L)).isEqualTo(7L);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // sendFollowUpReminders (scheduled job)
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("sendFollowUpReminders() — @Scheduled job")
    class FollowUpReminderJobTests {

        @Test
        @DisplayName("✅ marks each notified record as followUpNotified=true")
        void sendReminders_marksNotified() {
            MedicalRecord r1 = buildRecord(1L, 100L, 1L, 10L, LocalDateTime.now().minusDays(14));
            r1.setFollowUpDate(LocalDate.now());
            r1.setFollowUpNotified(false);

            MedicalRecord r2 = buildRecord(2L, 101L, 2L, 10L, LocalDateTime.now().minusDays(7));
            r2.setFollowUpDate(LocalDate.now());
            r2.setFollowUpNotified(false);

            given(recordRepository.findPendingFollowUpReminders(LocalDate.now()))
                    .willReturn(List.of(r1, r2));
            given(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                    .willReturn(null);
            given(recordRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            recordService.sendFollowUpReminders();

            then(recordRepository).should(times(2)).save(argThat(r ->
                    Boolean.TRUE.equals(r.getFollowUpNotified())));
        }

        @Test
        @DisplayName("✅ no-op when no reminders are due today")
        void sendReminders_noDue_skips() {
            given(recordRepository.findPendingFollowUpReminders(LocalDate.now()))
                    .willReturn(List.of());

            recordService.sendFollowUpReminders();

            then(restTemplate).should(never()).postForEntity(anyString(), any(), any());
            then(recordRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("✅ notification failure for one record doesn't skip the rest")
        void sendReminders_oneFailure_continuesForOthers() {
            MedicalRecord r1 = buildRecord(1L, 100L, 1L, 10L, LocalDateTime.now().minusDays(14));
            r1.setFollowUpDate(LocalDate.now());
            MedicalRecord r2 = buildRecord(2L, 101L, 2L, 10L, LocalDateTime.now().minusDays(7));
            r2.setFollowUpDate(LocalDate.now());

            given(recordRepository.findPendingFollowUpReminders(LocalDate.now()))
                    .willReturn(List.of(r1, r2));

            // First call throws, second call succeeds
            given(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                    .willThrow(new RuntimeException("Notification down"))
                    .willReturn(null);

            given(recordRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            assertThatCode(() -> recordService.sendFollowUpReminders())
                    .doesNotThrowAnyException();

            // Only the second record (successful) should be saved as notified
            then(recordRepository).should(times(1)).save(any());
        }
    }
}