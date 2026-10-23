# ADR 0008 — Verified release candidates without automatic production deployment

- **Status:** Accepted
- **Date:** 2026-10-23

## Context

A professionally presented reference backend should demonstrate how source code becomes a traceable binary and how dependencies and base images are reviewed. Adding automatic production deployment would be premature because public OIDC, operational SLOs, backups and retention policies are not yet defined.

## Decision

- On pull requests, run GitHub dependency review, build an unpublished local candidate image, generate SPDX SBOM evidence, report high/critical Trivy image findings and **fail on fixable critical findings**.
- Offer a **manual** candidate workflow on `main`. A human supplies an `x.y.z-rc.n` version; the runner updates POM version only within its ephemeral working copy, executes `mvn verify`, builds a JAR and container image, records the source SHA and JAR checksum and generates the image SBOM.
- Request GitHub OIDC credentials with only the job-specific permissions required for a JAR build attestation. Provenance attestation is about the JAR, **not** a signature over an uploaded container image.
- Upload candidate artifacts to the workflow run for short-term inspection; do not create tags, registry pushes, releases, or deployments.
- Keep the successful stage-8 tests and their artifacts as supporting evidence, without extrapolating short smoke thresholds to capacity numbers.

## Trade-offs

Scanning can fail when vulnerability databases update, even without source changes; that is intentional evidence requiring triage. HIGH container findings remain report-only initially, whereas newly introduced HIGH dependency findings block. This policy does **not** prove zero vulnerability exposure. Attestation and SBOM aid traceability, not license approval or production security assurance.

A real release pipeline would additionally need signed immutable OCI image distribution, vulnerability-exception policy, approved dependency licensing, staging smoke validation, secret/identity management, database upgrade rehearsal and documented deploy/rollback governance.
