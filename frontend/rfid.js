/* ══════════════════════════════════════════════════════════════
   RFID Traslados v19 — Sprint 9
   T4: CRUD pallet_tags · T5: Sync desde servidor
   Pystelectronic · Ing. José Hernán Liza Garavito
   ══════════════════════════════════════════════════════════════ */

const IS_LOCAL = window.location.hostname === 'localhost'
              || window.location.hostname === '127.0.0.1';

const BASE_HOST = IS_LOCAL ? 'http://localhost' : 'http://38.253.180.55';

const CONFIG = {
  KC_BASE:      `${BASE_HOST}/auth`,
  REALM:        'rfid-realm',
  CLIENT_ID:    'rfid-frontend',
  REDIRECT_URI: window.location.origin + window.location.pathname,
  API:          `${BASE_HOST}/api/v1`,
  WS_HOST:      BASE_HOST
};

// ── Estado global ──────────────────────────────────────────────
let S = {
  accessToken: null, refreshToken: null,
  username: null, roles: [], expiresAt: 0
};

// Estado del Dashboard
let portalesRT = {};   // portalId → { readCount, expectedLpns, lastUpdate }
let wsConnected = false;
let kpiIntervalId = null;

// Estado de sesión de pallet (Sprint 9)
let sessionPalletActivo = null;  // { palletId, palletCode, lpnCount, portalId }
let sessionLpns = [];            // lista de LPNs leídos en la sesión actual

// Estado Despacho
let dispatchTransfer = null;  // traslado seleccionado para despacho
let dispatchValidaciones = []; // validaciones realizadas en paso 2

// Estado Recepción
let receiptTransfer = null;  // traslado seleccionado para recepción

/* ══════════════════════════════════════════════════════════════
   PKCE + Auth
═══════════════════════════════════════════════════════════════ */
function genVerifier() {
  const a = new Uint8Array(64);
  crypto.getRandomValues(a);
  return btoa(String.fromCharCode(...a)).replace(/\+/g,'-').replace(/\//g,'_').replace(/=/g,'');
}
async function genChallenge(v) {
  // crypto.subtle solo funciona en HTTPS
  // En HTTP usamos CryptoJS cargado en index.html via CDN
  if (crypto.subtle) {
    const h = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(v));
    return btoa(String.fromCharCode(...new Uint8Array(h)))
      .replace(/\+/g,'-').replace(/\//g,'_').replace(/=/g,'');
  }
  // Fallback HTTP: CryptoJS (script tag en index.html)
  const wordArray = CryptoJS.SHA256(v);
  const bytes = [];
  wordArray.words.forEach(w => {
    bytes.push((w>>>24)&0xff,(w>>>16)&0xff,(w>>>8)&0xff,w&0xff);
  });
  return btoa(String.fromCharCode(...bytes.slice(0, wordArray.sigBytes)))
    .replace(/\+/g,'-').replace(/\//g,'_').replace(/=/g,'');
}

function genState() {
  const a = new Uint8Array(16);
  crypto.getRandomValues(a);
  return Array.from(a, b=>b.toString(16).padStart(2,'0')).join('');
}

async function iniciarLogin() {
  document.getElementById('loginBtn').disabled = true;
  document.getElementById('loginBtn').textContent = 'Redirigiendo a Keycloak...';
  const verifier  = genVerifier();
  const challenge = await genChallenge(verifier);
  const state     = genState();
  sessionStorage.setItem('pkce_verifier', verifier);
  sessionStorage.setItem('pkce_state', state);
  // Backup en localStorage — en HTTP Chrome puede limpiar sessionStorage durante redirect
  localStorage.setItem('pkce_verifier_bk', verifier);
  localStorage.setItem('pkce_state_bk', state);
  const params = new URLSearchParams({
    client_id: CONFIG.CLIENT_ID, redirect_uri: CONFIG.REDIRECT_URI,
    response_type: 'code', scope: 'openid profile email roles',
    state, code_challenge: challenge, code_challenge_method: 'S256'
  });
  window.location.href = `${CONFIG.KC_BASE}/realms/${CONFIG.REALM}/protocol/openid-connect/auth?${params}`;
}

async function procesarCallback() {
  const p = new URLSearchParams(window.location.search);
  const code = p.get('code'), state = p.get('state'), error = p.get('error');
  if (error) { mostrarError(p.get('error_description') || 'Error de autenticación Keycloak'); return; }
  if (!code) return;
  const savedState    = sessionStorage.getItem('pkce_state')    || localStorage.getItem('pkce_state_bk');
  const savedVerifier = sessionStorage.getItem('pkce_verifier') || localStorage.getItem('pkce_verifier_bk');
  if (state !== savedState) {
    mostrarError('Estado PKCE inválido — recarga la página'); return;
  }
  window.history.replaceState({}, '', window.location.pathname);
  try {
    await canjearCodigo(code, savedVerifier);
  } catch(e) {
    mostrarError('Error al obtener token: ' + e.message);
  }
}

async function canjearCodigo(code, verifier) {
  const res = await fetch(
    `${CONFIG.KC_BASE}/realms/${CONFIG.REALM}/protocol/openid-connect/token`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'authorization_code', client_id: CONFIG.CLIENT_ID,
        redirect_uri: CONFIG.REDIRECT_URI, code, code_verifier: verifier
      })
    }
  );
  if (!res.ok) {
    const err = await res.json().catch(()=>({}));
    throw new Error(err.error_description || `HTTP ${res.status}`);
  }
  guardarSesion(await res.json());
  lanzarApp();
}

function guardarSesion(data) {
  S.accessToken  = data.access_token;
  S.refreshToken = data.refresh_token;
  S.expiresAt    = Date.now() + (data.expires_in - 30) * 1000;
  const payload  = JSON.parse(atob(data.access_token.split('.')[1]));
  S.username     = payload.preferred_username || payload.sub;
  S.roles        = (payload.realm_access?.roles || []).filter(r => r.startsWith('ROLE_'));
  sessionStorage.removeItem('pkce_verifier');
  sessionStorage.removeItem('pkce_state');
  localStorage.removeItem('pkce_verifier_bk');
  localStorage.removeItem('pkce_state_bk');
}

setInterval(async () => {
  if (S.accessToken && Date.now() > S.expiresAt) await renovarToken();
}, 60000);

async function renovarToken() {
  if (!S.refreshToken) { cerrarSesion(); return; }
  try {
    const res = await fetch(
      `${CONFIG.KC_BASE}/realms/${CONFIG.REALM}/protocol/openid-connect/token`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
          grant_type: 'refresh_token', client_id: CONFIG.CLIENT_ID,
          refresh_token: S.refreshToken
        })
      }
    );
    const data = await res.json();
    if (data.access_token) guardarSesion(data);
    else cerrarSesion();
  } catch { cerrarSesion(); }
}

function lanzarApp() {
  document.getElementById('login-screen').style.display = 'none';
  document.getElementById('app').style.display = 'block';

  document.getElementById('user-display').textContent = S.username;
  const roleName = (S.roles[0] || 'UNKNOWN').replace('ROLE_', '');
  const badge = document.getElementById('role-badge');
  badge.textContent = roleName;
  badge.className   = `role-badge role-${roleName}`;

  const isAdmin  = S.roles.includes('ROLE_ADMIN');
  const canWrite = S.roles.includes('ROLE_ADMIN') || S.roles.includes('ROLE_OPERATOR');

  if (isAdmin) document.querySelector('[data-tab="auditoria"]').classList.remove('oculto');
  if (canWrite) document.querySelector('[data-tab="pallet-tags"]').classList.remove('oculto');
  if (!canWrite) document.getElementById('card-crear-traslado').style.display = 'none';
  if (!canWrite) document.getElementById('card-pt-nuevo').style.display = 'none';
  // Ocultar despacho/recepción si no tiene permisos
  if (!canWrite) {
    document.querySelector('[data-tab="despacho"]').style.display = 'none';
    document.querySelector('[data-tab="recepcion"]').style.display = 'none';
  }

  // Inicializar
  cargarKPIs();
  cargarLecturas();
  cargarPalletTags();
  conectarWS();
  iniciarSessionPanel();

  // Auto-refresh KPIs cada 30 segundos
  if (kpiIntervalId) clearInterval(kpiIntervalId);
  kpiIntervalId = setInterval(cargarKPIs, 30000);
}

