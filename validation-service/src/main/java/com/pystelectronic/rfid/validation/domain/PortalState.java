package com.pystelectronic.rfid.validation.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * Estado de un portal RFID almacenado en Redis.
 *
 * Clave Redis: portal:state:{portalId}
 * TTL:         ninguno (persiste mientras el traslado esté activo)
 *
 * Este objeto es escrito por:
 *   - transfer-api cuando se asigna un portal a un traslado
 *   - validation-service cuando llega cada lectura (incrementa readCount)
 *
 * Leído por:
 *   - validation-service para obtener el transferId activo del portal
 *   - realtime-service  para push del estado al dashboard
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PortalState {

    private String portalId;
    private String transferId;      // traslado activo asignado al portal (null = sin traslado)
    private String status;          // ACTIVE | IDLE | CLOSED
    private int readCount;          // lecturas válidas recibidas
    private int expectedLpns;       // total LPNs esperados en el traslado
    private int anomalyCount;       // lecturas inválidas / EPCs inesperados
    private Instant lastReadAt;
    private Instant assignedAt;     // cuando se asignó el traslado al portal

    // ── Constructores ──────────────────────────────────────────

    public PortalState() {}

    public static PortalState idle(String portalId) {
        var s = new PortalState();
        s.portalId    = portalId;
        s.status      = "IDLE";
        s.readCount   = 0;
        s.anomalyCount = 0;
        return s;
    }

    // ── Mutaciones de negocio ──────────────────────────────────

    public void incrementRead(Instant readAt) {
        this.readCount++;
        this.lastReadAt = readAt;
    }

    public void incrementAnomaly(Instant readAt) {
        this.anomalyCount++;
        this.lastReadAt = readAt;
    }

    public boolean hasActiveTransfer() {
        return transferId != null && "ACTIVE".equals(status);
    }

    // ── Getters / Setters ──────────────────────────────────────

    public String getPortalId()          { return portalId; }
    public void setPortalId(String v)    { this.portalId = v; }

    public String getTransferId()        { return transferId; }
    public void setTransferId(String v)  { this.transferId = v; }

    public String getStatus()            { return status; }
    public void setStatus(String v)      { this.status = v; }

    public int getReadCount()            { return readCount; }
    public void setReadCount(int v)      { this.readCount = v; }

    public int getExpectedLpns()         { return expectedLpns; }
    public void setExpectedLpns(int v)   { this.expectedLpns = v; }

    public int getAnomalyCount()         { return anomalyCount; }
    public void setAnomalyCount(int v)   { this.anomalyCount = v; }

    public Instant getLastReadAt()       { return lastReadAt; }
    public void setLastReadAt(Instant v) { this.lastReadAt = v; }

    public Instant getAssignedAt()       { return assignedAt; }
    public void setAssignedAt(Instant v) { this.assignedAt = v; }
}
