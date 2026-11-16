# Linux audio-latency benchmark image for AudioLatencyComparisonTest.
# Mirrors the macOS host toolchain (JDK 21, rustc 1.95, cmake 3) so the
# Kotlin vs Rust hang-publish comparison runs end-to-end against the
# same set of sidecars, just on a different OS.

FROM eclipse-temurin:21-jdk-jammy

# rustup pulls 1.95.0 explicitly. cmake/build-essential/pkg-config/libssl-dev
# are needed by transitive *-sys crates (audiopus_sys bundles libopus and
# builds it via cmake; rustls→aws-lc-sys also uses cmake).
RUN apt-get update && apt-get install -y --no-install-recommends \
        curl ca-certificates git \
        build-essential pkg-config cmake \
        libssl-dev \
        bash \
    && rm -rf /var/lib/apt/lists/*

ENV RUSTUP_HOME=/opt/rustup \
    CARGO_HOME=/opt/cargo \
    PATH=/opt/cargo/bin:$PATH

# Pin rustc 1.95.0 to match the host. moq-relay v0.10.25's transitive
# Cargo.lock requires rustc >= 1.95 (constant_time_eq@0.4.3).
RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs -o /tmp/rustup.sh \
    && sh /tmp/rustup.sh -y --default-toolchain 1.95.0 --profile minimal \
    && rm /tmp/rustup.sh \
    && rustc --version && cargo --version

# CMake 4.x stripped support for very old `cmake_minimum_required` floors,
# which audiopus_sys's bundled libopus CMakeLists trips over. Jammy ships
# cmake 3.22 (still affected by some old crate `*-sys` requirements but
# tolerates the policy). Set the env var defensively to match the
# gradle Exec tasks.
ENV CMAKE_POLICY_VERSION_MINIMUM=3.5

WORKDIR /repo
