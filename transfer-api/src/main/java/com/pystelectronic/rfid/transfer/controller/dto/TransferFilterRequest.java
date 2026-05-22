package com.pystelectronic.rfid.transfer.controller.dto;

import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;

/**
 * Parámetros de filtrado para GET /api/v1/transfers.
 *
 * Todos los parámetros son opcionales. Si se omiten, retorna todos los traslados
 * paginados ordenados por createdAt DESC.
 *
 * Se usa como @ParameterObject en el controlador para que SpringDoc
 * genere los query params individuales en Swagger UI (en vez de un body JSON).
 */
public record TransferFilterRequest(

        @Parameter(description = "Filtrar por estado del traslado",
                   example = "DISPATCHED")
        String status,

        @Parameter(description = "Código del punto de origen",
                   example = "BODEGA-CENTRAL")
        String originCode,

        @Parameter(description = "Código del punto de destino",
                   example = "TIENDA-B001")
        String destinationCode,

        @Parameter(description = "Prioridad del traslado",
                   example = "HIGH")
        String priority,

        @Parameter(description = "Fecha programada desde (ISO 8601)",
                   example = "2026-04-01T00:00:00-05:00")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime scheduledDateFrom,

        @Parameter(description = "Fecha programada hasta (ISO 8601)",
                   example = "2026-04-30T23:59:59-05:00")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime scheduledDateTo,

        @Parameter(description = "Texto libre: busca en transferCode y carrierId",
                   example = "OT-20260422")
        String search

) {}
