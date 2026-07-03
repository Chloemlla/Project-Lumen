# Backend Deployment Security

> Concrete deployment contract for backend request signing and client security env wiring.

## Scenario: Request Signing Secret Parity

### 1. Scope / Trigger

- Trigger: backend deployment and Android release builds both participate in the same HMAC request-signing contract.
- Applies when changing backend deployment workflows, Android release build envs, request-signing middleware, or production API secrets.

### 2. Signatures

- Backend env: `LUMEN_REQUEST_SIGNING_SECRET`.
- Android release build env: `PROJECT_LUMEN_REQUEST_SIGNING_SECRET`.
- Backend middleware reads `X-Lumen-Timestamp`, `X-Lumen-Nonce`, and `X-Lumen-Signature`.
- Android client signs the canonical request payload and sends the same three headers on API requests.

### 3. Contracts

- `LUMEN_REQUEST_SIGNING_SECRET` and `PROJECT_LUMEN_REQUEST_SIGNING_SECRET` must carry the same secret value for the same production API environment.
- The local default `project-lumen-local-request-signing-key` is allowed only for local development or deliberately matched non-production testing.
- Backend deployment workflows must pass the GitHub Actions secret `PROJECT_LUMEN_REQUEST_SIGNING_SECRET` into the running container as `LUMEN_REQUEST_SIGNING_SECRET`.
- If `LUMEN_REQUIRE_REQUEST_SIGNING=true`, unsigned requests or signatures generated with a different secret must fail closed.
- `LUMEN_REQUIRE_PLAY_INTEGRITY` is independent from request signing; enabling it requires clients to be able to attach `X-Lumen-Integrity`.

### 4. Validation & Error Matrix

- Missing `X-Lumen-Timestamp`, `X-Lumen-Nonce`, or `X-Lumen-Signature` while signing is required -> HTTP 403.
- Timestamp outside `LUMEN_REQUEST_TIMESTAMP_SKEW_SECONDS` -> HTTP 403.
- Empty backend signing secret while signing is required -> HTTP 403.
- Android/backend signing secret mismatch -> HTTP 403 on otherwise valid signed requests.
- Reused nonce within the accepted timestamp window -> HTTP 403.
- Missing or too-short `X-Lumen-Integrity` while Play Integrity is required for that path -> HTTP 403.

### 5. Good/Base/Bad Cases

- Good: Android release and backend deployment both receive the same production request-signing secret from GitHub Actions secrets.
- Base: local backend and local/debug client both use the documented development fallback secret.
- Bad: Android release is built with `PROJECT_LUMEN_REQUEST_SIGNING_SECRET`, but backend deployment omits `LUMEN_REQUEST_SIGNING_SECRET`; login starts fail with HTTP 403 even when diagnostics report `signed=true`.

### 6. Tests Required

- GitHub workflow review: backend deployment env includes `LUMEN_REQUEST_SIGNING_SECRET: ${{ secrets.PROJECT_LUMEN_REQUEST_SIGNING_SECRET }}`.
- Backend route/security tests: a request signed with the configured secret succeeds, and the same request signed with a different secret returns HTTP 403.
- Android signing tests: canonical payload construction remains aligned with backend canonicalization for method, path, query, body hash, timestamp, and nonce.

### 7. Wrong vs Correct

#### Wrong

```yaml
env:
  IMAGE_URL: ${{ needs.build-image.outputs.deploy_image }}
```

#### Correct

```yaml
env:
  IMAGE_URL: ${{ needs.build-image.outputs.deploy_image }}
  LUMEN_REQUEST_SIGNING_SECRET: ${{ secrets.PROJECT_LUMEN_REQUEST_SIGNING_SECRET }}
```

The correct path keeps production request signatures verifiable by the backend without weakening the security middleware.
