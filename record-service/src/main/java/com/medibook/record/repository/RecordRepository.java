package com.medibook.record.repository;

import com.medibook.record.entity.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecordRepository extends JpaRepository<MedicalRecord, Long> {

    /** One record per appointment rule */
    Optional<MedicalRecord> findByAppointmentId(Long appointmentId);

    boolean existsByAppointmentId(Long appointmentId);

    /** Patient views their own records — newest first */
    List<MedicalRecord> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    /** Provider views records they created — newest first */
    List<MedicalRecord> findByProviderIdOrderByCreatedAtDesc(Long providerId);

    long countByPatientId(Long patientId);

    /** Follow-up date check — used by @Scheduled job.
     *  Finds records where followUpDate is today and reminder not yet sent. */
    @Query("SELECT r FROM MedicalRecord r " +
           "WHERE r.followUpDate = :today AND r.followUpNotified = false")
    List<MedicalRecord> findPendingFollowUpReminders(@Param("today") LocalDate today);

    /** Admin audit — all records newest first */
    List<MedicalRecord> findAllByOrderByCreatedAtDesc();

    /** Records with a follow-up date — for provider follow-up dashboard */
    List<MedicalRecord> findByProviderIdAndFollowUpDateIsNotNullOrderByFollowUpDateAsc(Long providerId);
}
