# OpenMRS O3 Development Environment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Install and verify the minimal official OpenMRS 3 O3 runtime needed before developing the TF-IDF extension.

**Architecture:** Use the official `openmrs-distro-referenceapplication` Docker Compose stack for database, backend, frontend, and gateway. Keep the current workspace as the custom project and reserve `backend/openmrs-module-tfidf-search` for the module source.

**Tech Stack:** Windows PowerShell, Docker Desktop, Docker Compose, Git, Java 17, Maven, Node.js/npm, OpenMRS 3 O3.

## Global Constraints

- Clone only `https://github.com/openmrs/openmrs-distro-referenceapplication.git` for the first runtime.
- Do not clone or modify `openmrs-core` or existing O3 frontend repositories.
- Use local demo/test data only.
- Verify the runtime through HTTP endpoints and Docker health/status output.

### Task 1: Install and verify Maven

**Files:**
- Modify: machine-level developer tooling only

- [ ] **Step 1: Check the package manager package name**

Run: `winget search --id Apache.Maven`

Expected: a Maven package is listed or an equivalent official Maven package is available.

- [ ] **Step 2: Install Maven**

Run the exact package-manager command returned by the search, accepting the package source terms when prompted.

- [ ] **Step 3: Verify Maven**

Run: `mvn -version`

Expected: Maven prints its version and uses the installed Java runtime.

### Task 2: Clone the official O3 distro

**Files:**
- Create: `openmrs-distro-referenceapplication/` via Git clone

- [ ] **Step 1: Confirm the target does not exist**

Run: `Test-Path .\openmrs-distro-referenceapplication`

Expected: `False`.

- [ ] **Step 2: Clone the repository**

Run: `git clone https://github.com/openmrs/openmrs-distro-referenceapplication.git openmrs-distro-referenceapplication`

- [ ] **Step 3: Inspect Compose configuration**

Run: `docker compose config` from the cloned repository.

Expected: the Compose configuration renders without an error.

### Task 3: Start the O3 runtime

**Files:**
- Use: `openmrs-distro-referenceapplication/docker-compose.yml`

- [ ] **Step 1: Pull and start the official images**

Run: `docker compose up -d` from `openmrs-distro-referenceapplication`.

- [ ] **Step 2: Inspect service state**

Run: `docker compose ps`.

Expected: database, backend, frontend, and gateway containers are created and running or healthy.

- [ ] **Step 3: Inspect startup logs if a service is not ready**

Run: `docker compose logs --tail=200 backend gateway db`.

Expected: logs provide the startup reason; do not delete volumes automatically.

### Task 4: Verify OpenMRS endpoints

**Files:**
- Create: `scripts/verify-openmrs.ps1`

- [ ] **Step 1: Check legacy and O3 URLs**

The script requests `http://localhost/openmrs` and `http://localhost/openmrs/spa`, records HTTP status codes, and fails if either request cannot connect.

- [ ] **Step 2: Check the REST endpoint**

The script requests `http://localhost/openmrs/ws/rest/v1/session` and records the expected unauthenticated response without exposing credentials.

- [ ] **Step 3: Run the verification script**

Run: `powershell -ExecutionPolicy Bypass -File .\scripts\verify-openmrs.ps1`

Expected: endpoint checks complete and print actionable results.

### Task 5: Prepare the customization workspace

**Files:**
- Create: `backend/openmrs-module-tfidf-search/.gitkeep`
- Create: `frontend/.gitkeep`
- Create: `scripts/README.md`

- [ ] **Step 1: Create the module and frontend extension points**

Create the directories without cloning unrelated upstream repositories.

- [ ] **Step 2: Document start/stop commands**

Document `docker compose up -d`, `docker compose ps`, `docker compose logs`, and `docker compose down` with the distro repository as the working directory.

- [ ] **Step 3: Re-run verification**

Run the verification script after the workspace scaffolding is created.
