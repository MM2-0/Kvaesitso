# Multi-stage image for the geode Nostr relay.
#
# Build from the REPO ROOT (the build needs the whole Gradle project), pointing
# at this file:
#
#   docker build -f geode/Dockerfile -t geode:local .
#   docker run --rm -p 7447:7447 geode:local
#
# That runs the relay on 0.0.0.0:7447 with the built-in in-memory store — events
# vanish on restart. For a persistent, configured deployment, mount a config and
# a data volume:
#
#   docker run -d --name geode -p 7447:7447 \
#     -v $PWD/geode.toml:/etc/geode/geode.toml:ro \
#     -v geode-data:/var/lib/geode \
#     geode:local --config /etc/geode/geode.toml
#
# with `file = "/var/lib/geode/events.db"` and `in_memory = false` in geode.toml.
#
# Published to GHCR on every release by .github/workflows/create-release.yml
# (docker-geode job) as ghcr.io/vitorpamplona/geode:<version> and :latest.

# --- build stage ------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
# Copy the whole repo (geode depends only on quartz, but the Gradle build needs
# settings.gradle.kts, the version catalog, and the wrapper). .dockerignore keeps
# build outputs and VCS metadata out of the context.
COPY . .
# installDist emits bin/geode + lib/*.jar under geode/build/install/geode.
RUN ./gradlew --no-daemon :geode:installDist

# --- runtime stage ----------------------------------------------------------
FROM eclipse-temurin:21-jre
LABEL org.opencontainers.image.source="https://github.com/vitorpamplona/amethyst"
LABEL org.opencontainers.image.description="geode — a standalone Nostr relay"
LABEL org.opencontainers.image.licenses="MIT"

# Run as a non-root user that owns the data dir.
RUN groupadd --system geode \
 && useradd --system --gid geode --home-dir /var/lib/geode --shell /usr/sbin/nologin geode \
 && mkdir -p /var/lib/geode \
 && chown geode:geode /var/lib/geode

COPY --from=build /src/geode/build/install/geode /opt/geode
# Ship the example config for reference inside the image.
COPY --from=build /src/geode/config.example.toml /opt/geode/share/geode/config.example.toml
ENV PATH="/opt/geode/bin:${PATH}"

VOLUME /var/lib/geode
EXPOSE 7447
USER geode

# Liveness: the default WebSocket port accepts a TCP connection. Uses bash's
# /dev/tcp (the temurin image is Ubuntu-based) so no curl/nc is needed. Override
# the port with --port and this HEALTHCHECK together if you remap it.
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/7447' || exit 1

ENTRYPOINT ["/opt/geode/bin/geode"]
CMD ["--host", "0.0.0.0", "--port", "7447"]
