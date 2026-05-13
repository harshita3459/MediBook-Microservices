package com.medibook.appointment.service;

import com.medibook.appointment.dto.AppointmentResponse;
import com.medibook.appointment.dto.BookAppointmentRequest;

import java.time.LocalDate;
import java.util.List;

public interface AppointmentService {

    AppointmentResponse bookAppointment(BookAppointmentRequest request);

    AppointmentResponse getById(Long appointmentId);

    List<AppointmentResponse> getByPatient(Long patientId);

    List<AppointmentResponse> getByProvider(Long providerId);

    List<AppointmentResponse> getByProviderAndDate(Long providerId, LocalDate date);

    List<AppointmentResponse> getUpcomingByPatient(Long patientId);

    AppointmentResponse cancelAppointment(Long appointmentId, String reason);

    AppointmentResponse rescheduleAppointment(Long appointmentId, Long newSlotId);

    AppointmentResponse completeAppointment(Long appointmentId);

    List<AppointmentResponse> getAllAppointments();

    long countByProvider(Long providerId);
}