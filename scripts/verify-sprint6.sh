#!/bin/bash
# ══════════════════════════════════════════════════════════════════
# verify-sprint6.sh — Verificación completa del Sprint 6
# Prueba PKCE indirectamente via direct-grants (Keycloak permite esto en rfid-frontend)
# ══════════════════════════════════════════════════════════════════

KC="http://localhost:8180/auth/realms/rfid-realm/protocol/openid-connect/token"
API="http://localhost:8080"
NGINX="http://localhost"
PASS=0; FAIL=0

ok()  { echo "   ✅ $1"; PASS=$((PASS+1)); }
fail(){ echo "   ❌ $1"; FAIL=$((FAIL+1)); }
sep() { echo ""; echo "▶ TEST $1: $2"; }

# ── Helper: obtener token via password grant ──────────────────
get_token() {
  curl -sf -X POST "$KC" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password&client_id=rfid-frontend&username=$1&password=$2&scope=openid" \
    2>/dev/null | python3 -c "
import sys,json
try:
  d=json.load(sys.stdin)
  print(d.get('access_token',''))
except: print('')
" 2>/dev/null
}

echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║  Verificación Sprint 6 — RBAC + Auditoría           ║"
echo "╚══════════════════════════════════════════════════════╝"

sep 1 "Servicios básicos UP"
curl -sf "http://localhost:8180/auth/health/ready" > /dev/null && ok "Keycloak :8180" || fail "Keycloak :8180"
curl -sf "$API/actuator/health" > /dev/null && ok "Transfer API :8080" || fail "Transfer API :8080"
curl -sf "http://localhost:8084/actuator/health" > /dev/null && ok "Audit Service :8084" || fail "Audit Service :8084"
curl -sf "$NGINX/auth/health/ready" > /dev/null && ok "Nginx → /auth/" || fail "Nginx → /auth/"

sep 2 "Obtener token como ADMIN"
ADMIN_TK=$(get_token "admin.rfid" "Admin@2026!")
[ -n "$ADMIN_TK" ] && ok "Token admin.rfid obtenido" || fail "No se pudo obtener token admin.rfid"

sep 3 "Obtener token como OPERATOR"
OPER_TK=$(get_token "operador.almacen" "Oper@2026!")
[ -n "$OPER_TK" ] && ok "Token operador.almacen obtenido" || fail "No se pudo obtener token"

sep 4 "Obtener token como READER"
READER_TK=$(get_token "auditor.rfid" "Audit@2026!")
[ -n "$READER_TK" ] && ok "Token auditor.rfid obtenido" || fail "No se pudo obtener token"

sep 5 "GET sin token → debe ser 401"
CODE=$(curl -sf -o /dev/null -w "%{http_code}" "$API/api/v1/transfers" 2>/dev/null || echo "000")
[ "$CODE" = "401" ] && ok "Sin token → 401" || fail "Sin token → $CODE (esperado 401)"

sep 6 "ADMIN GET /transfers → 200"
CODE=$(curl -sf -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer $ADMIN_TK" \
  "$API/api/v1/transfers" 2>/dev/null || echo "000")
[ "$CODE" = "200" ] && ok "ADMIN GET transfers → 200" || fail "ADMIN GET transfers → $CODE"

sep 7 "READER GET /transfers → 200"
CODE=$(curl -sf -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer $READER_TK" \
  "$API/api/v1/transfers" 2>/dev/null || echo "000")
[ "$CODE" = "200" ] && ok "READER GET transfers → 200" || fail "READER GET transfers → $CODE"

sep 8 "READER POST /transfers → debe ser 403"
CODE=$(curl -sf -o /dev/null -w "%{http_code}" \
  -X POST "$API/api/v1/transfers" \
  -H "Authorization: Bearer $READER_TK" \
  -H "Content-Type: application/json" \
  -d '{"originCode":"X","destinationCode":"Y","scheduledDate":"2026-06-01T10:00:00-05:00","priority":"NORMAL"}' \
  2>/dev/null || echo "000")
[ "$CODE" = "403" ] && ok "RBAC: READER POST → 403 ✔" || fail "RBAC: READER POST → $CODE (esperado 403)"

sep 9 "OPERATOR POST /transfers → 201"
CODE=$(curl -sf -o /dev/null -w "%{http_code}" \
  -X POST "$API/api/v1/transfers" \
  -H "Authorization: Bearer $OPER_TK" \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: VERIFY-SPRINT6-$(date +%s)" \
  -H "X-Correlation-Id: $(cat /proc/sys/kernel/random/uuid 2>/dev/null || echo 'test-id')" \
  -d "{\"originCode\":\"VERIFY\",\"destinationCode\":\"TEST\",
       \"scheduledDate\":\"2026-06-01T10:00:00-05:00\",\"priority\":\"LOW\"}" \
  2>/dev/null || echo "000")
[ "$CODE" = "201" ] && ok "OPERATOR POST transfer → 201" || fail "OPERATOR POST transfer → $CODE (esperado 201)"

sep 10 "Audit topic en Kafka"
# Verificar que exista el topic transfer.events (donde van los eventos de auditoría)
TOPICS=$(docker exec rfid-kafka kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null || echo "")
echo "$TOPICS" | grep -q "transfer.events" && ok "Topic transfer.events existe" || fail "Topic transfer.events no encontrado"

sep 11 "Registros de auditoría en BD"
# Esperar 5 segundos para que el audit-service procese
sleep 5
COUNT=$(docker exec rfid-postgres psql -U rfid_app -d rfid_transfers -t -c \
  "SELECT COUNT(*) FROM audit_log;" 2>/dev/null | tr -d ' ' || echo "?")
[ "$COUNT" != "?" ] && [ "$COUNT" -gt "0" ] 2>/dev/null && \
  ok "audit_log tiene $COUNT registro(s)" || \
  fail "audit_log vacío o tabla no existe (conteo: $COUNT)"

echo ""
echo "──────────────────────────────────────────────────────"
echo "  Resultado: ✅ $PASS pasaron · ❌ $FAIL fallaron"
echo ""
[ $FAIL -eq 0 ] \
  && echo "  🎉 Sprint 6 verificado correctamente" \
  || echo "  ⚠️  Revisar los fallos antes de dar por cerrado el sprint"
echo "──────────────────────────────────────────────────────"
