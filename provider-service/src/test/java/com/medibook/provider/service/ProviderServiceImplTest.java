package com.medibook.provider.service;

import com.medibook.provider.entity.Provider;
import com.medibook.provider.exception.ProviderAlreadyExistsException;
import com.medibook.provider.exception.ProviderNotFoundException;
import com.medibook.provider.repository.ProviderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProviderServiceImpl Tests")
class ProviderServiceImplTest {

    @Mock ProviderRepository providerRepository;
    @InjectMocks ProviderServiceImpl providerService;

    // ── Fixture ───────────────────────────────────────────────────────────

    private Provider buildProvider(Long id, Long userId, boolean verified, boolean available) {
        return Provider.builder()
                .providerId(id)
                .userId(userId)
                .specialization("Cardiology")
                .qualification("MBBS, MD")
                .clinicName("Heart Clinic")
                .city("Delhi")
                .isVerified(verified)
                .isAvailable(available)
                .avgRating(4.2)
                .totalReviews(50)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // registerProvider
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("registerProvider()")
    class RegisterTests {

        @Test
        @DisplayName("✅ saves provider as unverified and available")
        void register_newProvider_savedUnverified() {
            Provider input = buildProvider(null, 1L, true, true); // provider passes truthy values
            given(providerRepository.existsByUserId(1L)).willReturn(false);
            Provider saved = buildProvider(10L, 1L, false, true); // service overrides verified → false
            given(providerRepository.save(any(Provider.class))).willReturn(saved);

            Provider result = providerService.registerProvider(input);

            assertThat(result.getProviderId()).isEqualTo(10L);
            // Service must enforce isVerified=false regardless of input
            then(providerRepository).should().save(argThat(p ->
                    !p.isVerified()
                    && p.isAvailable()
                    && p.getAvgRating() == 0.0
                    && p.getTotalReviews() == 0
            ));
        }

        @Test
        @DisplayName("❌ duplicate userId throws ProviderAlreadyExistsException")
        void register_duplicateUserId_throwsException() {
            given(providerRepository.existsByUserId(5L)).willReturn(true);

            Provider input = buildProvider(null, 5L, false, true);
            assertThatThrownBy(() -> providerService.registerProvider(input))
                    .isInstanceOf(ProviderAlreadyExistsException.class)
                    .hasMessageContaining("5");

            then(providerRepository).should(never()).save(any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // verifyProvider / rejectProvider
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Admin Verification")
    class VerificationTests {

        @Test
        @DisplayName("✅ verifyProvider sets isVerified=true")
        void verify_provider_setsVerifiedTrue() {
            Provider p = buildProvider(1L, 1L, false, true);
            given(providerRepository.findById(1L)).willReturn(Optional.of(p));
            given(providerRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            Provider result = providerService.verifyProvider(1L);

            assertThat(result.isVerified()).isTrue();
        }

        @Test
        @DisplayName("✅ rejectProvider sets isVerified=false AND isAvailable=false")
        void reject_provider_setsVerifiedFalseAndUnavailable() {
            Provider p = buildProvider(1L, 1L, true, true);
            given(providerRepository.findById(1L)).willReturn(Optional.of(p));
            given(providerRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            Provider result = providerService.rejectProvider(1L);

            assertThat(result.isVerified()).isFalse();
            assertThat(result.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("❌ verify non-existent provider throws ProviderNotFoundException")
        void verify_notFound_throwsException() {
            given(providerRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> providerService.verifyProvider(999L))
                    .isInstanceOf(ProviderNotFoundException.class);
        }

        @Test
        @DisplayName("✅ getPendingVerification returns only unverified providers")
        void getPending_returnsUnverified() {
            List<Provider> pending = List.of(
                    buildProvider(1L, 1L, false, true),
                    buildProvider(2L, 2L, false, true)
            );
            given(providerRepository.findByIsVerifiedFalse()).willReturn(pending);

            List<Provider> result = providerService.getPendingVerification();

            assertThat(result).hasSize(2)
                    .allMatch(p -> !p.isVerified());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // setAvailability
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("setAvailability()")
    class AvailabilityTests {

        @Test
        @DisplayName("✅ provider can toggle availability ON")
        void setAvailable_true_setsAvailableTrue() {
            Provider p = buildProvider(1L, 1L, true, false);
            given(providerRepository.findById(1L)).willReturn(Optional.of(p));
            given(providerRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            Provider result = providerService.setAvailability(1L, true);

            assertThat(result.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("✅ provider can toggle availability OFF")
        void setAvailable_false_setsAvailableFalse() {
            Provider p = buildProvider(1L, 1L, true, true);
            given(providerRepository.findById(1L)).willReturn(Optional.of(p));
            given(providerRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            Provider result = providerService.setAvailability(1L, false);

            assertThat(result.isAvailable()).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // updateRating
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateRating()")
    class RatingTests {

        @Test
        @DisplayName("✅ rating is clamped to [0.0, 5.0] — never exceeds 5")
        void updateRating_ratingClamped() {
            Provider p = buildProvider(1L, 1L, true, true);
            given(providerRepository.findById(1L)).willReturn(Optional.of(p));
            given(providerRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            Provider result = providerService.updateRating(1L, 6.5, 100); // over 5

            assertThat(result.getAvgRating()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("✅ rating cannot go below 0")
        void updateRating_negativeRatingClamped() {
            Provider p = buildProvider(1L, 1L, true, true);
            given(providerRepository.findById(1L)).willReturn(Optional.of(p));
            given(providerRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            Provider result = providerService.updateRating(1L, -1.0, 0);

            assertThat(result.getAvgRating()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("✅ valid rating 4.3 with 123 reviews is stored as-is")
        void updateRating_validRating_storedCorrectly() {
            Provider p = buildProvider(1L, 1L, true, true);
            given(providerRepository.findById(1L)).willReturn(Optional.of(p));
            given(providerRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            Provider result = providerService.updateRating(1L, 4.3, 123);

            assertThat(result.getAvgRating()).isEqualTo(4.3);
            assertThat(result.getTotalReviews()).isEqualTo(123);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // getBySpecialization / getByCity / getTopRated
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Search operations")
    class SearchTests {

        @Test
        @DisplayName("✅ getBySpecialization delegates to repository and returns list")
        void getBySpecialization_returnsList() {
            given(providerRepository.findBySpecializationIgnoreCaseAndIsVerifiedTrue("cardiology"))
                    .willReturn(List.of(buildProvider(1L, 1L, true, true)));

            List<Provider> result = providerService.getBySpecialization("cardiology");

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("✅ getTopRated returns providers with rating ≥ minRating")
        void getTopRated_returnsVerifiedAboveMinRating() {
            List<Provider> topRated = List.of(buildProvider(1L, 1L, true, true));
            given(providerRepository.findTopRatedProviders(4.0)).willReturn(topRated);

            List<Provider> result = providerService.getTopRated(4.0);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("✅ searchProviders with blank keyword still calls repo")
        void search_nullKeyword_treatedAsEmpty() {
            given(providerRepository.searchVerifiedProviders("")).willReturn(List.of());

            List<Provider> result = providerService.searchProviders(null);

            assertThat(result).isEmpty();
            then(providerRepository).should().searchVerifiedProviders("");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // updateProvider
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateProvider()")
    class UpdateTests {

        @Test
        @DisplayName("✅ null fields in update do not overwrite existing values")
        void update_nullFields_preservesExisting() {
            Provider existing = buildProvider(1L, 1L, true, true);
            existing.setBio("Original bio");
            given(providerRepository.findById(1L)).willReturn(Optional.of(existing));
            given(providerRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            // Update with only specialization, bio = null
            Provider updates = Provider.builder()
                    .specialization("Neurology")
                    .bio(null) // should not overwrite
                    .build();

            Provider result = providerService.updateProvider(1L, updates);

            assertThat(result.getSpecialization()).isEqualTo("Neurology");
            assertThat(result.getBio()).isEqualTo("Original bio"); // unchanged
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // deleteProvider
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("deleteProvider()")
    class DeleteTests {

        @Test
        @DisplayName("✅ deletes existing provider")
        void delete_existing_callsDeleteById() {
            given(providerRepository.existsById(1L)).willReturn(true);

            providerService.deleteProvider(1L);

            then(providerRepository).should().deleteById(1L);
        }

        @Test
        @DisplayName("❌ deleting non-existent provider throws ProviderNotFoundException")
        void delete_notFound_throwsException() {
            given(providerRepository.existsById(999L)).willReturn(false);

            assertThatThrownBy(() -> providerService.deleteProvider(999L))
                    .isInstanceOf(ProviderNotFoundException.class);
        }
    }
}