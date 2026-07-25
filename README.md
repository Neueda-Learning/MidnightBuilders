# MidnightBuilders - Payment Processing System

Team starter repository for the training project described in `payment_processing.md` and `day_one_require.md`.

Current stack:
- Backend: Java + Spring Boot (Maven)
- Database: MySQL 8

## Repository Structure

- `demo1/` - Spring Boot backend service
- `day_one_require.md` - Day 1 requirement document
- `payment_processing.md` - Original training brief
- `docker-compose.yml` - Local MySQL container

## Prerequisites

- Git
- Java 17+
- Docker Desktop (for local MySQL)

## 1) Clone

```powershell
git clone <your-github-repo-url>
Set-Location MidnightBuilders
```

## 2) Start MySQL

Copy env template:

```powershell
Copy-Item .env.example .env
```

Start database:

```powershell
docker compose up -d
```

## 3) Run Backend Tests

```powershell
Set-Location demo1
.\mvnw.cmd test
```

## 4) Run Backend

```powershell
.\mvnw.cmd spring-boot:run
```

Service starts on `http://localhost:8080` by default.

## Team Collaboration Rules

- Branch from `main` using `feature/<name>` naming.
- Open Pull Request for every feature.
- At least one teammate review before merge.
- Keep PRs small and focused.

Detailed flow: `docs/git-workflow.md` and `CONTRIBUTING.md`.

## Initial Milestone (No Business Code Yet)

This repository is initialized for team development with:
- Spring Boot project scaffold
- MySQL local container setup
- Environment template
- Collaboration conventions and docs

Next step is implementing API/domain logic in feature branches.
