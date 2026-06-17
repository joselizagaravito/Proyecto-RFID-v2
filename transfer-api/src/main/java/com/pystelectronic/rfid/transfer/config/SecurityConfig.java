package com.pystelectronic.rfid.transfer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Profile("prod")
public class SecurityConfig {

    private final AuditFilter auditFilter;

    public SecurityConfig(AuditFilter auditFilter) {
        this.auditFilter = auditFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .sessionManagement(s ->
                s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(c -> c.configurationSource(corsConfigurationSource()))
            .csrf(c -> c.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // ── Sprint 9: Sesión de pallet ──
                // Endpoint interno (service-to-service) — NO expuesto vía Nginx
                .requestMatchers(HttpMethod.POST, "/api/v1/internal/portals/*/session/read")
                    .permitAll()
                // Endpoints de operador (portal web) — requieren rol
                .requestMatchers(HttpMethod.POST, "/api/v1/portals/*/session/open")
                    .hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers(HttpMethod.POST, "/api/v1/portals/*/session/read")
                    .hasAnyRole("ADMIN", "OPERATOR", "DEVICE")
                .requestMatchers(HttpMethod.POST, "/api/v1/portals/*/session/close-pallet")
                    .hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers(HttpMethod.GET, "/api/v1/portals/*/session")
                    .hasAnyRole("ADMIN", "OPERATOR", "READER", "DEVICE")
                .requestMatchers("/api/v1/audit-logs/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/read-tags/**")
                    .hasAnyRole("ADMIN", "OPERATOR", "DEVICE")
                .requestMatchers(HttpMethod.POST, "/api/v1/transfers/*/rfid-validations")
                    .hasAnyRole("ADMIN", "OPERATOR", "DEVICE")
                .requestMatchers(HttpMethod.POST,
                    "/api/v1/transfers",
                    "/api/v1/transfers/*/pallets",
                    "/api/v1/pallets/*/contents",
                    "/api/v1/transfers/*/dispatch",
                    "/api/v1/transfers/*/receipts")
                    .hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers(HttpMethod.GET, "/api/v1/**")
                    .hasAnyRole("ADMIN", "OPERATOR", "READER", "DEVICE")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            )
            .addFilterAfter(auditFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("preferred_username");

        // Extractor de roles inline — sin @Bean separado para evitar conflicto con ConversionService
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            JwtGrantedAuthoritiesConverter defaultConverter = new JwtGrantedAuthoritiesConverter();
            List<GrantedAuthority> authorities = new ArrayList<>(defaultConverter.convert(jwt));

            @SuppressWarnings("unchecked")
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null) {
                @SuppressWarnings("unchecked")
                List<String> roles = (List<String>) realmAccess.get("roles");
                if (roles != null) {
                    roles.stream()
                        .filter(r -> r.startsWith("ROLE_"))
                        .map(SimpleGrantedAuthority::new)
                        .forEach(authorities::add);
                }
            }
            return authorities;
        });

        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
            "http://38.253.180.55",
            "http://localhost:3000",
            "http://localhost",
            "http://127.0.0.1:3000"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
            "Authorization", "Content-Type",
            "X-Correlation-Id", "X-Idempotency-Key", "X-Requested-With"
        ));
        config.setExposedHeaders(List.of("X-Correlation-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
