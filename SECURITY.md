# Security Policy

## Supported versions

Supabase KMP is pre-1.0; security fixes are made against the latest released
`0.x` version. Please always upgrade to the latest release before reporting.

## Reporting a vulnerability

**Do not open a public issue for security vulnerabilities.**

Instead, report privately via GitHub's
[private vulnerability reporting](https://github.com/AndroidPoet/supabase-kmp/security/advisories/new)
(Security → Report a vulnerability). If that is unavailable, contact the
maintainer directly through their GitHub profile.

Please include:

- affected module(s) and version,
- a description and, ideally, a minimal reproduction,
- the impact you foresee.

We aim to acknowledge reports within 5 business days and to ship a fix or
mitigation as quickly as the severity warrants.

## Supply chain

Releases are built and verified in CI before publishing:

- **Signed artifacts** — every artifact on Maven Central is GPG-signed.
- **Wrapper validation** — the committed `gradle-wrapper.jar` is checked against
  known-good Gradle checksums on every push and pull request.
- **Dependency review + graph** — the transitive dependency graph is submitted to
  GitHub (enabling Dependabot alerts), and pull requests are blocked if they add a
  dependency with a known high-severity vulnerability.
- **Static analysis** — CodeQL scans the Kotlin/Java sources on every push, pull
  request, and weekly.
- **SBOM + provenance** — each release ships a CycloneDX SBOM (attached as a
  release asset) plus signed [build-provenance and SBOM
  attestations](https://docs.github.com/en/actions/security-guides/using-artifact-attestations).
  Verify a downloaded JAR with:

  ```bash
  gh attestation verify <artifact>.jar --repo AndroidPoet/supabase-kmp
  ```

## Scope / things to keep in mind

This is a client SDK. Some responsibilities sit with the integrating app:

- **Session persistence** — provide a secure `SessionStorage`/`KeyValueStore`
  backed by the platform keystore (Keychain, EncryptedSharedPreferences, …).
  The default `InMemorySessionStorage` keeps tokens in memory only.
- **`supabase-auth-admin`** uses the service-role key and must never be shipped
  in an anon-key client. Use it only in trusted server-side contexts.
- **Logging** — `logging = true` redacts `Authorization`/`apikey` headers, but
  avoid enabling verbose logging in production builds regardless.
