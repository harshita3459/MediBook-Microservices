package com.medibook.provider.service;

import com.medibook.provider.entity.Provider;

import java.util.List;

/**
 * ProviderService interface — the business contract.
 * 1. Loose coupling — callers depend on the interface, not the concrete class
 */
public interface ProviderService {

    // ── Registration & Profile ─────────────────────────────────────────────────

    Provider registerProvider(Provider provider);

    Provider getProviderById(Long providerId);

    Provider getProviderByUserId(Long userId);

    Provider updateProvider(Long providerId, Provider updatedData);

    void deleteProvider(Long providerId);

    // ── Search ─────────────────────────────────────────────────────────────────

    List<Provider> getAllVerifiedProviders();

    List<Provider> getBySpecialization(String specialization);

    List<Provider> searchProviders(String keyword);

    List<Provider> getByCity(String city);

    List<Provider> getTopRated(double minRating);

    List<Provider> getByFeeRange(double minFee, double maxFee);

    // ── Admin Operations ───────────────────────────────────────────────────────

    Provider verifyProvider(Long providerId);

    Provider rejectProvider(Long providerId);

    List<Provider> getPendingVerification();

    List<Provider> getAllProviders();       // admin only — includes unverified

    // ── Availability ───────────────────────────────────────────────────────────

    Provider setAvailability(Long providerId, boolean available);

    // ── Rating (called by review-service) ─────────────────────────────────────

    Provider updateRating(Long providerId, double newAvgRating, int totalReviews);
}
