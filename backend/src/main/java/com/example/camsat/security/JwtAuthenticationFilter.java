package com.example.camsat.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Not a @Component on purpose: it is created inside SecurityConfig so it is registered only in the security chain. */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    public static final String AUTH_ERROR_ATTRIBUTE = "camsat.auth.error";
    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) { this.jwtService = jwtService; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = header.substring(7).trim();
            try {
                AuthUser user = jwtService.verify(token);
                var auth = new UsernamePasswordAuthenticationToken(user, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (ExpiredJwtException e) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, "expired");
            } catch (JwtException | IllegalArgumentException e) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, "invalid");
            }
        }
        chain.doFilter(request, response);
    }
}
