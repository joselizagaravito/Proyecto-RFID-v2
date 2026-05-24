package com.pystelectronic.rfid.transfer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pystelectronic.rfid.transfer.audit.AuditLogService;
import com.pystelectronic.rfid.transfer.audit.dto.AuditLogEntry;
import com.pystelectronic.rfid.transfer.audit.dto.AuditLevel;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Filtro de auditoría — intercepta toda petición autenticada.
 *
 * Flujo:
 *   1. Envuelve request/response para poder leer body después del filter chain
 *   2. Ejecuta el filter chain normalmente (el request llega al controller)
 *   3. En el bloque finally: construye AuditLogEntry y lo publica a Kafka
 *   4. El audit-service consume el topic y persiste en audit_log (PostgreSQL)
 *
 * Garantías:
 *   - Un fallo del audit NUNCA rompe el request principal (try-catch total)
 *   - La escritura es asíncrona via @Async en AuditLogService
 *   - El body se sanitiza antes de guardar (sin tokens, passwords, secrets)
 *
 * Sprint 6 — Pystelectronic · Ing. José Hernán Liza Garavito
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class AuditFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuditFilter.class);

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    private static final String[] SKIP_PATHS = {
        "/actuator/health",
        "/actuator/info",
        "/actuator/prometheus",
        "/favicon.ico"
    };

    public AuditFilter(AuditLogService auditLogService, ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        // No auditar health checks ni OPTIONS (preflight CORS)
        return "OPTIONS".equals(method)
            || Arrays.stream(SKIP_PATHS).anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        ContentCachingRequestWrapper wrappedReq  = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResp = new ContentCachingResponseWrapper(response);

        try {
            chain.doFilter(wrappedReq, wrappedResp);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            // Auditar solo requests autenticados (no anónimos)
            boolean esAutenticado = auth != null
                && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getName());

            if (esAutenticado) {
                try {
                    AuditLogEntry entry = construirEntrada(wrappedReq, wrappedResp, auth, duration);
                    auditLogService.saveAsync(entry);
                } catch (Exception e) {
                    // El audit NUNCA debe romper el flujo
                    log.error("[AuditFilter] Error construyendo entrada de auditoría: {}", e.getMessage());
                }
            }

            // CRÍTICO: copiar el body al response real para que el cliente lo reciba
            wrappedResp.copyBodyToResponse();
        }
    }

    private AuditLogEntry construirEntrada(ContentCachingRequestWrapper req,
                                            ContentCachingResponseWrapper resp,
                                            Authentication auth,
                                            long durationMs) {
        // IP real (considera proxy Nginx con X-Forwarded-For)
        String clientIp = Optional.ofNullable(req.getHeader("X-Forwarded-For"))
            .map(h -> h.split(",")[0].trim())
            .orElse(req.getRemoteAddr());

        // Roles como string
        String roles = auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.joining(", "));

        AuditLevel level = resolverNivel(req.getMethod(), req.getRequestURI());

        return AuditLogEntry.builder()
            .correlationId(req.getHeader("X-Correlation-Id"))
            .idempotencyKey(req.getHeader("X-Idempotency-Key"))
            .userId(auth.getName())
            .userRoles(roles)
            .clientIp(clientIp)
            .userAgent(req.getHeader("User-Agent"))
            .httpMethod(req.getMethod())
            .endpointPath(req.getRequestURI())
            .queryParams(buildQueryParams(req))
            .requestSummary(extractSummary(req, level))
            .httpStatus((short) resp.getStatus())
            .errorCode((String) req.getAttribute("rfid.error.code"))
            .durationMs((int) durationMs)
            .auditLevel(level)
            .createdAt(OffsetDateTime.now())
            .build();
    }

    /**
     * FULL   → POSTs críticos (transfers, dispatch, receipts) + PUT + DELETE
     * STANDARD → POSTs operacionales (pallets, contents, rfid-validations, read-tags)
     * READ   → todos los GETs
     */
    private AuditLevel resolverNivel(String method, String path) {
        if ("GET".equals(method))    return AuditLevel.READ;
        if ("PUT".equals(method) || "DELETE".equals(method)) return AuditLevel.FULL;

        if ("POST".equals(method)) {
            if (path.matches(".*/transfers$")
                || path.matches(".*/transfers/[^/]+/dispatch$")
                || path.matches(".*/transfers/[^/]+/receipts$")) {
                return AuditLevel.FULL;
            }
            return AuditLevel.STANDARD;
        }
        return AuditLevel.READ;
    }

    private String buildQueryParams(HttpServletRequest req) {
        if (req.getQueryString() == null) return null;
        try {
            Map<String, String> params = req.getParameterMap().entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> String.join(",", e.getValue())
                ));
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractSummary(ContentCachingRequestWrapper req, AuditLevel level) {
        if (level == AuditLevel.READ) return null;
        try {
            byte[] body = req.getContentAsByteArray();
            if (body.length == 0) return null;

            String bodyStr = new String(body, req.getCharacterEncoding());

            // Sanitizar datos sensibles
            bodyStr = bodyStr
                .replaceAll("\"password\"\\s*:\\s*\"[^\"]*\"", "\"password\":\"***\"")
                .replaceAll("\"token\"\\s*:\\s*\"[^\"]*\"",    "\"token\":\"***\"")
                .replaceAll("\"secret\"\\s*:\\s*\"[^\"]*\"",   "\"secret\":\"***\"")
                .replaceAll("\"epc\"\\s*:\\s*\"[^\"]*\"",      "\"epc\":\"[EPC]\"");

            int maxLen = (level == AuditLevel.FULL) ? 2000 : 500;
            return bodyStr.length() > maxLen
                ? bodyStr.substring(0, maxLen) + "...[truncated]"
                : bodyStr;
        } catch (Exception e) {
            return null;
        }
    }
}
