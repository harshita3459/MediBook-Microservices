package com.medibook.auth.oauth2;

import com.medibook.auth.entity.User;
import com.medibook.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        String email;
        String name;
        String providerId;
        User.AuthProvider provider;

        if ("google".equals(registrationId)) {
            email      = oAuth2User.getAttribute("email");
            name       = oAuth2User.getAttribute("name");
            providerId = oAuth2User.getAttribute("sub");
            provider   = User.AuthProvider.GOOGLE;
        } else if ("github".equals(registrationId)) {
            email = oAuth2User.getAttribute("email");
            // GitHub may return null email if user has it private
            if (email == null) {
                // Fallback: use login as email placeholder
                String login = oAuth2User.getAttribute("login");
                email = login + "@github-noemail.com";
            }
            name       = oAuth2User.getAttribute("name") != null
                         ? oAuth2User.getAttribute("name")
                         : oAuth2User.getAttribute("login");
            providerId = String.valueOf(oAuth2User.getAttribute("id"));
            provider   = User.AuthProvider.GITHUB;
        } else {
            throw new OAuth2AuthenticationException("Unsupported provider: " + registrationId);
        }

        final String resolvedEmail = email;
        final String resolvedName = name;
        final String resolvedProviderId = providerId;
        final User.AuthProvider resolvedProvider = provider;

        log.info("OAuth2 login: provider={} email={}", registrationId, resolvedEmail);

        // Find or create the user in our DB
        User user = userRepository.findByEmail(resolvedEmail)
            .map(existing -> {
                existing.setProvider(resolvedProvider);
                existing.setProviderId(resolvedProviderId);
                if (existing.getFullName() == null || existing.getFullName().isBlank()) {
                    existing.setFullName(resolvedName);
                }
                return userRepository.save(existing);
            })
            .orElseGet(() -> {
                User newUser = User.builder()
                    .email(resolvedEmail)
                    .fullName(resolvedName)
                    .provider(resolvedProvider)
                    .providerId(resolvedProviderId)
                    .role(User.Role.PATIENT)
                    .isActive(true)
                    .build();
                return userRepository.save(newUser);
            });

        log.info("OAuth2 user saved/found: userId={}", user.getUserId());

        // Wrap into our custom principal that carries the DB userId
        return new OAuth2UserPrincipal(user, oAuth2User.getAttributes());
    }
}
