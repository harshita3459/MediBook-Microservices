package com.medibook.auth.oauth2;

import com.medibook.auth.entity.User;
import com.medibook.auth.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Called by Spring Security after a successful OAuth2 login.
 * Generates a JWT and redirects to the React frontend with tokens as query params.
 *
 * Set FRONTEND_URL env variable (or app property) to your frontend origin.
 * Defaults to http://localhost:5173 for local development.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;

    @Value("${medibook.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2UserPrincipal principal = (OAuth2UserPrincipal) authentication.getPrincipal();
        User user = principal.getUser();

        String accessToken  = jwtUtil.generateAccessToken(
            user.getEmail(), user.getRole().name(), user.getUserId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

        log.info("OAuth2 login successful for user: {} (role={})", user.getEmail(), user.getRole());

        String encodedName = user.getFullName() != null
            ? URLEncoder.encode(user.getFullName(), StandardCharsets.UTF_8) : "";

        String targetUrl = frontendUrl + "/oauth2/redirect"
            + "?accessToken="  + accessToken
            + "&refreshToken=" + refreshToken
            + "&userId="       + user.getUserId()
            + "&role="         + user.getRole().name()
            + "&email="        + URLEncoder.encode(user.getEmail(), StandardCharsets.UTF_8)
            + "&fullName="     + encodedName;

        response.sendRedirect(targetUrl);
    }
}
