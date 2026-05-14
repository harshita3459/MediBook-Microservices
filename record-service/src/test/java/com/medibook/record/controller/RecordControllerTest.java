package com.medibook.record.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.medibook.record.dto.RecordResponse;
import com.medibook.record.service.RecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecordController Tests")
class RecordControllerTest {

    @Mock
    private RecordService recordService;

    @InjectMocks
    private RecordController recordController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(recordController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    private RecordResponse buildResponse(Long id) {
        return RecordResponse.builder()
                .recordId(id)
                .appointmentId(10L)
                .patientId(1L)
                .providerId(5L)
                .diagnosis("Hypertension")
                .prescription("Amlodipine 5mg")
                .notes("Monitor BP weekly")
                .followUpDate(LocalDate.of(2026, 7, 1))
                .followUpNotified(false)
                .createdAt(LocalDateTime.of(2026, 6, 1, 10, 0))
                .build();
    }

    private String createRecordJson() {
        return """
                {
                  "appointmentId": 10,
                  "patientId": 1,
                  "providerId": 5,
                  "diagnosis": "Hypertension",
                  "prescription": "Amlodipine 5mg",
                  "notes": "Monitor BP weekly",
                  "labResults": "BP: 150/90"
                }
                """;
    }

    @Test
    @DisplayName("POST /api/v1/records -> 201 CREATED")
    void create_validRequest_returns201() throws Exception {
        given(recordService.createRecord(any())).willReturn(buildResponse(1L));

        mockMvc.perform(post("/api/v1/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRecordJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recordId").value(1))
                .andExpect(jsonPath("$.diagnosis").value("Hypertension"));
    }

    @Test
    @DisplayName("GET /api/v1/records/{id} -> 200 OK")
    void getById_returns200() throws Exception {
        given(recordService.getRecordById(1L)).willReturn(buildResponse(1L));

        mockMvc.perform(get("/api/v1/records/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordId").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/records/appointment/{appointmentId} -> 200 OK")
    void getByAppointment_returns200() throws Exception {
        given(recordService.getRecordByAppointment(10L)).willReturn(buildResponse(1L));

        mockMvc.perform(get("/api/v1/records/appointment/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentId").value(10));
    }

    @Test
    @DisplayName("GET /api/v1/records/patient/{patientId} -> 200 OK with list")
    void getByPatient_returns200() throws Exception {
        given(recordService.getRecordsByPatient(1L))
                .willReturn(List.of(buildResponse(1L), buildResponse(2L)));

        mockMvc.perform(get("/api/v1/records/patient/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/records/patient/{patientId}/count -> 200 OK")
    void getCount_returns200() throws Exception {
        given(recordService.getRecordCount(1L)).willReturn(5L);

        mockMvc.perform(get("/api/v1/records/patient/1/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5));
    }

    @Test
    @DisplayName("GET /api/v1/records/provider/{providerId} -> 200 OK")
    void getByProvider_returns200() throws Exception {
        given(recordService.getRecordsByProvider(5L))
                .willReturn(List.of(buildResponse(1L)));

        mockMvc.perform(get("/api/v1/records/provider/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/records/provider/{providerId}/followups -> 200 OK")
    void getProviderFollowUps_returns200() throws Exception {
        given(recordService.getProviderFollowUps(5L))
                .willReturn(List.of(buildResponse(1L)));

        mockMvc.perform(get("/api/v1/records/provider/5/followups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/records/followups -> 200 OK")
    void getFollowUps_returns200() throws Exception {
        given(recordService.getFollowUpRecords(any()))
                .willReturn(List.of(buildResponse(1L)));

        mockMvc.perform(get("/api/v1/records/followups")
                        .param("date", "2026-07-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/records -> 200 OK with all records")
    void getAll_returns200() throws Exception {
        given(recordService.getAllRecords())
                .willReturn(List.of(buildResponse(1L), buildResponse(2L)));

        mockMvc.perform(get("/api/v1/records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("PUT /api/v1/records/{id} -> 200 OK")
    void update_returns200() throws Exception {
        given(recordService.updateRecord(eq(1L), any())).willReturn(buildResponse(1L));

        mockMvc.perform(put("/api/v1/records/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diagnosis\":\"Updated diagnosis\",\"prescription\":\"New med\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordId").value(1));
    }

    @Test
    @DisplayName("PUT /api/v1/records/{id}/attach -> 200 OK")
    void attach_returns200() throws Exception {
        RecordResponse withAttachment = buildResponse(1L);
        withAttachment.setAttachmentUrl("https://s3.amazonaws.com/doc.pdf");
        given(recordService.attachDocument(eq(1L), anyString())).willReturn(withAttachment);

        mockMvc.perform(put("/api/v1/records/1/attach")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attachmentUrl\":\"https://s3.amazonaws.com/doc.pdf\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachmentUrl").value("https://s3.amazonaws.com/doc.pdf"));
    }

    @Test
    @DisplayName("DELETE /api/v1/records/{id} -> 200 OK with message")
    void delete_returns200() throws Exception {
        willDoNothing().given(recordService).deleteRecord(1L);

        mockMvc.perform(delete("/api/v1/records/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Medical record deleted successfully"));
    }
}
