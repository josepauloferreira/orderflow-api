# Release candidate process — explicit and auditable

**No automatic deploy, GitHub Release creation, registry push or change to production resources.**

## Pull request gates

Every PR runs `ci.yml` (Java 25 Maven/Testcontainers, Docker + real Keycloak, bounded k6/lock/restore exercise) and `supply-chain.yml`.

The security workflow performs:

1. GitHub dependency review on the pull-request diff: fail for newly introduced HIGH/CRITICAL known vulnerable dependencies.
2. A local image build, without publishing to a registry.
3. An SPDX JSON image SBOM generated using Syft through Anchore's action.
4. A Trivy HIGH/CRITICAL findings report uploaded as a short-lived artifact. High findings are **informational initially**, but must be reviewed before any release.
5. A separate Trivy gate that fails for **fixable CRITICAL** image vulnerabilities. Known unfixed findings remain a risk, not an approval.

External vulnerability feeds can change between runs. A green scan is not a security certification. Action versions should eventually be pinned to verified commit SHA digests.

## Build a manual candidate

1. Merge the reviewed PR after all CI and supply-chain jobs are successful.
2. From GitHub Actions, select **Build release candidate**.
3. Choose the **main** branch and a version such as `0.1.0-rc.1`.
4. The workflow checks the version grammar, changes the Maven version in the ephemeral runner workspace, runs the complete Maven verification suite, and builds a matching candidate JAR and Docker image.
5. It generates SHA-256 checksums for the JAR, an SPDX JSON image SBOM, and a Trivy critical scan. The release **fails** if a fixable critical image finding exists.
6. GitHub's artifact attestation signs **JAR build provenance** using short-lived workload identity. The workflow stores the JAR, SBOM, scan results, checksum and source SHA as a 14-day GitHub Actions artifact.
7. No Docker image is uploaded to GHCR or deployed. To distribute images with immutable digests and signatures, a separate authenticated release process must be deliberately designed.

The workflow only accepts candidate versions of the form `MAJOR.MINOR.PATCH-rc.NUMBER`, for example `0.1.0-rc.1`. It does **not** create or push Git tags. The resulting artifact is traceable to a source commit SHA.

If changing the Maven version in the ephemeral workspace creates a different dependency resolution or executable packaging behavior, the release verification job must detect it before attesting the JAR.

## Independent verification

Download the uploaded candidate artifact and verify the JAR against `SHA256SUMS`:

```bash
cd <extracted-candidate-artifact-directory>
sha256sum --check SHA256SUMS
```

Verify the remote GitHub attestation with the GitHub CLI when the candidate run exists (this uses the repository's attestation store):

```bash
gh attestation verify orderflow-api-0.1.0-rc.1.jar \
  --repo josepauloferreira/orderflow-api
```

Do not assert provenance verification without actually executing these commands. A checksum proves byte integrity relative to a particular checksum file, not that the package is safe or trustworthy.

## Before real deployment

Deployment needs a separate design for approved HTTPS OIDC, managed secrets, locked-down network exposure, controlled DB migrations and rollback, SLOs, rate limits, idempotency retention, backups and restoration on another host, an image registry with pinned image digests and image signatures, a staging smoke test and an explicit human approval gate.

Document the artifact SHA, Git commit, resolved dependencies, scanner findings and environment variables (never their secret values) for each actual release.
