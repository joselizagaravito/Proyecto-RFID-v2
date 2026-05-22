package com.pystelectronic.rfid.readtag.service;

import com.pystelectronic.rfid.readtag.dto.RawReadMessage;
import com.pystelectronic.rfid.readtag.entity.ReadTag;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * MapStruct mapper: convierte RawReadMessage (Kafka) → ReadTag (JPA).
 * Los campos Instant se convierten a LocalDateTime usando la zona del sistema.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ReadTagMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "lastTime", expression = "java(toLocalDateTime(msg.getLastTime()))")
    @Mapping(target = "firstUpdate", expression = "java(toLocalDateTime(msg.getFirstUpdate()))")
    ReadTag toEntity(RawReadMessage msg);

    default LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) return LocalDateTime.now();
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
