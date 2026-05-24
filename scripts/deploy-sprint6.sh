#!/bin/bash
# ══════════════════════════════════════════════════════════════════
# deploy-sprint6.sh — Deploy Sprint 6 en servidor 38.253.180.55
#
# Pre-condiciones:
#   - Sprints 1-5 funcionando
#   - Nginx NATIVO corriendo en puerto 80
#   - Docker Compose en /home/joseliza/app/rfid-backend
#   - Acceso SSH como joseliza
#
# Ejecutar: bash deploy-sprint6.sh
# ══════════════════════════════════════════════════════════════════

set -e

APP_DIR="/home/joseliza/app/rfid-backend"
WEB_DIR="/var/www/rfid"
NGINX_SITES="/etc/nginx/sites-available"
NGINX_ENABLED="/etc/nginx/sites-enabled"

echo ""
echo "╔══════════════════════════════════════════════╗"
echo "║  RFID Traslados — Deploy Sprint 6            ║"
echo "║  Keycloak activo + audit-service + RBAC      ║"
echo "╚══════════════════════════════════════════════╝"
echo ""

# ── 1. Pull del código ─────────────────────────────────────────
echo "▶ [1/9] Actualizando código desde GitHub..."
cd "$APP_DIR"
git pull origin master
echo "   ✅ Código actualizado (commit: $(git rev-parse --short HEAD))"

# ── 2. Verificar realm-rfid.json ──────────────────────────────
echo "▶ [2/9] Verificando keycloak/realm-rfid.json..."
if [ ! -f "$APP_DIR/keycloak/realm-rfid.json" ]; then
  echo "   ❌ FALTA: keycloak/realm-rfid.json"
  echo "   Cópialo y vuelve a ejecutar el script"
  exit 1
fi
echo "   ✅ realm-rfid.json encontrado"

# ── 3. Aplicar migración V8 manualmente (igual que V7) ─────────
echo "▶ [3/9] Aplicando migración V8 (audit_log)..."

# Verificar si ya está aplicada
V8_EXISTE=$(docker exec rfid-postgres psql -U rfid_app -d rfid_transfers -t -c \
  "SELECT COUNT(*) FROM flyway_schema_history WHERE version='8';" 2>/dev/null | tr -d ' ' || echo "0")

if [ "$V8_EXISTE" = "1" ]; then
  echo "   ⏭️  V8 ya está aplicada — saltando"
else
  echo "   Aplicando SQL..."
  docker exec -i rfid-postgres psql -U rfid_app -d rfid_transfers \
    < "$APP_DIR/audit-service/src/main/resources/db/migration/V8__create_audit_log.sql"

  echo "   Marcando en flyway_schema_history..."
  docker exec rfid-postgres psql -U rfid_app -d rfid_transfers -c "
    INSERT INTO flyway_schema_history
      (installed_rank, version, description, type, script,
       checksum, installed_by, installed_on, execution_time, success)
    VALUES (
      (SELECT COALESCE(MAX(installed_rank),0)+1 FROM flyway_schema_history),
      '8', 'create audit log', 'SQL', 'V8__create_audit_log.sql',
      0, 'rfid_app', now(), 100, true
    );" > /dev/null

  echo "   ✅ V8 aplicada y registrada"
fi

# ── 4. Detener servicios Java para rebuild ────────────────────
echo "▶ [4/9] Deteniendo servicios Java..."
docker compose stop transfer-api read-tag-service validation-service 2>/dev/null || true
echo "   ✅ Servicios detenidos"

# ── 5. Build servicios Java (perfil prod ahora) ───────────────
echo "▶ [5/9] Compilando servicios Java con perfil prod..."
docker compose build --no-cache transfer-api read-tag-service validation-service audit-service
echo "   ✅ Build completado"

# ── 6. Levantar stack completo ────────────────────────────────
echo "▶ [6/9] Levantando stack completo..."
docker compose up -d
echo "   ✅ Stack iniciado"

