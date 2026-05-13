# Bloom Study (PDF study backend)

Spring Boot app that turns uploaded PDFs into guided study sessions (summaries, sections, quizzes). Static UI lives under `backend/src/main/resources/static/`.

## Requirements

- **Java 17** (see `backend/pom.xml`)
- **Maven** (or use the included `backend/mvnw.cmd` / `backend/mvnw` wrapper)

## Run locally

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Default profile is **H2** (file DB under `backend/data/`, gitignored). The app listens on **8081** by default (`application.properties`).

Open [http://localhost:8081/](http://localhost:8081/) in a browser.

## Secrets and local config

**Do not commit API keys.** This repo ignores:

- `.env` and `.env.*`
- `backend/application-local.properties`
- Local DB files (`backend/data/`, `*.mv.db`, `*.trace.db`)
- `backend/uploads/`

For OpenRouter, set **`OPENROUTER_API_KEY`** in your environment, or copy `backend/application-local.properties.example` to `backend/application-local.properties` and set `openrouter.api.key=` there (that file stays local only).

```powershell
# PowerShell example (session only)
$env:OPENROUTER_API_KEY = "your-key-here"
cd backend
.\mvnw.cmd spring-boot:run
```

## PostgreSQL (optional local / production)

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=postgres"
```

Configure JDBC URL and credentials via environment variables **`DATABASE_URL`**, **`DATABASE_USERNAME`**, **`DATABASE_PASSWORD`** (see `backend/src/main/resources/application-postgres.properties`) or uncomment overrides in `application-local.properties`.

## Publish to GitHub

1. Create an empty repository under your account (no need to add a README if this repo already has one).
2. From the **project root** (`adhd_pdf_project`):

   ```powershell
   git init
   git add .
   git status
   ```

   Confirm **`application-local.properties`**, **`.env`**, **`backend/data/`**, and **`backend/target/`** do **not** appear in `git status`.

3. Commit and push:

   ```powershell
   git commit -m "Initial commit: Bloom Study backend"
   git branch -M main
   git remote add origin https://github.com/YOUR_USER/YOUR_REPO.git
   git push -u origin main
   ```

Replace `YOUR_USER/YOUR_REPO` with your real GitHub path.

## Deploy on Render (outline)

- Create a **Web Service**; root directory **`backend`**; build e.g. `.\mvnw.cmd -B -DskipTests package` (or `./mvnw -B -DskipTests package` on Linux); start with `java -jar target/study-backend-0.0.1-SNAPSHOT.jar` (confirm exact JAR name under `backend/target/` after a local build).
- Add **environment variables** in the Render dashboard: at minimum **`OPENROUTER_API_KEY`**, and for Postgres profile **`SPRING_PROFILES_ACTIVE=postgres`** plus **`DATABASE_URL`** / username / password as required by your `application-postgres.properties`.
- Use **Render PostgreSQL** (or another managed DB) for production; treat the container disk as ephemeral unless you attach persistent storage for uploads.

## License

Add a `LICENSE` file when you choose a license for the project.
