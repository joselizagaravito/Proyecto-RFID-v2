import express from "express";
import crypto from "crypto";

const app  = express();
const PORT = parseInt(process.env.PORT || "3003", 10);
const HMAC_SECRET = process.env.WEBHOOK_HMAC_SECRET || "rfid-hmac-secret-dev";

const receivedAlerts = [];

app.use(express.json());

app.post("/webhook", (req, res) => {
  const bodyStr   = JSON.stringify(req.body);
  const sigHeader = req.headers["x-rfid-signature"] || "";
  const alertId   = req.headers["x-rfid-alert-id"]  || "unknown";

  const expectedSig = crypto
    .createHmac("sha256", HMAC_SECRET)
    .update(bodyStr)
    .digest("hex");

  const sigValid = crypto.timingSafeEqual(
    Buffer.from(sigHeader.padEnd(64, "0")),
    Buffer.from(expectedSig.padEnd(64, "0"))
  );

  const entry = {
    receivedAt: new Date().toISOString(),
    alertId,
    alertType:  req.body.alertType || "UNKNOWN",
    transferId: req.body.transferId,
    portalId:   req.body.portalId,
    signatureOk: sigValid,
    payload:    req.body,
  };

  receivedAlerts.push(entry);

  console.log("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
  console.log(`[${entry.receivedAt}] ALERTA RECIBIDA`);
  console.log(`  AlertId:   ${alertId}`);
  console.log(`  AlertType: ${entry.alertType}`);
  console.log(`  Transfer:  ${entry.transferId || "—"}`);
  console.log(`  HMAC:      ${sigValid ? "✓ válida" : "✗ INVÁLIDA"}`);
  console.log(`  Payload:   ${JSON.stringify(req.body, null, 2)}`);
  console.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

  if (!sigValid) return res.status(401).json({ error: "Firma HMAC inválida" });

  res.status(200).json({ status: "received", alertId, processedAt: new Date().toISOString() });
});

app.get("/alerts",      (_req, res) => res.json({ total: receivedAlerts.length, alerts: receivedAlerts }));
app.get("/alerts/last", (_req, res) => res.json(receivedAlerts[receivedAlerts.length - 1] || { message: "Sin alertas aun" }));
app.delete("/alerts",   (_req, res) => { receivedAlerts.length = 0; res.json({ message: "Historial limpiado" }); });
app.get("/health",      (_req, res) => res.json({ status: "UP", service: "webhook-mock", alertsCount: receivedAlerts.length, timestamp: new Date().toISOString() }));

app.listen(PORT, () => {
  console.log(`[webhook-mock] Escuchando en :${PORT}`);
  console.log("  POST   /webhook      <- recibe alertas");
  console.log("  GET    /alerts       <- historial");
  console.log("  GET    /alerts/last  <- ultima alerta");
  console.log("  DELETE /alerts       <- limpiar");
  console.log("  GET    /health       <- health check");
});
