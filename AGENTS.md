# Multimodal Data Platform

MMDP is an MVP repository for a multimodal data platform with a Vue frontend and a Spring Boot backend.

## Repository Layout

- `mmdp-frontend/`: Vue 3 + Vite frontend
- `mmdp-backend/`: Spring Boot backend
- `docs/`: project notes, MVP scope, and version docs
- `container-bat/`: local helper scripts, including MinIO startup

## Monorepo Convention

The frontend and backend live in a single repository during the MVP phase so we can keep API changes, UI changes, and docs aligned in one place.

## What Is Not Committed

The repository does not commit:

- real database or MinIO credentials
- local environment files
- generated frontend artifacts
- Maven build output and local dependency caches
- runtime data under `data/`

## Frontend

Install and run:

```bash
cd mmdp-frontend
npm install
npm run dev
```

The frontend reads `VITE_API_BASE_URL`. For local development you can create `.env.development` from `.env.development.example`.

## Backend

Install and run:

```bash
cd mmdp-backend
mvn spring-boot:run
```

Configuration is stored in `src/main/resources/application.yml` with safe defaults and environment-variable overrides:

- `MMDP_DB_URL`
- `MMDP_DB_USERNAME`
- `MMDP_DB_PASSWORD`
- `MMDP_MINIO_ENDPOINT`
- `MMDP_MINIO_ACCESS_KEY`
- `MMDP_MINIO_SECRET_KEY`
- `MMDP_MINIO_BUCKET`

See [mmdp-backend/README.md](D:/project/multimodal-data-platform/mmdp-backend/README.md) for backend-specific setup details.

