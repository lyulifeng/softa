
# 1. Build multi-platform images

## 1.1 Build & Push by GitHub Actions
You can use the GitHub Actions workflow or any other CI tools, to build and push multi-platform images to Docker container registry.
Refer to the `.github/workflows/build-and-push.yml` file for more details.
Trigger build action on push of tags starting with "v", e.g., v1.0.0

`./deploy/Dockerfile` is a multi-stage Dockerfile with three stages:
1. **fonts** — downloads Noto font files via `deploy/install-font.sh` (cached layer, rarely changes)
2. **build_stage** — compiles the application jar with Maven
3. **final image** — copies the fonts and jar into a minimal JRE image

The `APP_PATH`, `APP_NAME` parameters are passed as build arguments in the GitHub Actions workflow.
The image `version` is extracted from the tag name e.g., `v1.0.0` -> `1.0.0`.

`./deploy/Dockerfile.ci` is a two-stage variant for CI pipelines where the jar is pre-built. It includes the same font installation stage.

## 1.2 Build & Push by Local Script Manually
Build images and push them to Docker container registry.
Specify the `APP_PATH`, `APP_VERSION` to build the image for the Java application.
```
./deploy/build.sh <APP_PATH> <APP_VERSION> [<REGISTRY_NAMESPACE>]
```
The `APP_PATH` is the relative path to the application source code directory, such as `apps/demo-app`.
And the last name in `APP_PATH` is the application name, such as `demo-app`.

The `REGISTRY_NAMESPACE` is optional, and the default value is `softa`.
You can specify the `REGISTRY_NAMESPACE` to push the image to your own docker image repository.

Example to build the demo application image:
```bash
./deploy/build.sh apps/demo-app 1.0.3
```

# 2. Install Noto Fonts (PDF Generation)
Noto fonts are required for PDF generation with `file-starter` (RICH_TEXT document templates).
The fonts are installed automatically inside Docker images (see above). For local development, run:
```bash
sh deploy/install-font.sh
```
The script auto-detects the OS font directory (`~/Library/Fonts` on macOS, `~/.local/share/fonts` on Linux).
You can also pass an explicit target directory:
```bash
sh deploy/install-font.sh /path/to/fonts
```

# 3. Start EFK by Docker Compose (Optional)
```bash
docker compose -f deploy/efk/docker-compose.yml up -d
```
Access the Kibana console at http://localhost:5601

The stack bootstraps the change-log index automatically: a one-shot `setup` service applies `deploy/init_es/changelog-index.json` (index name defaults to `dev_demo_change_log`; override with `SPRING_ELASTICSEARCH_INDEX_CHANGELOG`).

Or you can specify the `spring.elasticsearch.uris` property to connect to your own Elasticsearch cluster — then apply `deploy/init_es/changelog-index.json` yourself before the first change log is written (otherwise ES auto-creates the index with dynamic mapping and eventually hits the total-fields limit).

# 4. Start Pulsar by Docker Compose (Optional)
```bash
docker compose -f deploy/pulsar/docker-compose.yml up -d
```
Access the Pulsar console at http://localhost:8080

Or you can specify the `spring.pulsar.client.service-url` property to connect to your own Pulsar cluster.

The most crucial information is you need to configure the `mq.topics.xxx.topic` properties to enable the corresponding Listeners.

On the other hand, if you are not ready to set up the pulsar service, you can also choose not to configure
or comment out the `mq.topics.xxx` topics to avoid the issue of being unable to start.

# 5. Start Minio by Docker Compose (Optional)
```bash
docker compose -f deploy/minio/docker-compose.yml up -d
```
### Minio API Endpoints
http://localhost:9000

### Minio Web UI Dashboard
http://localhost:9001
Username: minioadmin
Password: minioadmin

# 6. Start the demo application by Docker Compose
```bash
docker compose -f ./deploy/demo-app/docker-compose.yml up -d
```
No SQL seeding is required: on an empty database the boot reconciler first creates the `sys_*` catalog tables from their own annotations, then the `["*"]`-scope scanner creates every business table. For a reviewable reference DDL (DB-first workflows, DBA review), generate one on demand: `REGEN_BASELINE=true mvn test -pl apps/demo-app -Dtest=MetadataBaselineDdlGenerator` (writes `deploy/init_mysql/1.Metadata.ddl.sql`, intentionally untracked).

# 7. Production Environment
It is highly recommended that the production environment be deployed using a pipeline and Kubernetes for containerization.

## 7.1 Observability

The compose files in this directory are development stacks: each pins the `json-file` driver with rotation, because Docker's default never rotates and a long-running local stack will fill the disk.

On a server, do **not** pin the driver per service. Docker validates log options per driver, so one `logging:` block cannot serve both a laptop and a log platform, and making it conditional forces an overlay file that has to be remembered on every manual `up`. Configure `/etc/docker/daemon.json` instead — one setting per host, covering every container on it, with nothing to forget. Any driver that ships logs off-host must also set `mode: non-blocking` with a `max-buffer-size`: the default is *blocking*, so a slow destination stalls the container's writes and takes application threads with it.

The application side — ECS-structured JSON logs, the `traceId` / `tenantId` / `userId` fields the framework puts in the MDC, error tracking and tracing through `sentry-starter` — is documented in the Observability guide (`docs/backend_dev/observability` on the documentation site), together with both `daemon.json` recipes.