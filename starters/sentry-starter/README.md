# Sentry Starter

Error tracking and performance tracing via [Sentry](https://sentry.io), pre-wired for the softa runtime. The starter brings three things on top of the official `sentry-spring-boot-4-starter`:

1. **Context-aware events** — every Sentry event and transaction is tagged with `trace_id` / `tenant_id` / `user_id` from the softa `Context`, so issues can be filtered per tenant and cross-referenced with log lines carrying the same `traceId` (softa-web mirrors the Context into SLF4J's MDC for the duration of each request).
2. **Log-based capture** — `sentry-logback` is included, so anything the application logs at ERROR level with a throwable becomes a Sentry event. This is the capture channel that works with softa's `WebExceptionHandler`, which handles every exception itself (an MVC-level interceptor would never see them). Business exceptions logged at WARN/INFO stay out by design.
3. **Switchable JDBC spans** — per-statement database spans via P6Spy, off by default and free when off (no proxy installed).

## Usage

```xml
<dependency>
    <groupId>io.softa</groupId>
    <artifactId>sentry-starter</artifactId>
</dependency>
```

The SDK stays **fully disabled until a DSN is configured** — depending on this starter costs nothing in environments that do not use Sentry (local development, tests).

## Configuration

All standard `sentry.*` properties apply (relaxed binding: `SENTRY_DSN` ⇔ `sentry.dsn`). The ones that matter:

| Property / env var | Default | Meaning |
| --- | --- | --- |
| `SENTRY_DSN` | unset (SDK disabled) | The project DSN from sentry.io. |
| `SENTRY_ENVIRONMENT` | unset | Environment label, e.g. `uat` / `prod`. |
| `SENTRY_RELEASE` | unset | Release identifier; set it to the deployed image tag so regressions map to versions. |
| `SENTRY_TRACES_SAMPLE_RATE` | `0` (tracing off) | Fraction of requests recorded as transactions, e.g. `0.1`. |
| `SOFTA_SENTRY_JDBCTRACING_ENABLED` (`softa.sentry.jdbc-tracing.enabled`) | `false` | Wrap DataSources in P6Spy and record a span per SQL statement (on sampled requests only). |

## Complete configuration example

Split the configuration by what varies. Static policy is committed with the app in `application.yml`; per-environment values come from the environment — relaxed binding maps `SENTRY_DSN` ⇔ `sentry.dsn`, `SOFTA_SENTRY_JDBCTRACING_ENABLED` ⇔ `softa.sentry.jdbc-tracing.enabled`.

**application.yml** — environment-independent defaults:

```yaml
sentry:
  # Data-protection red line — never attach cookies, user IP or request bodies.
  send-default-pii: false
  # Release identifier: taken from the image tag the platform injects, so issues
  # and regressions map to deployed versions with no extra plumbing.
  release: ${APP_IMAGE_TAG:}
  logging:
    # Defaults shown, spelled out for discoverability: ERROR logs with a throwable
    # become events; INFO and above are recorded as breadcrumbs on those events.
    minimum-event-level: error
    minimum-breadcrumb-level: info
```

**Per environment** — e.g. the server's `.env` / container environment:

```bash
# The DSN is the master switch: without it everything below is inert.
SENTRY_DSN=https://<key>@o<org-id>.ingest.us.sentry.io/<project-id>
SENTRY_ENVIRONMENT=uat
# Fraction of requests recorded as transactions (0 = error tracking only).
# UAT: 1.0 is fine. Prod: start at 0.1–0.2 and adjust to quota.
SENTRY_TRACES_SAMPLE_RATE=1.0
# Optional per-statement SQL spans (P6Spy proxy) — see the overhead notes below.
SOFTA_SENTRY_JDBCTRACING_ENABLED=true
```

The same configuration expressed entirely in a profile, for setups that prefer `application-<profile>.yml` over environment variables:

```yaml
sentry:
  dsn: https://<key>@o<org-id>.ingest.us.sentry.io/<project-id>
  environment: prod
  traces-sample-rate: 0.1

softa:
  sentry:
    jdbc-tracing:
      enabled: false
```

Troubleshooting: set `SENTRY_DEBUG=true` and the SDK logs why events are (or are not) being sent.

## PII

`send-default-pii` defaults to `false` and should stay that way for applications holding personal data. Exception *messages* still travel to Sentry — keep personal data out of them (this is good logging hygiene regardless). Server-side scrubbing rules on sentry.io are the second line of defense.

## JDBC tracing overhead

With the switch off, nothing is wrapped — zero overhead. With it on, every JDBC call goes through one extra proxy invocation (microseconds; relevant only in hot loops issuing thousands of tiny statements), and span objects are allocated only for requests selected by `traces-sample-rate`. Enable it on UAT freely; on production, decide based on the sample rate and statement volume.

## Distributed tracing

The Sentry browser/Next.js SDKs attach `sentry-trace` / `baggage` headers to same-origin API calls; the backend SDK continues those traces automatically. Frontend and backend releases then share one trace view in Sentry — no extra configuration on this side.
