package com.pystelectronic.rfid.transfer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
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

/**
 * Configuración de seguridad para PRODUCCIÓN.
 *
 * Activa solo con --spring.profiles.active=prod
 * El DevSecurityConfig (@Profile("dev")) sigue existiendo para desarrollo local.
 *
 * RBAC basado en roles del realm Keycloak:
 *   ROLE_ADMIN    → acceso total
 *   ROLE_OPERATOR → crear/despachar/recibir/validar
 *   ROLE_READER   → solo GETs
 *   ROLE_DEVICE   → POST read-tags y rfid-validations
 *
 * Sprint 6 — Pystelectronic · Ing. José Hernán Liza Garavito
 */
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

                // Públicos — health checks sin token
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/info"
                ).permitAll()

                // Solo ADMIN puede consultar audit-logs
                .requestMatchers("/api/v1/audit-logs/**")
                    .hasRole("ADMIN")

                // PUT y DELETE: solo ADMIN
                .requestMatchers(HttpMethod.PUT, "/api/v1/**")
                    .hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/**")
                    .hasRole("ADMIN")

                // Lecturas crudas: ADMIN + OPERATOR + DEVICE (PDAs leen)
                .requestMatchers(HttpMethod.POST, "/api/v1/read-tags/**")
                    .hasAnyRole("ADMIN", "OPERATOR", "DEVICE")

                // Validaciones RFID: ADMIN + OPERATOR + DEVICE (portales validan)
                .requestMatchers(HttpMethod.POST,
                    "/api/v1/transfers/*/rfid-validations")
                    .hasAnyRole("ADMIN", "OPERATOR", "DEVICE")

                // Operaciones de escritura sobre traslados: ADMIN + OPERATOR
                .requestMatchers(HttpMethod.POST,
                    "/api/v1/transfers",
                    "/api/v1/transfers/*/pallets",
                    "/api/v1/pallets/*/contents",
                    "/api/v1/transfers/*/dispatch",
                    "/api/v1/transfers/*/receipts")
                    .hasAnyRole("ADMIN", "OPERATOR")

                // Todos los GETs: cualquier rol autenticado
                .requestMatchers(HttpMethod.GET, "/api/v1/**")
                    .hasAnyRole("ADMIN", "OPERATOR", "READER", "DEVICE")

                .anyRequest().authenticated()
            )

            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            )

            // AuditFilter corre después de la validación JWT
            .addFilterAfter(auditFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Extrae roles del claim realm_access.roles de Keycloak.
     * Keycloak coloca los roles del realm en:
     * { "realm_access": { "roles": ["ROLE_ADMIN", "ROLE_OPERATOR", ...] } }
     *
     * Spring Security espera "ROLE_" como prefijo para hasRole("ADMIN").
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(keycloakRolesConverter());
        // El username del token Keycloak está en preferred_username
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }

    @Bean
    public Converter<Jwt, Collection<GrantedAuthority>> keycloakRolesConverter() {
        return jwt -> {
            // Roles estándar de Spring (scope, etc.)
            JwtGrantedAuthoritiesConverter defaultConverter = new JwtGrantedAuthoritiesConverter();
            List<GrantedAuthority> authorities = new ArrayList<>(defaultConverter.convert(jwt));

            // Roles del realm de Keycloak
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
        };
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
            "Authorization",
            "Content-Type",
            "X-Correlation-Id",
            "X-Idempotency-Key",
            "X-Requested-With"
        ));
        config.setExposedHeaders(List.of("X-Correlation-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
