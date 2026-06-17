package com.pystelectronic.rfid.transfer.controller.dto;

import java.util.UUID;

/**
 * Respuesta a una lectura RFID en sesión de pallet.
 *
 * resultType:
 *   PALLET_OPENED    → se leyó un pallet, queda activo para los LPN siguientes
 *   PALLET_REUSED    → el pallet ya existía (mismo EPC), se reactivó
 *   LPN_ADDED        → LPN asociado al pallet activo
 *   LPN_REUSED       → el LPN ya existía (mismo EPC), no se duplicó
 *   LPN_REJECTED     → se leyó un LPN sin pallet activo → rechazado
 *
 * Sprint 9 · Pystelectronic
 */
public record RfidSessionReadResponse(
        String  resultType,
        String  message,
        String  epc,
        // Datos del pallet activo (si aplica)
        UUID    palletId,
        String  palletCode,
        // Datos del LPN creado (si aplica)
        UUID    lpnId,
        String  lpnCode,
        // Contexto de sesión
        UUID    transferId,
        String  portalId,
        Integer palletLpnCount
) {
    public static RfidSessionReadResponse palletOpened(
            UUID palletId, String palletCode, String epc,
            UUID transferId, String portalId, boolean reused) {
        return new RfidSessionReadResponse(
                reused ? "PALLET_REUSED" : "PALLET_OPENED",
                reused ? "Pallet reactivado — listo para recibir LPNs"
                       : "Pallet abierto — listo para recibir LPNs",
                epc, palletId, palletCode, null, null,
                transferId, portalId, 0);
    }

    public static RfidSessionReadResponse lpnAdded(
            UUID palletId, String palletCode, UUID lpnId, String lpnCode,
            String epc, UUID transferId, String portalId, int count, boolean reused) {
        return new RfidSessionReadResponse(
                reused ? "LPN_REUSED" : "LPN_ADDED",
                reused ? "LPN ya registrado en este pallet"
                       : "LPN agregado al pallet activo",
                epc, palletId, palletCode, lpnId, lpnCode,
                transferId, portalId, count);
    }

    public static RfidSessionReadResponse lpnRejected(
            String epc, String portalId) {
        return new RfidSessionReadResponse(
                "LPN_REJECTED",
                "No hay pallet activo — lee un pallet antes de los LPN",
                epc, null, null, null, null,
                null, portalId, 0);
    }
}