function cerrarSesion() {
  if (kpiIntervalId) clearInterval(kpiIntervalId);
  const logoutUrl = `${CONFIG.KC_BASE}/realms/${CONFIG.REALM}/protocol/openid-connect/logout?`
    + new URLSearchParams({ client_id: CONFIG.CLIENT_ID, post_logout_redirect_uri: CONFIG.REDIRECT_URI });
  S = { accessToken:null, refreshToken:null, username:null, roles:[], expiresAt:0 };
  window.location.href = logoutUrl;
}

function mostrarError(msg) {
  const el = document.getElementById('loginError');
  el.textContent = msg; el.style.display = 'block';
  const btn = document.getElementById('loginBtn');
  btn.disabled = false; btn.textContent = '🔐 Ingresar con Keycloak';
}

/* ══════════════════════════════════════════════════════════════
   HTTP helpers
═══════════════════════════════════════════════════════════════ */
function generateUUID() {
  // crypto.randomUUID solo funciona en HTTPS — fallback para HTTP
  if (crypto.randomUUID) return crypto.randomUUID();
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = Math.random() * 16 | 0;
    return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
  });
}

function buildHeaders(method = 'GET', extra = {}) {
  const h = { 'Content-Type': 'application/json' };
  if (S.accessToken) h['Authorization'] = `Bearer ${S.accessToken}`;
  if (method !== 'GET') h['X-Correlation-Id'] = generateUUID();
  return { ...h, ...extra };
}

async function api(url, options = {}) {
  const method  = options.method || 'GET';
  const headers = buildHeaders(method, options.headers || {});
  try {
    const res = await fetch(url, { ...options, headers });
    if (res.status === 401 && S.refreshToken) {
      await renovarToken();
      if (S.accessToken) {
        return fetch(url, { ...options, headers: buildHeaders(method, options.headers || {}) });
      }
      cerrarSesion(); return null;
    }
    return res;
  } catch(e) {
    console.warn('API error:', e);
    return null;
  }
}

/* ══════════════════════════════════════════════════════════════
   Tabs + Helpers
═══════════════════════════════════════════════════════════════ */
function showTab(tab) {
  document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));
  document.querySelectorAll('.nav-tab').forEach(t => t.classList.remove('active'));
  document.getElementById('tab-'+tab).classList.add('active');
  document.querySelector(`[data-tab="${tab}"]`).classList.add('active');

  // Lazy load al abrir tab
  if (tab === 'despacho')  cargarTrasladosDespacho();
  if (tab === 'recepcion') cargarTrasladosRecepcion();
  if (tab === 'traslados') cargarTraslados();
}

const badge  = s => `<span class="badge badge-${(s||'').toLowerCase()}">${s||'—'}</span>`;
const msgEl  = (type, text) => `<div class="alert alert-${type}">${text}</div>`;
const fmtDate = d => d ? new Date(d).toLocaleString('es-PE') : '—';
const fmtDateShort = d => d ? new Date(d).toLocaleDateString('es-PE') : '—';

/* ══════════════════════════════════════════════════════════════
   DASHBOARD KPIs
═══════════════════════════════════════════════════════════════ */
async function cargarKPIs() {
  const [resT, resR] = await Promise.all([
    api(`${CONFIG.API}/transfers?size=100&sort=createdAt,desc`),
    api(`${CONFIG.API}/read-tags?size=1`)
  ]);

  if (resT && resT.ok) {
    const data  = await resT.json();
    const items = data.content || [];
    const total = data.totalElements || 0;

    // KPI cards
    document.getElementById('kpi-total').textContent = total;
    const transit   = items.filter(t => t.status === 'IN_TRANSIT').length;
    const dispatched = items.filter(t => t.status === 'DISPATCHED').length;
    const received  = items.filter(t => t.status === 'RECEIVED').length;
    const draft     = items.filter(t => t.status === 'DRAFT').length;
    const prepared  = items.filter(t => t.status === 'PREPARED').length;
    const cancelled = items.filter(t => t.status === 'CANCELLED').length;

    document.getElementById('kpi-transit').textContent  = transit + dispatched;
    document.getElementById('kpi-received').textContent = received;

    // Barra RT
    document.getElementById('rt-transit').textContent = transit + dispatched;

    // Breakdown por estado
    document.getElementById('sb-draft').textContent = draft;
    document.getElementById('sb-prep').textContent  = prepared;
    document.getElementById('sb-disp').textContent  = dispatched;
    document.getElementById('sb-tran').textContent  = transit;
    document.getElementById('sb-recv').textContent  = received;

    // Gráfico de barras SVG
    renderBarChart(
      ['DRAFT','PREP','DISPA','TRANS','RECV','CANC'],
      [draft, prepared, dispatched, transit, received, cancelled],
      ['#3730A3','#C2410C','#0369A1','#1D4ED8','#15803D','#991B1B']
    );

    // Tabla traslados recientes con acciones rápidas
    document.getElementById('kpi-transfers').innerHTML = items.slice(0,10).map(t => `
      <tr>
        <td class="txt-mono"><b>${t.transferCode||'—'}</b></td>
        <td>${t.originCode||'—'}</td>
        <td>${t.destinationCode||'—'}</td>
        <td>${badge(t.status)}</td>
        <td>${t.priority||'—'}</td>
        <td>${t.totalPallets != null ? t.totalPallets : '—'}</td>
        <td>${fmtDateShort(t.createdAt)}</td>
        <td>${accionRapida(t)}</td>
      </tr>`).join('') ||
      '<tr><td colspan="8" class="no-data">Sin traslados</td></tr>';
  }

  if (resR && resR.ok) {
    const dr = await resR.json();
    const totalReads = dr.totalElements || 0;
    document.getElementById('kpi-reads').textContent = totalReads;
    document.getElementById('rt-reads').textContent  = totalReads;

    // Eficiencia calculada (reads / total transfers si hay traslados)
    const totalT = parseInt(document.getElementById('kpi-total').textContent) || 1;
    const efic = Math.min(100, Math.round((totalReads / Math.max(totalT, 1)) * 10));
    document.getElementById('kpi-eficiencia').textContent = efic;
    document.getElementById('kpi-efic-d').textContent = `${totalReads} lecturas / ${totalT} traslados`;
  }

  // Alertas (usamos un valor aproximado desde ws)
  const alrtEl = document.getElementById('kpi-alertas');
  if (alrtEl.textContent === '—') alrtEl.textContent = '0';
}

function accionRapida(t) {
  const btns = [];
  if (t.status === 'PREPARED') {
    btns.push(`<button class="btn btn-sm btn-gold"
      onclick="seleccionarDespacho('${t.transferId}','${t.transferCode}','${t.originCode}','${t.destinationCode}')">
      🚢 Despachar</button>`);
  }
  if (t.status === 'DISPATCHED' || t.status === 'IN_TRANSIT') {
    btns.push(`<button class="btn btn-sm btn-primary"
      onclick="seleccionarRecepcion('${t.transferId}','${t.transferCode}','${t.originCode}','${t.destinationCode}')">
      📥 Recibir</button>`);
  }
  if (t.status === 'RECEIVED') {
    btns.push(`<button class="btn btn-sm btn-secondary"
      onclick="verReconciliacion('${t.transferId}')">
      📊 Reconciliar</button>`);
  }
  btns.push(`<button class="btn btn-sm btn-outline"
    onclick="document.getElementById('p-tid').value='${t.transferId}';showTab('pallets')">
    📦 Pallets</button>`);
  return btns.join(' ');
}

