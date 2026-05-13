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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RecordServiceImpl implements RecordService {

    private final RecordRepository recordRepository;
    private final RestTemplate restTemplate;

    @Value("${services.appointment-url}")
    private String appointmentUrl;

    @Value("${services.notification-url}")
    private String notificationUrl;

    @Override
    @CacheEvict(cacheNames = {
            "records.byId", "records.byAppointment", "records.byPatient",
            "records.byProvider", "records.followUps", "records.providerFollowUps",
            "records.count", "records.all"
    }, allEntries = true)
    public RecordResponse createRecord(CreateRecordRequest req) {
        log.info("Creating medical record: appointmentId={} providerId={}",
                req.getAppointmentId(), req.getProviderId());

        if (recordRepository.existsByAppointmentId(req.getAppointmentId())) {
            throw new RecordAlreadyExistsException(
                    "A medical record already exists for appointmentId: " + req.getAppointmentId());
        }

        verifyAppointmentCompleted(req.getAppointmentId());

        MedicalRecord record = MedicalRecord.builder()
                .appointmentId(req.getAppointmentId())
                .patientId(req.getPatientId())
                .providerId(req.getProviderId())
                .diagnosis(req.getDiagnosis())
                .prescription(req.getPrescription())
                .notes(req.getNotes())
                .labResults(req.getLabResults())
                .attachmentUrl(req.getAttachmentUrl())
                .followUpDate(req.getFollowUpDate())
                .build();

        MedicalRecord saved = recordRepository.save(record);
        log.info("Medical record created: recordId={} followUpDate={}", saved.getRecordId(), saved.getFollowUpDate());
        return RecordResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "records.byId", key = "#recordId")
    public RecordResponse getRecordById(Long recordId) {
        return RecordResponse.from(findOrThrow(recordId));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "records.byAppointment", key = "#appointmentId")
    public RecordResponse getRecordByAppointment(Long appointmentId) {
        return RecordResponse.from(
                recordRepository.findByAppointmentId(appointmentId)
                        .orElseThrow(() -> new RecordNotFoundException(
                                "No medical record found for appointmentId: " + appointmentId)));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "records.byPatient", key = "#patientId")
    public List<RecordResponse> getRecordsByPatient(Long patientId) {
        return recordRepository.findByPatientIdOrderByCreatedAtDesc(patientId)
                .stream().map(RecordResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "records.byProvider", key = "#providerId")
    public List<RecordResponse> getRecordsByProvider(Long providerId) {
        return recordRepository.findByProviderIdOrderByCreatedAtDesc(providerId)
                .stream().map(RecordResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "records.followUps", key = "#date == null ? 'today' : #date.toString()")
    public List<RecordResponse> getFollowUpRecords(LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        return recordRepository.findPendingFollowUpReminders(target)
                .stream().map(RecordResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "records.providerFollowUps", key = "#providerId")
    public List<RecordResponse> getProviderFollowUps(Long providerId) {
        return recordRepository
                .findByProviderIdAndFollowUpDateIsNotNullOrderByFollowUpDateAsc(providerId)
                .stream().map(RecordResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "records.count", key = "#patientId")
    public long getRecordCount(Long patientId) {
        return recordRepository.countByPatientId(patientId);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "records.all", key = "'all'")
    public List<RecordResponse> getAllRecords() {
        return recordRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(RecordResponse::from).toList();
    }

    @Override
    @CacheEvict(cacheNames = {
            "records.byId", "records.byAppointment", "records.byPatient",
            "records.byProvider", "records.followUps", "records.providerFollowUps",
            "records.count", "records.all"
    }, allEntries = true)
    public RecordResponse updateRecord(Long recordId, UpdateRecordRequest req) {
        MedicalRecord record = findOrThrow(recordId);

        if (record.getCreatedAt() != null
                && record.getCreatedAt().isBefore(LocalDateTime.now().minusHours(48))) {
            throw new RecordEditWindowExpiredException(
                    "Medical records can only be edited within 48 hours of creation. "
                            + "This record was created at: " + record.getCreatedAt());
        }

        if (req.getDiagnosis() != null) record.setDiagnosis(req.getDiagnosis());
        if (req.getPrescription() != null) record.setPrescription(req.getPrescription());
        if (req.getNotes() != null) record.setNotes(req.getNotes());
        if (req.getLabResults() != null) record.setLabResults(req.getLabResults());
        if (req.getAttachmentUrl() != null) record.setAttachmentUrl(req.getAttachmentUrl());
        if (req.getFollowUpDate() != null) {
            record.setFollowUpDate(req.getFollowUpDate());
            record.setFollowUpNotified(false);
        }

        log.info("Medical record updated: recordId={}", recordId);
        return RecordResponse.from(recordRepository.save(record));
    }

    @Override
    @CacheEvict(cacheNames = {
            "records.byId", "records.byAppointment", "records.byPatient",
            "records.byProvider", "records.followUps", "records.providerFollowUps",
            "records.count", "records.all"
    }, allEntries = true)
    public RecordResponse attachDocument(Long recordId, String attachmentUrl) {
        MedicalRecord record = findOrThrow(recordId);
        record.setAttachmentUrl(attachmentUrl);
        log.info("Document attached to recordId={}: url={}", recordId, attachmentUrl);
        return RecordResponse.from(recordRepository.save(record));
    }

    @Override
    @CacheEvict(cacheNames = {
            "records.byId", "records.byAppointment", "records.byPatient",
            "records.byProvider", "records.followUps", "records.providerFollowUps",
            "records.count", "records.all"
    }, allEntries = true)
    public void deleteRecord(Long recordId) {
        findOrThrow(recordId);
        recordRepository.deleteById(recordId);
        log.info("Medical record deleted: recordId={}", recordId);
    }

    @Scheduled(cron = "0 0 8 * * ?")
    public void sendFollowUpReminders() {
        LocalDate today = LocalDate.now();
        List<MedicalRecord> due = recordRepository.findPendingFollowUpReminders(today);

        if (due.isEmpty()) {
            log.debug("No follow-up reminders due today: {}", today);
            return;
        }

        log.info("Sending {} follow-up reminders for date: {}", due.size(), today);

        for (MedicalRecord record : due) {
            try {
                Map<String, Object> payload = Map.of(
                        "eventType", "FOLLOWUP_REMINDER",
                        "recipientId", record.getPatientId(),
                        "type", "FOLLOWUP_REMINDER",
                        "title", "Follow-Up Appointment Reminder",
                        "message", String.format(
                                "Today is your scheduled follow-up date set by your doctor. Please book your follow-up appointment. Medical Record ID: %d",
                                record.getRecordId()),
                        "channel", "ALL",
                        "relatedId", record.getRecordId(),
                        "relatedType", "MEDICAL_RECORD"
                );
                restTemplate.postForEntity(notificationUrl, payload, String.class);
                record.setFollowUpNotified(true);
                recordRepository.save(record);
                log.info("Follow-up reminder sent: recordId={} patientId={}", record.getRecordId(), record.getPatientId());
            } catch (Exception ex) {
                log.error("Failed to send follow-up reminder for recordId={}: {}", record.getRecordId(), ex.getMessage());
            }
        }
    }

    private MedicalRecord findOrThrow(Long recordId) {
        return recordRepository.findById(recordId)
                .orElseThrow(() -> new RecordNotFoundException(recordId));
    }

    private void verifyAppointmentCompleted(Long appointmentId) {
        try {
            Map<?, ?> appt = restTemplate.getForObject(appointmentUrl + "/" + appointmentId, Map.class);
            if (appt != null) {
                String status = (String) appt.get("status");
                if (!"COMPLETED".equals(status)) {
                    throw new AppointmentNotCompletedException(
                            "Medical records can only be created for COMPLETED appointments. "
                                    + "Current appointment status: " + status
                                    + ". Please mark the appointment as complete first.");
                }
            }
        } catch (AppointmentNotCompletedException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Could not verify appointment status (non-blocking): {}", ex.getMessage());
        }
    }
}
