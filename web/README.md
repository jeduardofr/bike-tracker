# Bike Tracker — web dashboard

Password-protected dashboard that reads the same Turso database the Android app
syncs to. Weekly overview (stats, weekday-colored route map, day cards, trip list)
plus per-trip detail: ride/walk segmented map, speed chart, max speed, moving vs
stopped time, elevation gain, and kilometer splits.

- **Stack:** React + Vite SPA, Hono server on Node (serves the API and the built SPA).
- **Auth:** single password → HMAC-signed session cookie (30 days). The Turso token
  never leaves the server.
- **Maps:** Leaflet + CARTO dark tiles (no API key). **Charts:** hand-rolled SVG.

## Setup

```bash
cd web
npm install
cp .env.example .env   # then fill it in
```

`.env` values:

| Var | Meaning |
|---|---|
| `TURSO_DATABASE_URL` | `libsql://bike-tracker-<org>.turso.io` |
| `TURSO_AUTH_TOKEN` | Prefer read-only: `turso db tokens create bike-tracker --read-only` |
| `DASHBOARD_PASSWORD` | The login password |
| `SESSION_SECRET` | `openssl rand -hex 32` |
| `PORT` | default `8787` |

## Develop

```bash
npm run dev:server   # API on :8787
npm run dev:web      # Vite on :5173, proxies /api to :8787
```

## Build & run (production)

```bash
npm run build        # typecheck + bundle SPA into dist/
npm run start        # serves API + dist on $PORT
```

## Deploy on a VPS

1. Copy the `web/` directory to the server (or `git pull` and `npm ci`).
2. Create `/opt/bike-tracker/web/.env` (mode `600`) with a **read-only** Turso token.
3. systemd unit — `/etc/systemd/system/bike-tracker-web.service`:

```ini
[Unit]
Description=Bike Tracker web dashboard
After=network-online.target

[Service]
WorkingDirectory=/opt/bike-tracker/web
ExecStart=/usr/bin/npm run start
Restart=on-failure
User=www-data
Environment=NODE_ENV=production

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl enable --now bike-tracker-web
```

4. Reverse proxy with HTTPS (required — the session cookie and password travel on
   this connection). Caddy makes it two lines in `/etc/caddy/Caddyfile`:

```
bike.yourdomain.com {
    reverse_proxy localhost:8787
}
```

nginx equivalent: proxy `location /` to `http://127.0.0.1:8787` inside a TLS server
block (use certbot for the certificate).

## Security notes

- Serve **only via HTTPS**; the login password is sent in the request body.
- Use a **read-only** Turso token here — the rw token belongs only to the phone.
- Route data traces home/office locations. Keep the password strong and private;
  the page also sets `noindex`.