function renderBarChart(labels, values, colors) {
  const svg    = document.getElementById('activity-svg');
  if (!svg) return;
  const max    = Math.max(...values, 1);
  const bw     = 44, gap = 22, padL = 10, padB = 22, h = 100;
  const totalW = padL + labels.length * (bw + gap);
  svg.setAttribute('viewBox', `0 0 ${totalW} ${h}`);

  svg.innerHTML = labels.map((lbl, i) => {
    const bh  = Math.round(((values[i] || 0) / max) * (h - padB - 6));
    const x   = padL + i * (bw + gap);
    const y   = h - padB - bh;
    const val = values[i] || 0;
    return `
      <rect x="${x}" y="${y}" width="${bw}" height="${bh}"
        fill="${colors[i]}" rx="4" opacity=".85"/>
      <text x="${x + bw/2}" y="${y - 3}" font-size="10" fill="${colors[i]}"
        text-anchor="middle" font-weight="700">${val}</text>
      <text x="${x + bw/2}" y="${h - 5}" font-size="8.5" fill="#888"
        text-anchor="middle">${lbl}</text>
    `;
  }).join('');
}

function cargarPortales() {
  const grid = document.getElementById('portal-grid');
  if (Object.keys(portalesRT).length === 0) {
    grid.innerHTML = `<div style="color:var(--muted);font-size:13px;grid-column:1/-1;
      padding:16px;text-align:center">
      Sin datos de portales aún — conecta el WebSocket y envía lecturas RFID para ver portales aquí.
    </div>`;
    return;
  }
  grid.innerHTML = Object.entries(portalesRT).map(([pid, d]) => {
    const pct = d.expectedLpns ? Math.min(100, Math.round((d.readCount / d.expectedLpns) * 100)) : 0;
    const st  = pct >= 100 ? 'full' : pct > 80 ? '' : '';
    const over = d.readCount > d.expectedLpns && d.expectedLpns > 0;
    const statusCls = over ? 'alert' : d.readCount > 0 ? 'active' : 'idle';
    const statusLbl = over ? 'Exceso' : d.readCount > 0 ? 'Activo' : 'Inactivo';
    return `
      <div class="portal-card">
        <div class="portal-header">
          <div class="portal-name">🔵 ${pid}</div>
          <div class="portal-status ${statusCls}">${statusLbl}</div>
        </div>
        <div class="portal-progress">
          <div class="portal-progress-fill ${over?'over':st}"
            style="width:${Math.min(100,pct)}%"></div>
        </div>
        <div class="portal-stats">
          <span>Leídos: <b>${d.readCount}</b></span>
          <span>Esperados: <b>${d.expectedLpns||'?'}</b></span>
          <span>${pct}%</span>
        </div>
        <div style="font-size:11px;color:var(--muted);margin-top:6px">
          Última act: ${d.lastUpdate || '—'}
        </div>
      </div>`;
  }).join('');
}

/* ══════════════════════════════════════════════════════════════
   WebSocket — Socket.IO
═══════════════════════════════════════════════════════════════ */
function conectarWS() {
  const wsEl  = document.getElementById('ws-events');
  const dot1  = document.getElementById('ws-dot');
  const dot2  = document.getElementById('ws-dot2');
  const stBar = document.getElementById('rt-ticker');

  const setDots = (cls) => {
    [dot1, dot2].forEach(d => { if(d) d.className = `rt-dot ${cls}`; });
  };

  try {
    const socket = io(CONFIG.WS_HOST + '/rfid', {
      path: '/socket.io/',
      auth: { token: S.accessToken || '' },
      transports: ['websocket', 'polling']
    });

    socket.on('connect', () => {
      setDots('online');
      wsConnected = true;
      socket.emit('subscribe', { namespace: '/rfid' });
      addWSLine('✅ WebSocket conectado al realtime-service', 'portal');
    });

    socket.on('disconnect', reason => {
      setDots('offline');
      wsConnected = false;
      stBar.textContent = `⚠️ Desconectado (${reason}) — reconectando...`;
    });

    socket.on('connect_error', err => {
      setDots('offline');
      addWSLine(`❌ Error WS: ${err.message}`, 'anomaly');
    });

    socket.on('epc:read', data => {
      const line = `EPC:${data.epcCode} Portal:${data.portalId} → ${data.status}`;
      addWSLine(`[${ts()}] ${line}`, 'epc');
      stBar.textContent = line;
      document.getElementById('rt-reads').textContent =
        (parseInt(document.getElementById('rt-reads').textContent)||0) + 1;
    });

    socket.on('epc:anomaly', data => {
      const line = `⚠️ ALERTA ${data.anomalyType || data.alertType || ''} · EPC:${data.epcCode} Portal:${data.portalId}`;
      addWSLine(`[${ts()}] ${line}`, 'anomaly');
      stBar.textContent = line;
      const cnt = document.getElementById('rt-alerts-count');
      cnt.textContent = (parseInt(cnt.textContent)||0) + 1;
      document.getElementById('kpi-alertas').textContent =
        (parseInt(document.getElementById('kpi-alertas').textContent)||0) + 1;
    });

    socket.on('pallet:opened', data => {
      sessionPalletActivo = {
        palletId: data.palletId, palletCode: data.palletCode,
        lpnCount: data.lpnCount || 0, portalId: data.portalId
      };
      sessionLpns = [];
      renderSessionPanel();
      const line = `📦 Pallet activo: ${data.palletCode} | Portal:${data.portalId}`;
      addWSLine(`[${ts()}] ${line}`, 'portal');
      stBar.textContent = line;
    });

    socket.on('lpn:added', data => {
      if (sessionPalletActivo) sessionPalletActivo.lpnCount = data.lpnCount || 0;
      sessionLpns.unshift({ lpnCode: data.lpnCode, epc: data.epc, ts: ts() });
      if (sessionLpns.length > 50) sessionLpns.pop();
      renderSessionPanel();
      const line = `✅ LPN: ${data.lpnCode} → ${data.palletCode}`;
      addWSLine(`[${ts()}] ${line}`, 'epc');
      stBar.textContent = line;
    });

    socket.on('lpn:rejected', data => {
      renderSessionRejected(data.epc);
      const line = `🚫 LPN rechazado: ${data.epc} — Lee un pallet primero`;
      addWSLine(`[${ts()}] ${line}`, 'anomaly');
      stBar.textContent = line;
      const cnt = document.getElementById('rt-alerts-count');
      if (cnt) cnt.textContent = (parseInt(cnt.textContent)||0) + 1;
    });

    socket.on('portal:state', data => {
      const portals = data.portals || (data.portalId ? [data] : []);
      portals.forEach(p => {
        const pid = p.portalId;
        if (!pid) return;
        portalesRT[pid] = {
          readCount:    parseInt(p.readCount)    || 0,
          expectedLpns: parseInt(p.expectedLpns) || 0,
          lastUpdate:   new Date().toLocaleTimeString('es-PE')
        };
        const line = ` Portal:${pid} ${portalesRT[pid].readCount}/${portalesRT[pid].expectedLpns} LPNs`;
        addWSLine(`[${ts()}] ${line}`, 'portal');
        stBar.textContent = line;
      });
      cargarPortales();
    });

  } catch(e) {
    setDots('offline');
    addWSLine('Socket.IO no disponible: ' + e.message, 'anomaly');
  }
}

