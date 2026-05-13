package com.medibook.provider.repository;

import com.medibook.provider.entity.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data access layer for Provider.
 *
 * Spring Data JPA auto-implements all these methods at runtime —
 * we only declare the method signatures and Spring generates the SQL.
 * Custom queries use JPQL (not SQL) so they work with any database.
 */
@Repository
public interface ProviderRepository extends JpaRepository<Provider, Long> {

    // ── Basic lookups ──────────────────────────────────────────────────────────

    // Find by the userId from auth-service — used when a provider logs in
    Optional<Provider> findByUserId(Long userId);

    // Check if a userId already has a provider profile
    boolean existsByUserId(Long userId);

    // ── Search queries ─────────────────────────────────────────────────────────

    // All verified providers of a given specialization — main patient search
    List<Provider> findBySpecializationIgnoreCaseAndIsVerifiedTrue(String specialization);

    // All providers in a city (verified only) — location filter
    List<Provider> findByCityIgnoreCaseAndIsVerifiedTrue(String city);

    /**
     * Full-text search across name/specialization/clinic/city.
     * Uses LOWER() for case-insensitive matching without a full-text index.
     * In production with large datasets, replace with MySQL FULLTEXT or Elasticsearch.
     *
     * Note: 'name' is NOT on the Provider entity — it lives in auth_db.
     * So we search clinicName and specialization here, and filter by name
     * separately after fetching from auth-service if needed.
     */
    @Query("""
        SELECT p FROM Provider p
        WHERE p.isVerified = true
          AND (
               LOWER(p.specialization) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(p.clinicName)     LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(p.clinicAddress)  LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(p.city)           LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
        ORDER BY p.avgRating DESC
        """)
    List<Provider> searchVerifiedProviders(@Param("keyword") String keyword);

    // ── Admin queries ──────────────────────────────────────────────────────────

    // Providers pending verification — admin dashboard
    List<Provider> findByIsVerifiedFalse();

    // All verified providers — used by admin overview
    List<Provider> findByIsVerifiedTrue();

    // All available AND verified providers — shown on browse page
    List<Provider> findByIsVerifiedTrueAndIsAvailableTrue();

    // ── Analytics queries ──────────────────────────────────────────────────────

    // Count by specialization — for admin analytics ("most popular specialization")
    long countBySpecialization(String specialization);

    /**
     * Top-rated providers — used for homepage featured section.
     * Returns providers with rating >= minRating, sorted best-first.
     */
    @Query("""
        SELECT p FROM Provider p
        WHERE p.isVerified = true
          AND p.isAvailable = true
          AND p.avgRating >= :minRating
        ORDER BY p.avgRating DESC, p.totalReviews DESC
        """)
    List<Provider> findTopRatedProviders(@Param("minRating") double minRating);

    /**
     * Providers by fee range — used when patient filters by budget.
     */
    @Query("""
        SELECT p FROM Provider p
        WHERE p.isVerified = true
          AND p.isAvailable = true
          AND p.consultationFee BETWEEN :minFee AND :maxFee
        ORDER BY p.consultationFee ASC
        """)
    List<Provider> findByFeeRange(@Param("minFee") double minFee,
                                   @Param("maxFee") double maxFee);
}