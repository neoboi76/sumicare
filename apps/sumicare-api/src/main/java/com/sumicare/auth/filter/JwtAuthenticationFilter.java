/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.auth.filter;

import com.sumicare.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Date;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER)) {
            chain.doFilter(request, response);
            return;
        }
        String token = header.substring(BEARER.length());
        try {
            // Order matters: signature is verified first, then the per-jti deny-list and the
            // per-user "tokens-since" watermark are checked BEFORE any Authentication is built,
            // so a revoked or pre-watermark token never grants access.
            Claims claims = jwtService.parse(token);
            if (jwtService.isRevoked(claims.getId())) {
                chain.doFilter(request, response);
                return;
            }
            Date issuedAt = claims.getIssuedAt();
            if (issuedAt != null && jwtService.isTokenIssuedBeforeRevocation(
                    claims.getSubject(), issuedAt.toInstant().getEpochSecond())) {
                chain.doFilter(request, response);
                return;
            }
            if (!"access".equals(claims.get("type", String.class))) {
                chain.doFilter(request, response);
                return;
            }
            String role = claims.get("role", String.class);
            if (role == null) {
                chain.doFilter(request, response);
                return;
            }
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                    claims.getSubject(),
                    claims.get("org", String.class),
                    role
            );
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    principal, token, List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (JwtException ignored) {
            // An invalid token is dropped silently so the request proceeds as anonymous and
            // the 401 entry point (not a 500) handles it, which lets the client's refresh flow trigger.
        }
        chain.doFilter(request, response);
    }

    public record AuthenticatedPrincipal(String userId, String organizationId, String role) {}
}
