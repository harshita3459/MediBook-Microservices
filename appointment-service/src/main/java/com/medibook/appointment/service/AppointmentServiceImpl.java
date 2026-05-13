package com.medibook.appointment.service;

import com.medibook.appointment.dto.AppointmentResponse;
import com.medibook.appointment.dto.BookAppointmentRequest;
import com.medibook.appointment.entity.Appointment;
import com.medibook.appointment.entity.Appointment.AppointmentStatus;
import com.medibook.appointment.exception.AppointmentAlreadyExistsException;
import com.medibook.appointment.exception.AppointmentNotFoundException;
import com.medibook.appointment.exception.InvalidStatusTransitionException;
import com.medibook.appointment.exception.ServiceCallException;
import com.medibook.appointment.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final RestTemplate restTemplate;

    @Value("${services.schedule-url}")
    private String scheduleUrl;

    @Value("${services.notification-url}")
    private String notificationUrl;

    @Value("${services.provider-url}")
    private String providerUrl;

    @Override
    @CacheEvict(cacheNames = {
            "appointments.byId", "appointments.patient", "appointments.provider",
            "appointments.providerDate", "appointments.upcoming",
            "appointments.all", "appointments.count"
    }, allEntries = true)
    public AppointmentResponse bookAppointment(BookAppointmentRequest req) {
        log.info("Booking appointment: patientId={} slotId={}", req.getPatientId(), req.getSlotId());

        if (appointmentRepository.findBySlotId(req.getSlotId()).isPresent()) {
            throw new AppointmentAlreadyExistsException(
                    "Slot " + req.getSlotId() + " is already booked by another appointment");
        }

        Map<String, Object> slotDetails = fetchSlotDetails(req.getSlotId());
        LocalDate slotDate = parseLocalDate(slotDetails.get("slotDate"));
        LocalTime startTime = parseLocalTime(slotDetails.get("startTime"));
        LocalTime endTime = parseLocalTime(slotDetails.get("endTime"));
        validateBookableSlot(req.getProviderId(), slotDetails, slotDate, startTime);

        Appointment tempAppointment = Appointment.builder()
                .patientId(req.getPatientId())
                .providerId(req.getProviderId())
                .slotId(req.getSlotId())
                .appointmentDate(slotDate)
                .startTime(startTime)
                .endTime(endTime)
                .status(AppointmentStatus.SCHEDULED)
                .serviceType(req.getServiceType())
                .modeOfConsultation(req.getModeOfConsultation())
                .patientNotes(req.getPatientNotes())
                .build();

        Appointment saved = appointmentRepository.save(tempAppointment);
        bookSlotInScheduleService(req.getSlotId(), saved.getAppointmentId());
        log.info("Appointment created: id={}", saved.getAppointmentId());
        sendNotificationSafely("BOOKING_CONFIRMED", saved);
        return AppointmentResponse.from(saved);
    }

    @Override
    @CacheEvict(cacheNames = {
            "appointments.byId", "appointments.patient", "appointments.provider",
            "appointments.providerDate", "appointments.upcoming",
            "appointments.all", "appointments.count"
    }, allEntries = true)
    public AppointmentResponse cancelAppointment(Long appointmentId, String reason) {
        Appointment appt = findOrThrow(appointmentId);

        if (appt.getStatus() != AppointmentStatus.SCHEDULED) {
            throw new InvalidStatusTransitionException(
                    "Cannot cancel appointment in status: " + appt.getStatus()
                            + ". Only SCHEDULED appointments can be cancelled.");
        }

        releaseSlotInScheduleService(appt.getSlotId());
        appt.setStatus(AppointmentStatus.CANCELLED);
        appt.setCancellationReason(reason);
        Appointment cancelled = appointmentRepository.save(appt);

        log.info("Appointment CANCELLED: id={} reason={}", appointmentId, reason);
        sendNotificationSafely("APPOINTMENT_CANCELLED", cancelled);
        return AppointmentResponse.from(cancelled);
    }

    @Override
    @CacheEvict(cacheNames = {
            "appointments.byId", "appointments.patient", "appointments.provider",
            "appointments.providerDate", "appointments.upcoming",
            "appointments.all", "appointments.count"
    }, allEntries = true)
    public AppointmentResponse rescheduleAppointment(Long appointmentId, Long newSlotId) {
        Appointment appt = findOrThrow(appointmentId);

        if (appt.getStatus() != AppointmentStatus.SCHEDULED) {
            throw new InvalidStatusTransitionException(
                    "Cannot reschedule appointment in status: " + appt.getStatus());
        }

        releaseSlotInScheduleService(appt.getSlotId());
        bookSlotInScheduleService(newSlotId, appointmentId);

        Map<String, Object> newSlotDetails = fetchSlotDetails(newSlotId);
        LocalDate newSlotDate = parseLocalDate(newSlotDetails.get("slotDate"));
        LocalTime newStartTime = parseLocalTime(newSlotDetails.get("startTime"));
        LocalTime newEndTime = parseLocalTime(newSlotDetails.get("endTime"));
        validateBookableSlot(appt.getProviderId(), newSlotDetails, newSlotDate, newStartTime);

        appt.setSlotId(newSlotId);
        appt.setAppointmentDate(newSlotDate);
        appt.setStartTime(newStartTime);
        appt.setEndTime(newEndTime);
        appt.setStatus(AppointmentStatus.RESCHEDULED);
        Appointment rescheduled = appointmentRepository.save(appt);

        log.info("Appointment RESCHEDULED: id={} newSlotId={}", appointmentId, newSlotId);
        sendNotificationSafely("APPOINTMENT_RESCHEDULED", rescheduled);
        return AppointmentResponse.from(rescheduled);
    }

    @Override
    @CacheEvict(cacheNames = {
            "appointments.byId", "appointments.patient", "appointments.provider",
            "appointments.providerDate", "appointments.upcoming",
            "appointments.all", "appointments.count"
    }, allEntries = true)
    public AppointmentResponse completeAppointment(Long appointmentId) {
        Appointment appt = findOrThrow(appointmentId);

        if (appt.getStatus() != AppointmentStatus.SCHEDULED
                && appt.getStatus() != AppointmentStatus.RESCHEDULED) {
            throw new InvalidStatusTransitionException(
                    "Cannot complete appointment in status: " + appt.getStatus());
        }

        appt.setStatus(AppointmentStatus.COMPLETED);
        Appointment completed = appointmentRepository.save(appt);

        log.info("Appointment COMPLETED: id={}", appointmentId);
        sendNotificationSafely("APPOINTMENT_COMPLETED", completed);
        return AppointmentResponse.from(completed);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "appointments.byId", key = "#id")
    public AppointmentResponse getById(Long id) {
        return AppointmentResponse.from(findOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "appointments.patient", key = "#patientId")
    public List<AppointmentResponse> getByPatient(Long patientId) {
        return appointmentRepository.findByPatientIdOrderByAppointmentDateDesc(patientId)
                .stream().map(AppointmentResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "appointments.provider", key = "#providerId")
    public List<AppointmentResponse> getByProvider(Long providerId) {
        return appointmentRepository.findByProviderIdOrderByAppointmentDateAscStartTimeAsc(providerId)
                .stream().map(AppointmentResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "appointments.providerDate", key = "#providerId + '-' + #date")
    public List<AppointmentResponse> getByProviderAndDate(Long providerId, LocalDate date) {
        return appointmentRepository.findByProviderIdAndAppointmentDateOrderByStartTimeAsc(providerId, date)
                .stream().map(AppointmentResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "appointments.upcoming", key = "#patientId")
    public List<AppointmentResponse> getUpcomingByPatient(Long patientId) {
        return appointmentRepository.findUpcomingByPatient(patientId, LocalDate.now(ZoneId.of("Asia/Kolkata")))
                .stream().map(AppointmentResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "appointments.all", key = "'all'")
    public List<AppointmentResponse> getAllAppointments() {
        return appointmentRepository.findAll()
                .stream().map(AppointmentResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "appointments.count", key = "#providerId")
    public long countByProvider(Long providerId) {
        return appointmentRepository.countByProviderId(providerId);
    }

    private Appointment findOrThrow(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new AppointmentNotFoundException(id));
    }

    private void bookSlotInScheduleService(Long slotId, Long appointmentId) {
        try {
            String url = scheduleUrl + "/" + slotId + "/book?appointmentId=" + appointmentId;
            restTemplate.put(url, null);
            log.debug("Slot {} booked in schedule-service for appointmentId={}", slotId, appointmentId);
        } catch (RestClientException ex) {
            throw new ServiceCallException("schedule-service (book slot)", ex);
        }
    }

    private void releaseSlotInScheduleService(Long slotId) {
        try {
            restTemplate.put(scheduleUrl + "/" + slotId + "/release", null);
            log.debug("Slot {} released in schedule-service", slotId);
        } catch (RestClientException ex) {
            log.error("Failed to release slot {} in schedule-service: {}", slotId, ex.getMessage());
        }
    }

    private void sendNotificationSafely(String eventType, Appointment appt) {
        try {
            Map<String, Object> payload = Map.of(
                    "eventType", eventType,
                    "appointmentId", appt.getAppointmentId(),
                    "patientId", appt.getPatientId(),
                    "providerId", appt.getProviderId()
            );
            restTemplate.postForEntity(notificationUrl + "/appointment-event", payload, String.class);
            log.debug("Notification sent: eventType={} appointmentId={}", eventType, appt.getAppointmentId());
        } catch (Exception ex) {
            log.warn("Notification send failed (non-blocking): eventType={} error={}", eventType, ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchSlotDetails(Long slotId) {
        try {
            String baseUrl = scheduleUrl.replace("/internal/slots", "");
            String url = baseUrl + "/api/v1/slots/" + slotId;
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            if (resp.getBody() != null) {
                return resp.getBody();
            }
        } catch (Exception ex) {
            log.warn("Could not fetch slot details for slotId={}: {}", slotId, ex.getMessage());
        }
        return Map.of();
    }

    private LocalDate parseLocalDate(Object val) {
        if (val == null) return null;
        if (val instanceof String s) {
            try {
                return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
            } catch (Exception e) {
                return null;
            }
        }
        if (val instanceof java.util.List<?> list && list.size() >= 3) {
            try {
                int y = ((Number) list.get(0)).intValue();
                int m = ((Number) list.get(1)).intValue();
                int d = ((Number) list.get(2)).intValue();
                return LocalDate.of(y, m, d);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private LocalTime parseLocalTime(Object val) {
        if (val == null) return null;
        if (val instanceof String s) {
            try {
                return LocalTime.parse(s.length() > 8 ? s.substring(0, 8) : s);
            } catch (Exception e) {
                return null;
            }
        }
        if (val instanceof java.util.List<?> list && list.size() >= 2) {
            try {
                int h = ((Number) list.get(0)).intValue();
                int min = ((Number) list.get(1)).intValue();
                int sec = list.size() >= 3 ? ((Number) list.get(2)).intValue() : 0;
                return LocalTime.of(h, min, sec);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private void validateBookableSlot(Long providerId, Map<String, Object> slotDetails,
                                      LocalDate slotDate, LocalTime startTime) {
        if (slotDate == null || startTime == null) {
            throw new ServiceCallException("Call to schedule-service failed: slot details unavailable");
        }

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Kolkata"));
        if (slotDate.isBefore(today) || (slotDate.isEqual(today) && startTime.isBefore(now))) {
            throw new IllegalArgumentException("Cannot book a past time slot. Please choose a future slot.");
        }

        if (Boolean.TRUE.equals(slotDetails.get("isBlocked"))) {
            throw new IllegalStateException("This slot is no longer available because the provider has blocked it.");
        }

        if (Boolean.TRUE.equals(slotDetails.get("isBooked"))) {
            throw new IllegalStateException("This slot has just been booked by another patient. Please choose another slot.");
        }

        verifyProviderAvailability(providerId);
    }

    private void verifyProviderAvailability(Long providerId) {
        try {
            ResponseEntity<Map> resp = restTemplate.getForEntity(providerUrl + "/" + providerId, Map.class);
            Map<?, ?> body = resp.getBody();
            if (body == null) {
                throw new IllegalStateException("Provider details are unavailable right now. Please try again.");
            }
            if (Boolean.FALSE.equals(body.get("isAvailable"))) {
                throw new IllegalStateException("This provider is currently unavailable for booking.");
            }
            if (Boolean.FALSE.equals(body.get("isVerified"))) {
                throw new IllegalStateException("This provider profile is not currently bookable.");
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceCallException("provider-service (availability check)", ex);
        }
    }
}