function addWSLine(text, type = 'epc') {
  const el   = document.getElementById('ws-events');
  const cls  = type === 'anomaly' ? 'ws-line-anomaly' : type === 'portal' ? 'ws-line-portal' : 'ws-line-epc';
  const line = document.createElement('span');
  line.className = cls;
  line.textContent = text + '\n';
  el.insertBefore(line, el.firstChild);
  // Limitar a 100 líneas
  while (el.children.length > 100) el.removeChild(el.lastChild);
}

function ts() { return new Date().toLocaleTimeString('es-PE'); }

/* ══════════════════════════════════════════════════════════════
   DESPACHO — flujo completo
═══════════════════════════════════════════════════════════════ */
async function cargarTrasladosDespacho() {
  const res = await api(`${CONFIG.API}/transfers?size=50&sort=createdAt,desc`);
  if (!res || !res.ok) {
    document.getElementById('dispatch-list').innerHTML =
      msgEl('error', 'No se pudo cargar traslados');
    return;
  }
  const data  = await res.json();
  const items = (data.content || []).filter(t => t.status === 'PREPARED');

  if (items.length === 0) {
    document.getElementById('dispatch-list').innerHTML =
      msgEl('warn', '⚠️ No hay traslados en estado PREPARED. Primero crea y prepara un traslado en la pestaña Traslados.');
    return;
  }

  document.getElementById('dispatch-list').innerHTML = items.map(t => `
    <div class="transfer-select-row" onclick="seleccionarDespacho('${t.transferId}','${t.transferCode}','${t.originCode}','${t.destinationCode}')">
      <div style="font-size:24px">📦</div>
      <div style="flex:1">
        <div class="ts-code">${t.transferCode}</div>
        <div class="ts-meta">${t.originCode} → ${t.destinationCode} · Creado: ${fmtDate(t.createdAt)}</div>
      </div>
      ${badge(t.status)}
      <button class="btn btn-sm btn-gold">Seleccionar →</button>
    </div>`).join('');
}

function seleccionarDespacho(id, code, origin, dest) {
  dispatchTransfer    = { id, code, origin, dest };
  dispatchValidaciones = [];
  showTab('despacho');
  irDespachoStep2();
  document.getElementById('dispatch-transfer-info').innerHTML =
    `📦 <b>${code}</b> &nbsp;|&nbsp; <b>${origin}</b> → <b>${dest}</b> &nbsp;|&nbsp; ID: <code>${id}</code>`;
  document.getElementById('dispatch-confirm-info').innerHTML =
    `Vas a despachar: <b>${code}</b> (${origin} → ${dest}).<br>
     Asegúrate de haber validado todos los pallets antes de confirmar.`;
  // Limpiar checklist
  document.getElementById('dispatch-checklist').innerHTML = `
    <li class="rfid-check-item">
      <div class="chk chk-pend">?</div>
      <span>Sin validaciones aún — use el formulario de arriba</span>
    </li>`;
  document.getElementById('dsp-device').value = 'PORTAL-ORIGEN-01';
}

function irDespachoStep1() {
  ['dispatch-step1','dispatch-step2','dispatch-step3'].forEach((id,i) => {
    document.getElementById(id).style.display = i===0 ? 'block' : 'none';
  });
  actualizarStepper('dispatch-stepper', 1);
}
function irDespachoStep2() {
  ['dispatch-step1','dispatch-step2','dispatch-step3'].forEach((id,i) => {
    document.getElementById(id).style.display = i===1 ? 'block' : 'none';
  });
  actualizarStepper('dispatch-stepper', 2);
}
function irDespachoStep3() {
  if (!dispatchTransfer) { alert('Selecciona un traslado primero'); return; }
  ['dispatch-step1','dispatch-step2','dispatch-step3'].forEach((id,i) => {
    document.getElementById(id).style.display = i===2 ? 'block' : 'none';
  });
  actualizarStepper('dispatch-stepper', 3);
}
function volverDespachoStep1() { irDespachoStep1(); }

async function validarRFIDDespacho() {
  if (!dispatchTransfer) {
    document.getElementById('msg-dsp-val').innerHTML = msgEl('error','Selecciona un traslado primero');
    return;
  }
  const epc    = document.getElementById('dsp-epc').value.trim();
  const lpn    = document.getElementById('dsp-lpn').value.trim();
  const device = document.getElementById('dsp-device').value.trim();
  if (!epc || !device) {
    document.getElementById('msg-dsp-val').innerHTML = msgEl('error','EPC y Device ID son requeridos');
    return;
  }
  const body = {
    lpnCode: lpn || undefined, epc,
    deviceId: device, deviceType: document.getElementById('dsp-devtype').value,
    userId: S.username || 'frontend-user', readDateTime: new Date().toISOString()
  };
  const res  = await api(`${CONFIG.API}/transfers/${dispatchTransfer.id}/rfid-validations`,
    { method: 'POST', body: JSON.stringify(body) });
  if (!res) return;
  const data = await res.json();
  const ok   = res.ok && data.result === 'VALID';
  document.getElementById('msg-dsp-val').innerHTML = ok
    ? msgEl('success', `✅ Validado: <b>${data.result}</b> — ${data.reason || ''}`)
    : msgEl('error',   `❌ ${res.ok ? data.result : 'Error ' + res.status}: ${data.reason || data.message || ''}`);

  // Agregar al checklist
  dispatchValidaciones.push({ epc, lpn, device, result: data.result, ok });
  renderDispatchChecklist();
  if (ok) {
    document.getElementById('dsp-epc').value = '';
    document.getElementById('dsp-lpn').value = '';
  }
}

function renderDispatchChecklist() {
  const ul = document.getElementById('dispatch-checklist');
  ul.innerHTML = dispatchValidaciones.map(v => `
    <li class="rfid-check-item">
      <div class="chk ${v.ok ? 'chk-ok' : 'chk-err'}">${v.ok ? '✓' : '✗'}</div>
      <span>EPC: <b class="txt-mono">${v.epc}</b>
        ${v.lpn ? `· LPN: ${v.lpn}` : ''} · <b>${v.result}</b></span>
    </li>`).join('') ||
    `<li class="rfid-check-item"><div class="chk chk-pend">?</div><span>Sin validaciones</span></li>`;
}

async function confirmarDespacho() {
  if (!dispatchTransfer) return;
  const carrier = document.getElementById('dsp-carrier').value.trim();
  if (!carrier) {
    document.getElementById('msg-dispatch').innerHTML = msgEl('error','Transportista es requerido');
    return;
  }
  const btn = document.getElementById('btn-dispatch-confirm');
  btn.disabled = true; btn.textContent = 'Procesando...';

  const body = {
    carrierId:   carrier,
    guideNumber: document.getElementById('dsp-guide').value.trim() || undefined,
    plate:       document.getElementById('dsp-plate').value.trim() || undefined,
    remarks:     document.getElementById('dsp-remarks').value.trim() || undefined,
    dispatchedBy: S.username || 'frontend-user'
  };

  const res  = await api(`${CONFIG.API}/transfers/${dispatchTransfer.id}/dispatch`,
    { method: 'POST', body: JSON.stringify(body) });
  btn.disabled = false; btn.textContent = '🚢 Confirmar Despacho';
  if (!res) return;
  const data = await res.json();

  if (res.ok) {
    document.getElementById('msg-dispatch').innerHTML = msgEl('success',
      `✅ Traslado <b>${dispatchTransfer.code}</b> despachado exitosamente. Estado: <b>${data.status}</b>`);
    dispatchTransfer = null;
    setTimeout(() => { irDespachoStep1(); cargarTrasladosDespacho(); cargarKPIs(); }, 3000);
  } else {
    document.getElementById('msg-dispatch').innerHTML =
      msgEl('error', `Error ${res.status}: ${data.message || JSON.stringify(data)}`);
  }
}

