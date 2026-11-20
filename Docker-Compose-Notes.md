# Locus: Running with Docker Compose

Runs the backend (port 8080) and the frontend (port 5173) together. Run every command from the **repo root**, the folder that contains `docker-compose.yaml`.

Frontend: http://localhost:5173
Backend: http://localhost:8080

---

## Part 1: First time (new people)

### Step 1. Check the secret files

You need these files. They are git-ignored, so get the values from the project owner or create them yourself.

- `backend/.env` (copy from `backend/.env.example` and fill in the values)
- `frontend/.env` (copy from `frontend/.env.example`, if it exists)

`backend/.env` must contain:

```
DATABASE_URL=jdbc:postgresql://<host>.neon.tech/neondb?sslmode=require
DATABASE_USERNAME=<neon user>
DATABASE_PASSWORD=<neon password>
GOOGLE_CLIENT_ID=<client id>
JWT_SECRET=<at least 32 characters>
STRIPE_SECRET_KEY=sk_test_...
GEMINI_API_KEY=<key>
FRONTEND_ORIGIN=http://localhost:5173
```

Never commit these files to Git.

### Step 2. Fix line endings (Windows, once)

```powershell
$path = "backend\mvnw"
$c = [IO.File]::ReadAllText($path) -replace "`r`n", "`n"
[IO.File]::WriteAllText($path, $c)
```

macOS users skip this step.

### Step 3. Start everything

```
docker compose up --build -d
```

The first build takes several minutes. It downloads Maven and Node packages.

### Step 4. Check it works

```
docker compose ps
```

- `backend` should show `Up ... (healthy)` after about a minute.
- `frontend` should show `Up`.

```
curl.exe -i http://localhost:8080/actuator/health/liveness
```

Mac: use `curl` instead of `curl.exe`. Expect `200` and `{"status":"UP"}`.

Open http://localhost:5173 in your browser.

---

## Part 2: Already set up (everyday use)

### Start

```
docker compose start
```

### Stop (keeps containers)

```
docker compose stop
```

### Restart

```
docker compose restart
```

### Check status and logs

```
docker compose ps
docker compose logs -f backend
```

Press Ctrl+C to stop following logs. The containers keep running.

---

## After you change something

| You changed | Run |
|---|---|
| Java code, `pom.xml`, or the backend Dockerfile | `docker compose up --build -d` |
| Frontend code or the frontend Dockerfile | `docker compose up --build -d` |
| `backend/.env` or `FRONTEND_ORIGIN` | `docker compose up -d --force-recreate backend` |
| Nothing | `docker compose start` |

`start`, `stop`, and `restart` do not pick up code or `.env` changes. Use `up` for those.

For a full clean restart:

```
docker compose down
docker compose up --build -d
```

---

## Stop and remove

```
docker compose stop    # pause, keep containers
docker compose down    # stop and remove containers (data is in Neon, so nothing is lost)
```

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `backend` shows `unhealthy` | `docker compose logs --tail 50 backend`. Usually a missing value in `backend/.env` or a Neon connection problem. |
| Frontend loads but says it can't reach the server | Check `docker compose ps` shows backend `healthy`. Then check the browser console (F12). |
| Browser says "blocked by CORS policy" | `FRONTEND_ORIGIN` in `backend/.env` must include `http://localhost:5173`. Then run `docker compose up -d --force-recreate backend`. |
| `nginx.conf: not found` during build | `nginx.conf` must be inside `frontend/`, not the repo root. |
| Port 5173 or 8080 already in use | Stop the other app, or run `docker ps` to find the container using it. |
| Build fails with `./mvnw: not found` (Windows) | Repeat Step 2 (line endings). |

## Rules

- Never commit `.env` files to Git.
- Don't run the backend from your IDE against Neon while Docker is running. Both would run the payment worker.
- Changing `FRONTEND_ORIGIN` requires recreating the backend, not just restarting it.