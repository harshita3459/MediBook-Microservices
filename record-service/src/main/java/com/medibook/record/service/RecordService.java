package com.medibook.record.service;

import com.medibook.record.dto.*;
import java.time.LocalDate;
import java.util.List;

public interface RecordService {
    RecordResponse createRecord(CreateRecordRequest request);
    RecordResponse getRecordById(Long recordId);
    RecordResponse getRecordByAppointment(Long appointmentId);
    List<RecordResponse> getRecordsByPatient(Long patientId);
    List<RecordResponse> getRecordsByProvider(Long providerId);
    RecordResponse updateRecord(Long recordId, UpdateRecordRequest request);
    void deleteRecord(Long recordId);
    RecordResponse attachDocument(Long recordId, String attachmentUrl);
    List<RecordResponse> getFollowUpRecords(LocalDate date);
    List<RecordResponse> getProviderFollowUps(Long providerId);
    long getRecordCount(Long patientId);
    List<RecordResponse> getAllRecords();
}
