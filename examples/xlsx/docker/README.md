# Isolated Linux engine test deployment

This setup builds UTLXe from the checked-out GitHub revision. It uses Java 17 on
Linux; it does not build a GraalVM native engine. It does not mount or contact
My Bottle Collection, its database, or its existing containers.

The engine container has a 2 GiB memory limit, no swap allowance and a 1,408 MiB
Java heap. The gateway has a separate 64 MiB limit. A server-wide NGINX counter
allows two active requests and returns HTTP 429 for excess requests. Batch and
pipeline APIs are not exposed. Scratch files use a Docker disk volume; keep an
eye on disk space and memory during tests. Java heap size is not total RSS.

Only `127.0.0.1:18090` is published. The gateway exposes health, transformation
loading and individual transformation execution for trusted local testing.
The engine's Docker network is internal. The gateway also joins a normal bridge
network so Docker can publish its localhost port. Only the gateway has outbound
network access. Do not add this service to the public website proxy. This is a
test deployment, not an authenticated production API.

## Deploy from GitHub in PuTTY

Use an account with Docker access, or prefix Docker commands with `sudo`.
These commands require Git, Python 3, Docker Engine and Docker Compose v2.
The image build downloads Gradle dependencies and Linux packages. Allow several
GiB of disk space and additional memory for the build; the 2 GiB limit applies
to the running engine, not Docker's image builder.

Check the host first:

```sh
whoami
free -h
df -h /
docker version
docker compose version
docker ps --format 'table {{.Names}}\t{{.Ports}}'
ss -ltn | grep ':18090 ' || true
```

If port 18090 is occupied, stop here and select another localhost port in
`compose.yaml`. Use a new checkout directory so existing deployments are untouched:

```sh
mkdir -p "$HOME/utlx-testing"
cd "$HOME/utlx-testing"
git clone --filter=blob:none --sparse --branch development https://github.com/Eikel1980/utl-x.git xlsx
cd xlsx
git sparse-checkout set modules formats stdlib schema proto gradle scripts examples/xlsx
# Replace this value with the exact published revision supplied with the deployment instructions.
DEPLOY_COMMIT=REPLACE_WITH_PUBLISHED_COMMIT
git checkout --detach "$DEPLOY_COMMIT"
git status --short
git rev-parse HEAD
docker compose -f examples/xlsx/docker/compose.yaml config --quiet
docker compose -f examples/xlsx/docker/compose.yaml build --pull
docker compose -f examples/xlsx/docker/compose.yaml up -d --wait
curl --fail --silent --show-error http://127.0.0.1:18090/api/health
```

An empty `git status --short` and the expected commit identify the source used
for the build. If the fork is private, use an existing authorized GitHub login
on the VPS; keep credentials out of the URL and terminal command history.

## Run Linux checks

Run the HTTP tests from the checkout on the VPS. They use generated sample
inventory only and load their own temporary test transformations:

```sh
python3 examples/xlsx/docker/smoke-test.py
python3 examples/xlsx/docker/smoke-test.py --rows 10000
docker stats --no-stream
docker compose -f examples/xlsx/docker/compose.yaml logs --tail=80
```

The HTTP test exercises all three strategies, workbook output, two simultaneous
imports, Unicode, null/zero/false values and invalid-workbook rejection. To test
dates, cached formulas and the other CLI fixtures inside the Linux image:

```sh
docker compose -f examples/xlsx/docker/compose.yaml exec engine \
  python3 /app/test-xlsx-cli.py /opt/java/openjdk/bin/java --jar /app/utlx.jar --rows 10000
```

The HTTP concurrency test verifies two successful overlapping client calls. The
gateway configuration is responsible for rejecting additional requests; verify
HTTP 429 under a sustained three-client load before relying on admission limits.
The test containers are ephemeral: restart the HTTP smoke test after restarting
the engine to load its transformations again.

## Stop without affecting the website

```sh
docker compose -f examples/xlsx/docker/compose.yaml down
```

This stops only the `utlx-xlsx-test` Compose project and retains its scratch
volume. There are no changes to My Bottle Collection or its deployment files.

## Recover an internal-only gateway deployment

The initial configuration connected the gateway only to an internal network.
Docker could start both containers while omitting the requested host port.
The corrected configuration attaches only the gateway to an additional normal
bridge network. Its port remains bound to `127.0.0.1`.

After checking out the networking correction from GitHub, recreate the gateway
with the existing engine image:

```sh
sudo docker compose -f examples/xlsx/docker/compose.yaml config --quiet
sudo docker compose -f examples/xlsx/docker/compose.yaml up -d --no-build --no-deps --force-recreate --wait gateway
sudo docker compose -f examples/xlsx/docker/compose.yaml port gateway 8080
curl --fail --silent --show-error http://127.0.0.1:18090/api/health
```

The port command should report `127.0.0.1:18090`. If access still fails, collect
container status and logs without restarting Docker or changing host firewall rules:

```sh
sudo docker compose -f examples/xlsx/docker/compose.yaml ps -a
sudo docker compose -f examples/xlsx/docker/compose.yaml logs --tail=80 gateway engine
```