/* ══════════════════════════════════════════════════════════════
   RECEPCIÓN — flujo completo
═══════════════════════════════════════════════════════════════ */
async function cargarTrasladosRecepcion() {
  const res = await api(`${CONFIG.API}/transfers?size=50&sort=createdAt,desc`);
  if (!res || !res.ok) {
    document.getElementById('receipt-list').innerHTML = msgEl('error','No se pudo cargar traslados');
    return;
  }
  const data  = await res.json();
  const items = (data.content || []).filter(t =>
    t.status === 'DISPATCHED' || t.status === 'IN_TRANSIT');

  if (items.length === 0) {
    document.getElementById('receipt-list').innerHTML =
      msgEl('warn', '⚠️ No hay traslados pendientes de recepción (DISPATCHED o IN_TRANSIT).');
    return;
  }

  document.getElementById('receipt-list').innerHTML = items.map(t => `
    <div class="transfer-select-row"
      onclick="seleccionarRecepcion('${t.transferId}','${t.transferCode}','${t.originCode}','${t.destinationCode}')">
      <div style="font-size:24px">🚛</div>
      <div style="flex:1">
        <div class="ts-code">${t.transferCode}</div>
        <div class="ts-meta">${t.originCode} → ${t.destinationCode} · ${fmtDate(t.createdAt)}</div>
      </div>
      ${badge(t.status)}
      <button class="btn btn-sm btn-primary">Seleccionar →</button>
    </div>`).join('');
}

function seleccionarRecepcion(id, code, origin, dest) {
  receiptTransfer = { id, code, origin, dest };
  showTab('recepcion');
  document.getElementById('receipt-step1').style.display = 'none';
  document.getElementById('receipt-step2').style.display = 'block';
  document.getElementById('receipt-step3').style.display = 'none';
  actualizarStepper('rstep', 2);
  document.getElementById('receipt-transfer-info').innerHTML =
    `🚛 <b>${code}</b> &nbsp;|&nbsp; <b>${origin}</b> → <b>${dest}</b> &nbsp;|&nbsp; ID: <code>${id}</code>`;
  document.getElementById('rec-device').value = 'PORTAL-DESTINO-01';
}

function volverRecepcionStep1() {
  receiptTransfer = null;
  document.getElementById('receipt-step1').style.display = 'block';
  document.getElementById('receipt-step2').style.display = 'none';
  document.getElementById('receipt-step3').style.display = 'none';
  actualizarStepper('rstep', 1);
  cargarTrasladosRecepcion();
}

async function confirmarRecepcion() {
  if (!receiptTransfer) return;
  const device = document.getElementById('rec-device').value.trim();
  if (!device) {
    document.getElementById('msg-receipt').innerHTML = msgEl('error','Device ID es requerido');
    return;
  }
  const body = {
    deviceId:    device,
    deviceType:  document.getElementById('rec-devtype').value,
    remarks:     document.getElementById('rec-remarks').value.trim() || undefined,
    receivedBy:  S.username || 'frontend-user',
    receiptDate: new Date().toISOString()
  };

  const res  = await api(`${CONFIG.API}/transfers/${receiptTransfer.id}/receipts`,
    { method: 'POST', body: JSON.stringify(body) });
  if (!res) return;
  const data = await res.json();

  if (res.ok) {
    document.getElementById('msg-receipt').innerHTML =
      msgEl('success', `✅ Recepción confirmada. Estado: <b>${data.status || 'RECEIVED'}</b>`);
    // Pasar a reconciliación
    setTimeout(() => verReconciliacion(receiptTransfer.id), 1200);
  } else {
    document.getElementById('msg-receipt').innerHTML =
      msgEl('error', `Error ${res.status}: ${data.message || JSON.stringify(data)}`);
  }
}

async function verReconciliacion(transferId) {
  showTab('recepcion');
  document.getElementById('receipt-step1').style.display = 'none';
  document.getElementById('receipt-step2').style.display = 'none';
  document.getElementById('receipt-step3').style.display = 'block';
  actualizarStepper('rstep', 3);

  const res = await api(`${CONFIG.API}/transfers/${transferId}/reconciliation`);
  const el  = document.getElementById('reconcile-result');

  if (!res || !res.ok) {
    el.innerHTML = msgEl('error', `No se pudo obtener reconciliación (HTTP ${res?.status || '?'}).
      <br><small>Asegúrate de que el traslado esté en estado RECEIVED.</small>`);
    el.innerHTML += `<div style="margin-top:12px">
      <button class="btn btn-outline" onclick="volverRecepcionStep1()">← Volver a traslados</button>
    </div>`;
    return;
  }

  const data = await res.json();
  renderReconciliacion(data, transferId);
  cargarKPIs();
}

function renderReconciliacion(data, transferId) {
  const totalEsp   = data.totalExpected  || data.expectedLpns || 0;
  const totalRec   = data.totalReceived  || data.receivedLpns || 0;
  const missing    = data.missingItems   || data.missingLpns  || (totalEsp - totalRec);
  const extra      = data.extraItems     || data.extraLpns    || 0;
  const pct        = totalEsp ? Math.round((totalRec / totalEsp) * 100) : (totalRec > 0 ? 100 : 0);
  const status     = data.status || data.reconciliationStatus || 'UNKNOWN';
  const isOk       = pct === 100 && missing === 0 && extra === 0;
  const isWarn     = extra > 0;
  const cls        = isOk ? 'ok' : isWarn ? 'warn' : 'error';
  const emoji      = isOk ? '✅' : isWarn ? '⚠️' : '❌';

  const el = document.getElementById('reconcile-result');
  el.innerHTML = `
    <div class="reconcile-summary ${cls}">
      <div class="reconcile-title">${emoji} Reconciliación: <b>${status}</b></div>
      <div class="reconcile-stats">
        <div class="rs-item">
          <div class="rs-val">${pct}%</div>
          <div class="rs-lbl">Completado</div>
        </div>
        <div class="rs-item">
          <div class="rs-val">${totalEsp}</div>
          <div class="rs-lbl">Esperados</div>
        </div>
        <div class="rs-item">
          <div class="rs-val" style="color:${isOk?'#15803D':'#C2410C'}">${totalRec}</div>
          <div class="rs-lbl">Recibidos</div>
        </div>
        <div class="rs-item">
          <div class="rs-val" style="color:${missing>0?'#991B1B':'#15803D'}">${missing}</div>
          <div class="rs-lbl">Faltantes</div>
        </div>
        <div class="rs-item">
          <div class="rs-val" style="color:${extra>0?'#92400E':'#15803D'}">${extra}</div>
          <div class="rs-lbl">Extras</div>
        </div>
      </div>
    </div>

    ${renderReconciliacionTabla(data)}

    <div style="margin-top:16px;display:flex;gap:10px;flex-wrap:wrap">
      <button class="btn btn-outline" onclick="volverRecepcionStep1()">← Nueva Recepción</button>
      <button class="btn btn-secondary" onclick="cargarKPIs();showTab('kpis')">
        📊 Ver Dashboard
      </button>
    </div>
  `;
}

function renderReconciliacionTabla(data) {
  // Intentamos mostrar detalle de pallets si el backend los retorna
  const items = data.items || data.pallets || data.details || [];
  if (!items || items.length === 0) {
    return `<div class="alert alert-info" style="margin-top:12px">
      📋 Reconciliación registrada — el backend no devuelve detalle de items individuales en este endpoint.
    </div>`;
  }

  return `
    <div style="margin-top:16px">
      <div class="card-title" style="font-size:13px;margin-bottom:10px">Detalle por Item</div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr><th>LPN / EPC</th><th>Estado</th><th>Esperado</th><th>Recibido</th><th>Nota</th></tr>
          </thead>
          <tbody>
            ${items.map(it => {
              const sts = it.status || (it.received ? 'RECEIVED' : 'MISSING');
              const cls = sts === 'RECEIVED' ? 'diff-ok' : sts === 'EXTRA' ? 'diff-extra' : 'diff-miss';
              return `<tr class="${cls}">
                <td class="txt-mono">${it.lpnCode || it.epc || it.code || '—'}</td>
                <td>${badge(sts.toLowerCase())}</td>
                <td>${it.expected ?? '✓'}</td>
                <td>${it.received ?? '—'}</td>
                <td style="font-size:12px;color:var(--muted)">${it.note || it.reason || '—'}</td>
              </tr>`;
            }).join('')}
          </tbody>
        </table>
      </div>
    </div>`;
}

