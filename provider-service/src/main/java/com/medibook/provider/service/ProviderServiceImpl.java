package com.medibook.provider.service;

import com.medibook.provider.entity.Provider;
import com.medibook.provider.exception.ProviderAlreadyExistsException;
import com.medibook.provider.exception.ProviderNotFoundException;
import com.medibook.provider.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProviderServiceImpl implements ProviderService {

    private final ProviderRepository providerRepository;

    @Override
    @CacheEvict(cacheNames = {
            "providers.list", "providers.profile", "providers.user", "providers.search",
            "providers.specialization", "providers.city", "providers.topRated",
            "providers.feeRange", "providers.admin"
    }, allEntries = true)
    public Provider registerProvider(Provider provider) {
        log.info("Registering provider for userId: {}", provider.getUserId());

        if (providerRepository.existsByUserId(provider.getUserId())) {
            throw new ProviderAlreadyExistsException(
                    "Provider profile already exists for userId: " + provider.getUserId());
        }

        provider.setVerified(false);
        provider.setAvailable(true);
        provider.setAvgRating(0.0);
        provider.setTotalReviews(0);

        Provider saved = providerRepository.save(provider);
        log.info("Provider registered with ID: {} (pending verification)", saved.getProviderId());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "providers.profile", key = "#providerId")
    public Provider getProviderById(Long providerId) {
        return providerRepository.findById(providerId)
                .orElseThrow(() -> new ProviderNotFoundException(
                        "Provider not found: " + providerId));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "providers.user", key = "#userId")
    public Provider getProviderByUserId(Long userId) {
        return providerRepository.findByUserId(userId)
                .orElseThrow(() -> new ProviderNotFoundException(
                        "Provider not found for userId: " + userId));
    }

    @Override
    @CacheEvict(cacheNames = {
            "providers.list", "providers.profile", "providers.user", "providers.search",
            "providers.specialization", "providers.city", "providers.topRated",
            "providers.feeRange", "providers.admin"
    }, allEntries = true)
    public Provider updateProvider(Long providerId, Provider updatedData) {
        Provider existing = providerRepository.findById(providerId)
                .orElseThrow(() -> new ProviderNotFoundException(
                        "Provider not found: " + providerId));

        if (updatedData.getSpecialization() != null) existing.setSpecialization(updatedData.getSpecialization());
        if (updatedData.getQualification() != null) existing.setQualification(updatedData.getQualification());
        if (updatedData.getBio() != null) existing.setBio(updatedData.getBio());
        if (updatedData.getClinicName() != null) existing.setClinicName(updatedData.getClinicName());
        if (updatedData.getClinicAddress() != null) existing.setClinicAddress(updatedData.getClinicAddress());
        if (updatedData.getCity() != null) existing.setCity(updatedData.getCity());
        if (updatedData.getClinicPincode() != null) existing.setClinicPincode(updatedData.getClinicPincode());
        if (updatedData.getProfilePicUrl() != null) existing.setProfilePicUrl(updatedData.getProfilePicUrl());
        if (updatedData.getRegistrationNumber() != null) existing.setRegistrationNumber(updatedData.getRegistrationNumber());
        if (updatedData.getLanguagesSpoken() != null) existing.setLanguagesSpoken(updatedData.getLanguagesSpoken());
        if (updatedData.getConsultationFee() != null) existing.setConsultationFee(updatedData.getConsultationFee());
        if (updatedData.getExperienceYears() != null) existing.setExperienceYears(updatedData.getExperienceYears());

        Provider updated = providerRepository.save(existing);
        log.info("Provider updated: {}", providerId);
        return updated;
    }

    @Override
    @CacheEvict(cacheNames = {
            "providers.list", "providers.profile", "providers.user", "providers.search",
            "providers.specialization", "providers.city", "providers.topRated",
            "providers.feeRange", "providers.admin"
    }, allEntries = true)
    public void deleteProvider(Long providerId) {
        if (!providerRepository.existsById(providerId)) {
            throw new ProviderNotFoundException("Provider not found: " + providerId);
        }
        providerRepository.deleteById(providerId);
        log.info("Provider deleted: {}", providerId);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "providers.list", key = "'verified-available'")
    public List<Provider> getAllVerifiedProviders() {
        return providerRepository.findByIsVerifiedTrueAndIsAvailableTrue();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "providers.specialization", key = "#specialization.toLowerCase()")
    public List<Provider> getBySpecialization(String specialization) {
        return providerRepository.findBySpecializationIgnoreCaseAndIsVerifiedTrue(specialization);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "providers.search", key = "#keyword == null ? '' : #keyword.trim().toLowerCase()")
    public List<Provider> searchProviders(String keyword) {
        String cleanKeyword = keyword == null ? "" : keyword.trim();
        return providerRepository.searchVerifiedProviders(cleanKeyword);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "providers.city", key = "#city.toLowerCase()")
    public List<Provider> getByCity(String city) {
        return providerRepository.findByCityIgnoreCaseAndIsVerifiedTrue(city);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "providers.topRated", key = "#minRating")
    public List<Provider> getTopRated(double minRating) {
        return providerRepository.findTopRatedProviders(minRating);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "providers.feeRange", key = "#minFee + '-' + #maxFee")
    public List<Provider> getByFeeRange(double minFee, double maxFee) {
        return providerRepository.findByFeeRange(minFee, maxFee);
    }

    @Override
    @CacheEvict(cacheNames = {
            "providers.list", "providers.profile", "providers.user", "providers.search",
            "providers.specialization", "providers.city", "providers.topRated",
            "providers.feeRange", "providers.admin"
    }, allEntries = true)
    public Provider verifyProvider(Long providerId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ProviderNotFoundException(
                        "Provider not found: " + providerId));

        provider.setVerified(true);
        Provider verified = providerRepository.save(provider);
        log.info("Provider VERIFIED by admin: {}", providerId);
        return verified;
    }

    @Override
    @CacheEvict(cacheNames = {
            "providers.list", "providers.profile", "providers.user", "providers.search",
            "providers.specialization", "providers.city", "providers.topRated",
            "providers.feeRange", "providers.admin"
    }, allEntries = true)
    public Provider rejectProvider(Long providerId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ProviderNotFoundException(
                        "Provider not found: " + providerId));

        provider.setVerified(false);
        provider.setAvailable(false);
        Provider rejected = providerRepository.save(provider);
        log.warn("Provider REJECTED by admin: {}", providerId);
        return rejected;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "providers.admin", key = "'pending'")
    public List<Provider> getPendingVerification() {
        return providerRepository.findByIsVerifiedFalse();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "providers.admin", key = "'all'")
    public List<Provider> getAllProviders() {
        return providerRepository.findAll();
    }

    @Override
    @CacheEvict(cacheNames = {
            "providers.list", "providers.profile", "providers.user", "providers.search",
            "providers.specialization", "providers.city", "providers.topRated",
            "providers.feeRange", "providers.admin"
    }, allEntries = true)
    public Provider setAvailability(Long providerId, boolean available) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ProviderNotFoundException(
                        "Provider not found: " + providerId));

        provider.setAvailable(available);
        Provider updated = providerRepository.save(provider);
        log.info("Provider {} availability set to: {}", providerId, available);
        return updated;
    }

    @Override
    @CacheEvict(cacheNames = {
            "providers.list", "providers.profile", "providers.user", "providers.search",
            "providers.specialization", "providers.city", "providers.topRated",
            "providers.feeRange", "providers.admin"
    }, allEntries = true)
    public Provider updateRating(Long providerId, double newAvgRating, int totalReviews) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ProviderNotFoundException(
                        "Provider not found: " + providerId));

        double clampedRating = Math.max(0.0, Math.min(5.0, newAvgRating));
        provider.setAvgRating(clampedRating);
        provider.setTotalReviews(totalReviews);

        Provider updated = providerRepository.save(provider);
        log.info("Provider {} rating updated to: {} ({} reviews)",
                providerId, clampedRating, totalReviews);
        return updated;
    }
}
