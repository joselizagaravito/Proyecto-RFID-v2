package com.pystelectronic.rfid.readtag.service;

import com.pystelectronic.rfid.readtag.dto.RawReadMessage;
import com.pystelectronic.rfid.readtag.dto.ReadTagRequest;
import com.pystelectronic.rfid.readtag.dto.ReadTagResponse;
import com.pystelectronic.rfid.readtag.entity.ReadTag;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Mapper MapStruct para conversiones entre DTOs y entidad ReadTag.
 *
 * Sprint 4: agrega mapeos para REST (ReadTagRequest ↔ ReadTag,
 * ReadTag → ReadTagResponse, ReadTagRequest → RawReadMessage).
 */
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface ReadTagMapper {

    // ── Kafka → Entity ────────────────────────────────────────────────────────

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "lastTime",    expression = "java(toLocalDateTime(msg.getLastTime()))")
    @Mapping(target = "firstUpdate", expression = "java(toLocalDateTime(msg.getFirstUpdate()))")
    ReadTag toEntity(RawReadMessage msg);

    // ── REST Request → Entity ─────────────────────────────────────────────────

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "lastTime",    expression = "java(toLocalDateTime(req.getLastTime()))")
    @Mapping(target = "firstUpdate", expression = "java(toLocalDateTime(req.getFirstUpdate()))")
    ReadTag toEntity(ReadTagRequest req);

    // ── REST Request → RawReadMessage (reutiliza flujo Kafka) ─────────────────

    RawReadMessage toRawReadMessage(ReadTagRequest req);

    // ── Entity → Response ─────────────────────────────────────────────────────

    ReadTagResponse toResponse(ReadTag entity);

    // ── PUT: actualizar entidad existente desde request ───────────────────────

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "lastTime",    expression = "java(toLocalDateTime(req.getLastTime()))")
    @Mapping(target = "firstUpdate", expression = "java(toLocalDateTime(req.getFirstUpdate()))")
    void updateEntity(@MappingTarget ReadTag entity, ReadTagRequest req);

    // ── Conversión de tipos ────────────────────────────────────────────────────

    default LocalDateTime toLocalDateTime(Instant instant) {
        return instant != null
                ? LocalDateTime.ofInstant(instant, ZoneOffset.UTC)
                : null;
    }
}
