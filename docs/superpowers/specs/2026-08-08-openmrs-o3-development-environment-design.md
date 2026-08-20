# OpenMRS O3 Development Environment Design

## Goal

Run the official OpenMRS 3 O3 Reference Application locally with Docker and keep the TF-IDF project ready for a custom backend module, without cloning or modifying OpenMRS core.

## Scope

The first milestone is a reproducible local runtime. It includes the official O3 distro repository, its Docker Compose stack, health checks, and a clean extension point for the `tfidfsearch` module. It does not include TF-IDF implementation or a custom O3 frontend yet.

## Architecture

The `openmrs-distro-referenceapplication` repository is the only upstream repository cloned for the runtime. Its Compose stack supplies MariaDB, OpenMRS backend, O3 frontend, and gateway. The current `tfidf-openmrs` workspace remains the project repository and will later contain the Java module source and optional O3 microfrontend.

The module will integrate through OpenMRS extension mechanisms and REST APIs. OpenMRS core and existing O3 microfrontend repositories are dependencies of the distro images, not source repositories required for the first boot.

## Repository Policy

- Clone: `https://github.com/openmrs/openmrs-distro-referenceapplication.git`
- Keep local: `C:\src\tfidf-openmrs`
- Do not clone initially: `openmrs-core`, `openmrs-esm-core`, patient chart, or unrelated O3 application repositories.
- Do not edit upstream core code.

## Runtime Acceptance Criteria

- Docker Compose reports all required services running.
- `http://localhost/openmrs` responds with the legacy application.
- `http://localhost/openmrs/spa` responds with O3.
- OpenMRS REST endpoint responds through the gateway.
- The environment can be stopped and started using the distro repository's Compose commands.
- The workspace contains documented commands and a future module location.

## Constraints

- Use demo/test data only.
- Keep the first runtime installation minimal.
- Record exact tool versions and verification results.
- Avoid production SSL configuration for local development.
