# Uni Companion

| | |
|---|---|
| **Status** | Phase A scaffold (M1) |
| **Version** | 0.1.0-SNAPSHOT |
| **Last updated** | 2026-09-27 |

Campus-local AI study and collaboration platform (initial focus: **Universität Siegen**).

Canonical lecture PDFs are managed by **Course Admin / Professor** (upload + **publish/unpublish**).  
Students **enroll → study (RAG / cards / quiz) → match**.

## Locked stack (Phase A)

| Layer | Choice |
|---|---|
| Frontend | React + Vite + Tailwind |
| Backend | Java 21 · Spring Boot 4.1 · Maven |
| Deployables | `identity-service` + modular `learning-service` |
| Auth | JWT |
| Data | PostgreSQL + pgvector |
| AI | Google Gemini |
| PDF | PDFBox + local disk |
| Kafka / Gateway | Deferred |
| Process | **TDD** (see [TESTING.md](docs/TESTING.md)) |

## Documentation

**Start:** [docs/README.md](docs/README.md)

| Doc | Role |
|---|---|
| [Vision](docs/VISION.md) | Why |
| [Use Cases](docs/USE-CASES.md) | What (scenarios) |
| [Requirements](docs/REQUIREMENTS.md) | What (normative) |
| [Roadmap](docs/ROADMAP.md) | Phases A–C |
| [Execution Plan](docs/EXECUTION-PLAN.md) | How to build Phase A |
| [Testing / TDD](docs/TESTING.md) | Red→Green→Refactor standards |
| [Architecture](docs/ARCHITECTURE.md) | System design |
| [Services](docs/SERVICES.md) | Service catalog |
| [Frontend](docs/FRONTEND.md) / [Backend](docs/BACKEND.md) | Client & server standards |
| [Messaging](docs/MESSAGING.md) | Async / Kafka target |
| [Security](docs/SECURITY.md) | AuthZ & privacy |
| [ADRs](docs/adr/README.md) | Decisions |
| [Glossary](docs/GLOSSARY.md) | Terms |

## Practical rollout

| Phase | Focus |
|---|---|
| **A** | You as Course Admin; one course; classmate demo |
| **B** | Professor/Fachschaft admins; richer matching |
| **C** | LMS sync; advanced matching / agents |

## Run locally

Java 21, Maven, Node.js, and Docker Desktop.

**One command** (Postgres + identity + learning + Vite), each in its own window:

```powershell
.\dev.ps1              # AI = fake (default)
.\dev.ps1 -Ai vertex   # AI = real Vertex Gemini
.\dev-stop.ps1         # stop ports 8081/8082/5173 + Postgres
```

| Process | URL |
|---|---|
| Web | http://localhost:5173 |
| identity-service | http://localhost:8081 |
| learning-service | http://localhost:8082 |
| Postgres (pgvector) | localhost:5433 (host port avoids a local PostgreSQL on 5432), databases `identity` and `learning` |

### AI profiles (`fake` vs `vertex`)

Learning-service Spring profiles (always with `local`):

| `-Ai` | Profiles | Behavior |
|---|---|---|
| `fake` (default) | `local,fake` | Local stub embeddings + chat (CI-safe) |
| `vertex` | `local,vertex` | Vertex `gemini-embedding-001` + chat via ADC |

For Vertex: run `gcloud auth application-default login --project=uniconai`, and do **not** point `GOOGLE_APPLICATION_CREDENTIALS` at another project's key (`dev.ps1 -Ai vertex` clears it for that window). Re-upload materials after switching so embeddings match the active model.

Manual start (same as before):

```text
docker compose up -d
mvn spring-boot:run -pl services/identity-service
# PowerShell: $env:SPRING_PROFILES_ACTIVE='local,fake'   # or local,vertex
mvn spring-boot:run -pl services/learning-service
cd apps/web && npm install && npm run dev
```

Copy [`.env.example`](.env.example) when you need to override defaults. Demo logins: `admin` / `admin` (platform admin + course admin), `professor` / `professor` (course admin), `student` / `student`, and `student1`–`student4` / same password (students). The longer seed professor remains `admin@uni-companion.local` / `admin-pass-1` (course admin for courses they create).

```text
mvn test
cd apps/web && npm test
```

`mvn test` starts Postgres with Testcontainers, so Docker must be running.

## Production (Google Cloud Ubuntu + GitHub Actions)

The stack is four containers: Postgres (pgvector), identity-service, learning-service, and a Caddy front door that serves the SPA and proxies `/identity` and `/learning`.

### 1. GitHub secrets (CI/CD)

After the VM is bootstrapped, add these repository secrets so every push to `main` deploys:

| Secret | Value |
|---|---|
| `GCP_HOST` | External IP of the Compute Engine VM |
| `GCP_USER` | Usually `ubuntu` (must match the SSH key username) |
| `GCP_SSH_KEY` | Private key that can SSH to the instance |

GitHub Actions runs `mvn test` and `npm test` on every pull request and push. Deploy runs only on `main` after tests pass.

### 2. Google Cloud networking

On the VM (or VPC firewall), allow:

- TCP 22 (SSH; default VPC usually includes this)  
- TCP 80 (`http-server` network tag)  
- TCP 443 (`https-server` network tag)  

Prefer at least **4 GB RAM** (`e2-standard-2` / 8 GB is a good fit).

### 3. First install on the VM

SSH in, then:

```bash
sudo apt-get update && sudo apt-get install -y git
sudo git clone https://github.com/blackwhissh/uni-companion.git /opt/uni-companion
sudo bash /opt/uni-companion/deploy/gcp-bootstrap.sh
```

The script installs Docker, generates `/opt/uni-companion/.env.prod`, and builds the images (first build can take 10–20 minutes).

Open `http://<external-ip>`. Admin password is `SEED_ADMIN_PASSWORD` in `/opt/uni-companion/.env.prod`.

To use real Gemini later: put a Vertex service-account JSON at `/opt/uni-companion/secrets/gcp.json`, set `AI_PROFILE=vertex` and `GOOGLE_APPLICATION_CREDENTIALS=/secrets/gcp.json` in `.env.prod`, then re-run `deploy/remote-deploy.sh`. For HTTPS, set `CADDY_SITE=your.domain` and point DNS at the VM.

## Current status

Admins upload and publish PDFs from the course console. Enrolled students see only materials that are READY and PUBLISHED. Upload stores page text and 768-d embeddings (Vertex `gemini-embedding-001` when configured). Grounded Q&A uses those vectors. Flashcards and quizzes use bounded shared version pools keyed by the exact material set and generation recipe: existing versions are reused before one batched Vertex call creates a new version.
