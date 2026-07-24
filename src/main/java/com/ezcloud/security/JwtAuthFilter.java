package com.ezcloud.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Extracts a Bearer token from the Authorization header, validates it, and
 * populates the SecurityContext. Invalid tokens are ignored (the request then
 * fails authorization downstream) rather than erroring here.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final DomainUserDetailsService userDetailsService;

    public JwtAuthFilter(JwtService jwtService, DomainUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Optional.ofNullable(request.getHeader("Authorization"))
                .filter(header -> header.startsWith(BEARER_PREFIX))
                .filter(header -> SecurityContextHolder.getContext().getAuthentication() == null)
                .map(header -> header.substring(BEARER_PREFIX.length()))
                .ifPresent(token -> authenticate(token, request));
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        try {
            var username = jwtService.validateAndExtractUsername(token);
            var userDetails = userDetailsService.loadUserByUsername(username);
            var authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | UsernameNotFoundException ignored) {
            // Leave the context unauthenticated; authorization rules will reject.
        }
    }
}