# ── 7. Esperar a que Keycloak esté listo ─────────────────────
echo "▶ [7/9] Esperando a Keycloak (máx 150 segundos)..."
MAX=150; E=0
until curl -sf "http://localhost:8180/auth/health/ready" > /dev/null 2>&1; do
  [ $E -ge $MAX ] && { echo "   ⚠️  Keycloak tardó más de ${MAX}s. Ver: docker compose logs --tail=30 keycloak"; break; }
  sleep 5; E=$((E+5))
  printf "."
done
echo ""
echo "   ✅ Keycloak respondiendo"

# ── 8. Actualizar Nginx nativo ────────────────────────────────
echo "▶ [8/9] Actualizando configuración Nginx..."
if [ -f "$APP_DIR/nginx/rfid-traslados.nginx.conf" ]; then
  sudo cp "$APP_DIR/nginx/rfid-traslados.nginx.conf" "$NGINX_SITES/rfid-traslados"

  # Crear symlink si no existe
  if [ ! -L "$NGINX_ENABLED/rfid-traslados" ]; then
    sudo ln -sf "$NGINX_SITES/rfid-traslados" "$NGINX_ENABLED/rfid-traslados"
  fi

  # Validar y recargar Nginx
  sudo nginx -t && sudo nginx -s reload
  echo "   ✅ Nginx recargado"
else
  echo "   ⚠️  nginx/rfid-traslados.nginx.conf no encontrado — Nginx no actualizado"
fi

# ── 9. Desplegar frontend v15 ─────────────────────────────────
echo "▶ [9/9] Desplegando Frontend v15..."
if [ -f "$APP_DIR/frontend/rfid-transfers-app_15.html" ]; then
  sudo mkdir -p "$WEB_DIR"
  sudo cp "$APP_DIR/frontend/rfid-transfers-app_15.html" "$WEB_DIR/rfid-transfers-app.html"
  sudo chown -R www-data:www-data "$WEB_DIR"
  echo "   ✅ Frontend v15 desplegado en $WEB_DIR"
else
  echo "   ⚠️  frontend/rfid-transfers-app_15.html no encontrado"
fi

# ── Verificación rápida ───────────────────────────────────────
echo ""
sleep 8
echo "─── Estado de contenedores ─────────────────────────"
docker compose ps --format "table {{.Name}}\t{{.Status}}"

echo ""
echo "─── Verificación de servicios ──────────────────────"
check() {
  local n=$1 u=$2
  curl -sf "$u" > /dev/null 2>&1 && echo "✅ $n" || echo "❌ $n ($u)"
}
check "Keycloak"        "http://localhost:8180/auth/health/ready"
check "Transfer API"    "http://localhost:8080/actuator/health"
check "Read Tag Svc"    "http://localhost:8082/actuator/health"
check "Validation Svc"  "http://localhost:8083/actuator/health"
check "Audit Service"   "http://localhost:8084/actuator/health"
check "Realtime Svc"    "http://localhost:3001/health"
check "Alert Service"   "http://localhost:3002/health"
check "Nginx Frontend"  "http://localhost/nginx-health"
check "Nginx /auth"     "http://localhost/auth/health/ready"

echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║  Sprint 6 desplegado                                 ║"
echo "║                                                      ║"
echo "║  🌐 App:      http://38.253.180.55/                  ║"
echo "║  🔐 Keycloak: http://38.253.180.55/auth              ║"
echo "║              admin / admin123                        ║"
echo "║                                                      ║"
echo "║  Usuarios:                                           ║"
echo "║  admin.rfid     / Admin@2026!   → ADMIN              ║"
echo "║  operador.almacen / Oper@2026!  → OPERATOR           ║"
echo "║  operador.tienda  / Tienda@2026! → OPERATOR          ║"
echo "║  auditor.rfid   / Audit@2026!   → READER             ║"
echo "╚══════════════════════════════════════════════════════╝"
