package com.example.camsat.config;

import com.example.camsat.security.JwtAuthenticationFilter;
import com.example.camsat.security.JwtService;
import com.example.camsat.security.RestAccessDeniedHandler;
import com.example.camsat.security.RestAuthenticationEntryPoint;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtService jwtService,
            RestAuthenticationEntryPoint entryPoint,
            RestAccessDeniedHandler deniedHandler,
            @Qualifier("corsConfigurationSource")
            CorsConfigurationSource corsSource
    ) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsSource))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler)
                )
                .headers(Customizer.withDefaults())
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties props) {

        List<String> origins =
                props.cors() == null || props.cors().allowedOrigins() == null
                        ? List.of()
                        : props.cors().allowedOrigins()
                        .stream()
                        .map(String::trim)
                        .filter(origin -> !origin.isEmpty())
                        .toList();

        if (origins.stream().anyMatch(origin -> origin.equals("*"))) {
            throw new IllegalStateException(
                    "CORS_ALLOWED_ORIGINS must list explicit origins; a wildcard is not allowed."
            );
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(
                List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")
        );
        config.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "Accept")
        );
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", config);

        return source;
    }
}
