package com.medibook.payment.service;

import com.medibook.payment.dto.*;
import java.util.List;

public interface PaymentService {
    PaymentResponse processPayment(ProcessPaymentRequest request);
    PaymentResponse getPaymentByAppointment(Long appointmentId);
    List<PaymentResponse> getPaymentsByPatient(Long patientId);
    List<PaymentResponse> getPaymentHistory(Long patientId);
    PaymentResponse refundPayment(Long paymentId, String reason);
    String getPaymentStatus(Long paymentId);
    PaymentResponse updatePaymentStatus(Long paymentId, String status);
    InvoiceResponse generateInvoice(Long paymentId);
    Double getTotalRevenue(Long providerId);
    EarningsSummary getEarningsSummary(Long providerId);
    List<PaymentResponse> getAllPayments();
}