/* ══════════════════════════════════════════════════════════════
   Stepper helper
═══════════════════════════════════════════════════════════════ */
function actualizarStepper(prefix, stepActivo) {
  // Para dispatch usa IDs dstep-1/2/3, para receipt usa IDs rstep-1/2/3
  const ids = prefix === 'rstep'
    ? ['rstep-1','rstep-2','rstep-3']
    : ['dstep-1','dstep-2','dstep-3'];

  ids.forEach((id, i) => {
    const el = document.getElementById(id);
    if (!el) return;
    el.className = 'step';
    if (i + 1 < stepActivo)  el.classList.add('done');
    if (i + 1 === stepActivo) el.classList.add('active');
  });
}

/* ══════════════════════════════════════════════════════════════
   TRASLADOS (tab traslados — mismo que v15)
═══════════════════════════════════════════════════════════════ */
async function crearTraslado() {
  const body = {
    originCode:      document.getElementById('t-origin').value.trim(),
    destinationCode: document.getElementById('t-dest').value.trim(),
    priority:        document.getElementById('t-priority').value,
    scheduledDate:   document.getElementById('t-date').value
                       ? new Date(document.getElementById('t-date').value).toISOString() : undefined,
    carrierId:       document.getElementById('t-carrier').value.trim() || undefined,
    remarks:         document.getElementById('t-remarks').value.trim() || undefined
  };
  if (!body.originCode || !body.destinationCode || !body.scheduledDate) {
    document.getElementById('msg-traslado').innerHTML =
      msgEl('error','Origen, destino y fecha son requeridos');
    return;
  }
  const res  = await api(`${CONFIG.API}/transfers`, { method:'POST', body:JSON.stringify(body) });
  if (!res) return;
  const data = await res.json();
  document.getElementById('msg-traslado').innerHTML = res.ok
    ? msgEl('success', `✅ Traslado creado: <b>${data.transferCode}</b>`)
    : msgEl('error', `Error ${res.status}: ${data.message||'Desconocido'}`);
  if (res.ok) { cargarTraslados(); cargarKPIs(); }
}

async function cargarTraslados() {
  const res = await api(`${CONFIG.API}/transfers?size=50&sort=createdAt,desc`);
  if (!res || !res.ok) return;
  const data  = await res.json();
  const items = data.content || [];
  document.getElementById('lista-traslados').innerHTML = items.map(t=>`
    <tr>
      <td class="txt-mono"><b>${t.transferCode||'—'}</b></td>
      <td>${t.originCode||'—'}</td><td>${t.destinationCode||'—'}</td>
      <td>${badge(t.status)}</td><td>${t.priority||'—'}</td>
      <td>${fmtDate(t.createdAt)}</td>
      <td>${accionRapida(t)}</td>
    </tr>`).join('') ||
    '<tr><td colspan="7" class="no-data">Sin traslados</td></tr>';
}

