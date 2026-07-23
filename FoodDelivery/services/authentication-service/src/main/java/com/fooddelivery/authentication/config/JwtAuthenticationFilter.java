package com.fooddelivery.authentication.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.fooddelivery.authentication.domain.model.User;
import com.fooddelivery.authentication.domain.model.UserSession;
import com.fooddelivery.authentication.domain.repository.UserRepository;
import com.fooddelivery.authentication.domain.repository.UserSessionRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;

    public JwtAuthenticationFilter(
            JwtTokenProvider tokenProvider,
            UserRepository userRepository,
            UserSessionRepository userSessionRepository) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.userSessionRepository = userSessionRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                UUID userId = tokenProvider.getUserIdFromToken(jwt);
                UUID sessionId = tokenProvider.getSessionIdFromToken(jwt);
                User user = getActiveUserForSession(userId, sessionId);

                if (user == null) {
                    filterChain.doFilter(request, response);
                    return;
                }

                String email = user.getEmail();
                String role = user.getRole().name();
                SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);
                UserPrincipal principal = new UserPrincipal(userId, email, role, sessionId);

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, Collections.singletonList(authority));

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            // Ignore security context setting failures
        }

        filterChain.doFilter(request, response);
    }

    private User getActiveUserForSession(UUID userId, UUID sessionId) {
        if (userId == null || sessionId == null) {
            return null;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isActive()) {
            return null;
        }
        UserSession session = userSessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.isDeleted() || !session.getUser().getId().equals(userId)) {
            return null;
        }
        return user;
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
