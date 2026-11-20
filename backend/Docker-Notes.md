# Locus Backend: Docker Quick Start (Windows and macOS)

Run every command from the `backend` folder.

## First time only

### 1. Fix line endings (once)

**Windows (PowerShell):**

```powershell
$path = Join-Path $PWD "mvnw"
$c = [IO.File]::ReadAllText($path) -replace "`r`n", "`n"
[IO.File]::WriteAllText($path, $c)
Select-String -Path .\mvnw -Pattern "`r" -Quiet
```

The last line should print `False`.

**macOS (Terminal):**

```bash
sed -i '' 's/\r$//' mvnw
chmod +x mvnw
```

Running `sed` on a file that's already clean does nothing, so it's safe to repeat.

### 2. Build the image

```
docker build -t locus-backend .
```

Takes several minutes the first time. If it fails with a download error, run it again.

### 3. Run the container

```
docker run -d -p 8080:8080 --env-file .env --name locus-backend locus-backend
```

### 4. Check it works

```
docker logs -f locus-backend
```

Check the health endpoints:

**Windows (PowerShell):** use `curl.exe`, because `curl` is an alias for a different command there.

```powershell
curl.exe -i http://localhost:8080/actuator/health/liveness
curl.exe -i http://localhost:8080/actuator/health/readiness
```

**macOS (Terminal):**

```bash
curl -i http://localhost:8080/actuator/health/liveness
curl -i http://localhost:8080/actuator/health/readiness
```

Both should return `200` with `{"status":"UP"}`. Press Ctrl+C to stop following the logs. The container keeps running.

---

## Every time after that

### Start

```
docker start locus-backend
```

If that says the container doesn't exist, run step 3 again.

If that says the name is already in use:

```
docker rm -f locus-backend
docker run -d -p 8080:8080 --env-file .env --name locus-backend locus-backend
```

### Check status

```
docker ps
docker logs -f locus-backend
```

### Stop and restart

```
docker stop locus-backend
docker restart locus-backend
```

---

## After changing code or config

Changed Java code, `pom.xml`, or `application.yml`:

```
docker build -t locus-backend .
docker rm -f locus-backend
docker run -d -p 8080:8080 --env-file .env --name locus-backend locus-backend
```

Changed only `.env`:

```
docker rm -f locus-backend
docker run -d -p 8080:8080 --env-file .env --name locus-backend locus-backend
```

`docker start` and `docker restart` do not pick up code or `.env` changes.

---

## Troubleshooting

| Problem | Windows fix | macOS fix |
|---|---|---|
| Container exits right away | `docker logs locus-backend`, look for an unresolved `${...}` | Same |
| Readiness returns 503 | Wait a minute (Neon may be waking up), then retry | Same |
| Port 8080 in use | `docker ps`, or stop the IDE app | `lsof -i :8080`, or stop the IDE app |
| `./mvnw: not found` or `bad interpreter` during build | Repeat step 1 | Repeat step 1 |
| `permission denied` running `mvnw` | Not applicable | Repeat step 1 (`chmod +x`) |
| Flyway checksum error | A migration file differs from what Neon recorded | Same |

## Quick reference

```
docker build -t locus-backend .
docker run -d -p 8080:8080 --env-file .env --name locus-backend locus-backend
docker start locus-backend
docker stop locus-backend
docker restart locus-backend
docker logs -f locus-backend
docker ps
docker rm -f locus-backend
```

## Rules

- Never commit `.env` to Git.