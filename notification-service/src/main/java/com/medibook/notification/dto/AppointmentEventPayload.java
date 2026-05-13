package com.medibook.notification.dto;

import com.medibook.notification.entity.Notification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

//Payload published by appointment-service to RabbitMQ
//Also used as POST body for /api/v1/notifications/appointment-event (direct HTTP fallback)

public class AppointmentEventPayload {

    /** e.g. "BOOKING_CONFIRMED", "APPOINTMENT_CANCELLED" */
	private String eventType;
    private Long   appointmentId;
    private Long   patientId;
    private Long   providerId;
    private String appointmentDate;
    private String startTime;
    private String providerName;
    private String patientName;

    public String getEventType()      { return eventType; }
    public Long   getAppointmentId()  { return appointmentId; }
    public Long   getPatientId()      { return patientId; }
    public Long   getProviderId()     { return providerId; }
    public String getAppointmentDate(){ return appointmentDate; }
    public String getStartTime()      { return startTime; }
    public String getProviderName()   { return providerName; }
    public String getPatientName()    { return patientName; }
}