/* ══════════════════════════════════════════════════════════════
   PALLETS
═══════════════════════════════════════════════════════════════ */
async function agregarPallet() {
  const tid  = document.getElementById('p-tid').value.trim();
  const code = document.getElementById('p-code').value.trim();
  const wt   = document.getElementById('p-weight').value;
  if (!tid || !code) {
    document.getElementById('msg-pallet').innerHTML = msgEl('error','Transfer ID y código de pallet son requeridos');
    return;
  }
  const res = await api(`${CONFIG.API}/transfers/${tid}/pallets`, {
    method:'POST',
    body: JSON.stringify({ palletCode: code, grossWeight: wt ? parseFloat(wt) : undefined })
  });
  if (!res) return;
  const data = await res.json();
  if (res.ok) {
    document.getElementById('msg-pallet').innerHTML =
      msgEl('success', `✅ Pallet agregado: <b>${data.palletCode}</b> · ID: <code>${data.id}</code>`);
    // Autocompletar el UUID en el campo "Ver Contenido del Pallet"
    const queryField = document.getElementById('pallet-query');
    if (queryField && data.id) {
      queryField.value = data.id;
      // Hacer scroll al campo de contenido
      queryField.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
  } else {
    document.getElementById('msg-pallet').innerHTML =
      msgEl('error', `Error ${res.status}: ${data.message||'Desconocido'}`);
  }
}

async function verPallet() {
  const pid = document.getElementById('pallet-query').value.trim();
  if (!pid) return;
  const res = await api(`${CONFIG.API}/pallets/${pid}`);
  if (!res) return;
  const d = await res.json();
  document.getElementById('pallet-detail').innerHTML = res.ok
    ? msgEl('info', `<b>${d.palletCode}</b> · Estado: ${badge(d.status)} · LPNs: ${d.totalLpns||0} · Unidades: ${d.totalUnits||0}`)
    : msgEl('error', `Error ${res.status}: ${d.message||'Desconocido'}`);
}

/* ══════════════════════════════════════════════════════════════
   LECTURAS
═══════════════════════════════════════════════════════════════ */
async function registrarLectura() {
  const body = {
    epc:      document.getElementById('rt-epc').value.trim(),
    tag:      document.getElementById('rt-tag').value.trim() || undefined,
    moduloId: document.getElementById('rt-modulo').value.trim() || undefined,
    rssi:     document.getElementById('rt-rssi').value
                ? parseInt(document.getElementById('rt-rssi').value) : undefined
  };
  if (!body.epc) {
    document.getElementById('msg-readtag').innerHTML = msgEl('error','EPC es requerido');
    return;
  }
  const res = await api(`${CONFIG.API}/read-tags`, { method:'POST', body:JSON.stringify(body) });
  if (!res) return;
  document.getElementById('msg-readtag').innerHTML = res.ok
    ? msgEl('success','✅ Lectura registrada')
    : msgEl('error',`Error ${res.status}`);
  if (res.ok) cargarLecturas();
}

async function cargarLecturas() {
  const res = await api(`${CONFIG.API}/read-tags?size=20`);
  if (!res || !res.ok) return;
  const data = await res.json();
  document.getElementById('lista-lecturas').innerHTML =
    (data.content||[]).map(r=>`
      <tr>
        <td class="txt-mono">${r.id||'—'}</td>
        <td class="txt-mono">${r.epc||'—'}</td>
        <td>${r.tag||'—'}</td>
        <td>${r.moduloId||'—'}</td>
        <td>${r.rssi!=null?r.rssi+'dBm':'—'}</td>
        <td>${fmtDate(r.lastTime)}</td>
      </tr>`).join('') ||
    '<tr><td colspan="6" class="no-data">Sin lecturas</td></tr>';
}

/* ══════════════════════════════════════════════════════════════
   VALIDACIONES
═══════════════════════════════════════════════════════════════ */
async function validarLectura() {
  const tid = document.getElementById('v-tid').value.trim();
  if (!tid) {
    document.getElementById('msg-validacion').innerHTML = msgEl('error','Transfer ID es requerido');
    return;
  }
  const body = {
    lpnCode:     document.getElementById('v-lpn').value.trim() || undefined,
    epc:         document.getElementById('v-epc').value.trim() || undefined,
    deviceId:    document.getElementById('v-device').value.trim(),
    deviceType:  document.getElementById('v-devtype').value,
    userId:      S.username || 'frontend-user',
    readDateTime: new Date().toISOString()
  };
  const res  = await api(`${CONFIG.API}/transfers/${tid}/rfid-validations`,
    { method:'POST', body:JSON.stringify(body) });
  if (!res) return;
  const data = await res.json();
  document.getElementById('msg-validacion').innerHTML = res.ok
    ? msgEl(data.result==='VALID'?'success':'error', `Resultado: <b>${data.result}</b> — ${data.reason||''}`)
    : msgEl('error', `Error ${res.status}: ${data.message||''}`);
}

/* ══════════════════════════════════════════════════════════════
   AUDITORÍA
═══════════════════════════════════════════════════════════════ */
async function consultarAuditoria() {
  const start = document.getElementById('aud-start').value;
  const end   = document.getElementById('aud-end').value;
  if (!start || !end) {
    document.getElementById('audit-result').innerHTML = msgEl('error','Fechas son requeridas');
    return;
  }
  const params = new URLSearchParams({
    startDate: new Date(start).toISOString(),
    endDate:   new Date(end).toISOString(),
    size: 50, page: 0
  });
  ['aud-user','aud-ip','aud-method','aud-level'].forEach(id => {
    const v = document.getElementById(id).value;
    const k = id.replace('aud-','');
    if (v) params.set(
      k==='user'?'userId':k==='ip'?'clientIp':k==='method'?'httpMethod':'auditLevel', v);
  });

  const auditUrl = IS_LOCAL
    ? `http://localhost:8084/api/v1/audit-logs?${params}`
    : `${BASE_HOST}/audit/?${params}`;

  const res = await fetch(auditUrl, { headers: buildHeaders('GET') });
  if (!res || !res.ok) {
    document.getElementById('audit-result').innerHTML =
      msgEl('error', `Error ${res?.status||'?'} — audit-service puede no estar expuesto vía Nginx`);
    return;
  }
  const data  = await res.json();
  const items = data.content || [];
  document.getElementById('audit-result').innerHTML = `
    <p style="color:var(--muted);margin-bottom:12px">${data.totalElements||0} registros</p>
    <div class="table-wrap">
      <table>
        <thead>
          <tr><th>Usuario</th><th>IP</th><th>Método</th><th>Endpoint</th>
          <th>Status</th><th>Nivel</th><th>ms</th><th>Fecha</th></tr>
        </thead>
        <tbody>
          ${items.map(a=>`<tr>
            <td>${a.userId||'—'}</td>
            <td class="txt-mono" style="font-size:11px">${a.clientIp||'—'}</td>
            <td><b>${a.httpMethod||'—'}</b></td>
            <td class="txt-mono" style="font-size:11px">${a.endpointPath||'—'}</td>
            <td>${a.httpStatus}</td>
            <td>${a.auditLevel||'—'}</td>
            <td>${a.durationMs||0}</td>
            <td>${fmtDate(a.createdAt)}</td>
          </tr>`).join('')}
        </tbody>
      </table>
    </div>`;
}

/* ══════════════════════════════════════════════════════════════
   PALLET TAGS — T4 (CRUD) + T5 (Sync desde servidor)
   Sprint 8 · Pystelectronic
═══════════════════════════════════════════════════════════════ */

let _ptCache = [];

async function cargarPalletTags() {
  const tbody = document.getElementById('lista-pallet-tags');
  tbody.innerHTML = '<tr><td colspan="6" class="no-data">Cargando...</td></tr>';
  try {
    const res = await api(`${CONFIG.API}/pallet-tags?size=200&sort=createdAt,desc`);
    if (!res || !res.ok) {
      tbody.innerHTML = `<tr><td colspan="6" class="no-data">Error al cargar (${res?.status||'sin conexión'})</td></tr>`;
      return;
    }
    const data = await res.json();
    _ptCache = data.content || data || [];
    renderPalletTags(_ptCache);
  } catch(e) {
    tbody.innerHTML = `<tr><td colspan="6" class="no-data">Error: ${e.message}</td></tr>`;
  }
}

function renderPalletTags(items) {
  const canWrite = S.roles.includes('ROLE_ADMIN') || S.roles.includes('ROLE_OPERATOR');
  const tbody    = document.getElementById('lista-pallet-tags');
  const countEl  = document.getElementById('pt-count');
  if (countEl) countEl.textContent = `${items.length} tag(s) registrado(s)`;

  if (!items.length) {
    tbody.innerHTML = '<tr><td colspan="6" class="no-data">Sin registros</td></tr>';
    return;
  }
  tbody.innerHTML = items.map(t => `
    <tr>
      <td class="pt-epc-cell">${t.epc||'—'}</td>
      <td class="pt-epc-cell" style="color:var(--muted)">${t.tid||'—'}</td>
      <td>${t.descripcion||'—'}</td>
      <td><span class="badge badge-${(t.activo!==false)?'activo':'inactivo'}">${(t.activo!==false)?'Activo':'Inactivo'}</span></td>
      <td>${fmtDate(t.createdAt||t.fechaRegistro)}</td>
      <td>${canWrite
        ? `<button class="btn btn-sm btn-danger" onclick="eliminarPalletTag('${t.id}','${t.epc}')">🗑</button>`
        : '—'}</td>
    </tr>`).join('');
}

function filtrarPalletTags() {
  const q = (document.getElementById('pt-filtro').value||'').trim().toLowerCase();
  renderPalletTags(q
    ? _ptCache.filter(t =>
        (t.epc||'').toLowerCase().includes(q) ||
        (t.descripcion||'').toLowerCase().includes(q))
    : _ptCache);
}

async function crearPalletTag() {
  const epc  = (document.getElementById('pt-epc').value||'').trim().replace(/-/g,'').toUpperCase();
  const tid  = (document.getElementById('pt-tid').value||'').trim().replace(/-/g,'').toUpperCase() || undefined;
  const desc = (document.getElementById('pt-desc').value||'').trim() || undefined;

  if (!epc) {
    document.getElementById('msg-pt').innerHTML = msgEl('error','El EPC es obligatorio');
    return;
  }
  if (!/^[0-9A-F]{10,50}$/i.test(epc)) {
    document.getElementById('msg-pt').innerHTML = msgEl('error','EPC inválido — debe ser hexadecimal (sin guiones, 10-50 caracteres)');
    return;
  }

  const res = await api(`${CONFIG.API}/pallet-tags`, {
    method: 'POST',
    body: JSON.stringify({ epc, tid, descripcion: desc })
  });
  if (!res) return;
  const data = await res.json().catch(()=>({}));
  if (res.ok) {
    document.getElementById('msg-pt').innerHTML = msgEl('success', `✅ Tag registrado: <b>${epc}</b>`);
    document.getElementById('pt-epc').value  = '';
    document.getElementById('pt-tid').value  = '';
    document.getElementById('pt-desc').value = '';
    cargarPalletTags();
  } else {
    document.getElementById('msg-pt').innerHTML = msgEl('error',
      `Error ${res.status}: ${data.message||data.error||'No se pudo registrar'}`);
  }
}

async function eliminarPalletTag(id, epc) {
  if (!confirm(`¿Eliminar tag de pallet?\n\nEPC: ${epc}\n\nEsta acción no se puede deshacer.`)) return;
  const res = await api(`${CONFIG.API}/pallet-tags/${id}`, { method: 'DELETE' });
  if (!res) return;
  if (res.ok || res.status === 204) {
    cargarPalletTags();
  } else {
    alert(`Error al eliminar (HTTP ${res.status})`);
  }
}

// T5: Genera script SQL para sincronizar SQL Server local con el servidor
async function sincronizarPalletTags() {
  const statusEl = document.getElementById('pt-sync-status');
  const resultEl = document.getElementById('pt-sync-result');
  const sqlCard  = document.getElementById('card-sql-export');

  statusEl.textContent = '⏳ Consultando servidor...';
  resultEl.innerHTML   = '';
  sqlCard.style.display = 'none';

  try {
    const res = await api(`${CONFIG.API}/pallet-tags?size=500&sort=createdAt,asc&soloActivos=true`);
    if (!res || !res.ok) {
      statusEl.textContent = '';
      resultEl.innerHTML   = msgEl('error', `Error ${res?.status||'sin conexión'} al consultar servidor`);
      return;
    }
    const data  = await res.json();
    const items = data.content || data || [];
    statusEl.textContent = `✅ ${items.length} tag(s) encontrado(s)`;

    if (!items.length) {
      resultEl.innerHTML = msgEl('warn', 'No hay pallet_tags activos en el servidor central.');
      return;
    }

    // Generar script SQL para SQL Server local
    const lines = [
      '-- ═══════════════════════════════════════════════════════',
      '-- Script generado por portal RFID v17',
      `-- Fecha: ${new Date().toLocaleString('es-PE')}`,
      `-- Total: ${items.length} pallet tag(s) del servidor central`,
      '-- Ejecutar: sqlcmd -S .\\SQLEXPRESS -E -d rfid_db',
      '-- ═══════════════════════════════════════════════════════',
      '',
      'DELETE FROM pallet_tags;',
      '',
    ];
    items.forEach((t, i) => {
      const epc  = (t.epc||'').replace(/'/g,"''");
      const desc = (t.descripcion||`Tag sincronizado ${i+1}`).replace(/'/g,"''");
      lines.push(`INSERT INTO pallet_tags (EPC, Descripcion) VALUES ('${epc}', '${desc}');`);
    });
    lines.push('', `-- Verificar: SELECT COUNT(*) FROM pallet_tags; -- debe dar ${items.length}`);

    document.getElementById('pt-sql-script').value = lines.join('\n');
    sqlCard.style.display = 'block';

    resultEl.innerHTML = msgEl('success',
      `✅ Script listo para <b>${items.length}</b> tag(s). Cópialo y ejecútalo en la PC lectora.`);

    _ptCache = items;
    renderPalletTags(items);

  } catch(e) {
    statusEl.textContent = '';
    resultEl.innerHTML   = msgEl('error', `Error inesperado: ${e.message}`);
  }
}

function copiarScriptPT() {
  const el = document.getElementById('pt-sql-script');
  el.select();
  document.execCommand('copy');
  alert('✅ Script copiado al portapapeles');
}

/* ══════════════════════════════════════════════════════════════
   INICIALIZACIÓN
═══════════════════════════════════════════════════════════════ */

/* ══════════════════════════════════════════════════════════════
   SESIÓN DE PALLET — Sprint 9
   Gestión del pallet activo por portal en tiempo real.
═══════════════════════════════════════════════════════════════ */

function iniciarSessionPanel() {
  renderSessionPanel();
  // Cargar traslados IN_TRANSIT para el selector
  cargarTrasladosSession();
}

async function cargarTrasladosSession() {
  const sel = document.getElementById('session-transfer-select');
  if (!sel) return;
  const res = await api(`${CONFIG.API}/transfers?size=50&sort=createdAt,desc`);
  if (!res || !res.ok) return;
  const data = await res.json();
  const activos = (data.content || []).filter(t =>
    t.status === 'IN_TRANSIT' || t.status === 'DISPATCHED' || t.status === 'PREPARED');
  sel.innerHTML = '<option value="">— Selecciona un traslado —</option>' +
    activos.map(t => `<option value="${t.transferId}">${t.transferCode} · ${t.originCode}→${t.destinationCode} [${t.status}]</option>`).join('');
}

async function abrirSessionPortal() {
  const portalId = document.getElementById('session-portal-id').value.trim();
  const transferId = document.getElementById('session-transfer-select').value;
  if (!portalId) { mostrarMsgSession('error', 'Ingresa el ID del portal'); return; }
  if (!transferId) { mostrarMsgSession('error', 'Selecciona un traslado'); return; }

  const res = await api(`${CONFIG.API}/portals/${portalId}/session/open`, {
    method: 'POST', body: JSON.stringify({ transferId })
  });
  if (!res) return;
  const data = await res.json();
  if (res.ok) {
    sessionPalletActivo = null;
    sessionLpns = [];
    mostrarMsgSession('success', `✅ Sesión abierta en portal ${portalId}`);
    renderSessionPanel();
  } else {
    mostrarMsgSession('error', `Error ${res.status}: ${data.message || JSON.stringify(data)}`);
  }
}

async function cerrarPalletActivo() {
  const portalId = document.getElementById('session-portal-id').value.trim();
  if (!portalId) { mostrarMsgSession('error', 'Ingresa el ID del portal'); return; }
  const res = await api(`${CONFIG.API}/portals/${portalId}/session/close-pallet`, {
    method: 'POST'
  });
  if (res && (res.ok || res.status === 204)) {
    sessionPalletActivo = null;
    sessionLpns = [];
    mostrarMsgSession('success', 'Pallet cerrado — listo para leer el siguiente pallet');
    renderSessionPanel();
  } else {
    mostrarMsgSession('error', `Error al cerrar pallet (HTTP ${res?.status || '?'})`);
  }
}

function renderSessionPanel() {
  const panelEl = document.getElementById('session-pallet-panel');
  if (!panelEl) return;

  if (!sessionPalletActivo) {
    panelEl.innerHTML = `
      <div class="session-empty">
        <div style="font-size:2rem;margin-bottom:8px">📦</div>
        <div style="font-weight:600;margin-bottom:4px">Sin pallet activo</div>
        <div style="color:var(--muted);font-size:13px">Lee un pallet con el lector RFID para comenzar</div>
      </div>`;
    return;
  }

  panelEl.innerHTML = `
    <div class="session-pallet-activo">
      <div class="session-pallet-header">
        <div>
          <div class="session-pallet-code">📦 ${sessionPalletActivo.palletCode}</div>
          <div style="font-size:11px;color:var(--muted);margin-top:2px">Portal: ${sessionPalletActivo.portalId}</div>
        </div>
        <div class="session-lpn-counter">
          <span class="session-lpn-num">${sessionPalletActivo.lpnCount}</span>
          <span style="font-size:11px;color:var(--muted)">LPNs</span>
        </div>
      </div>
      <div class="session-lpn-list" id="session-lpn-list">
        ${sessionLpns.length === 0
          ? '<div style="color:var(--muted);font-size:12px;padding:8px">Esperando LPNs...</div>'
          : sessionLpns.map(l => `
            <div class="session-lpn-item">
              <span class="session-lpn-badge">✓</span>
              <span class="txt-mono" style="font-size:12px">${l.lpnCode}</span>
              <span style="color:var(--muted);font-size:11px;margin-left:auto">${l.ts}</span>
            </div>`).join('')}
      </div>
    </div>`;
}

function renderSessionRejected(epc) {
  const panelEl = document.getElementById('session-pallet-panel');
  if (!panelEl) return;
  // Flash de alerta roja sobre el panel
  const alert = document.createElement('div');
  alert.className = 'session-rejected-flash';
  alert.innerHTML = `🚫 LPN rechazado — Lee un pallet primero<br><span style="font-size:11px;opacity:.8">${epc}</span>`;
  panelEl.prepend(alert);
  setTimeout(() => alert.remove(), 3500);
}

function mostrarMsgSession(tipo, texto) {
  const el = document.getElementById('msg-session');
  if (!el) return;
  el.innerHTML = `<div class="alert alert-${tipo}">${texto}</div>`;
  setTimeout(() => { el.innerHTML = ''; }, 4000);
}

window.addEventListener('load', async () => {
  const p = new URLSearchParams(window.location.search);
  if (p.has('code') || p.has('error')) await procesarCallback();
});
