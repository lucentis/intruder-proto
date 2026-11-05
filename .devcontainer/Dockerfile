FROM mcr.microsoft.com/devcontainers/java:1-17-bookworm

ARG ANDROID_CMDLINE_TOOLS_VERSION=15859902
ARG ANDROID_CMDLINE_TOOLS_SHA256=4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583

ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}

USER root

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl unzip \
    && rm -rf /var/lib/apt/lists/*

RUN set -eux; \
    archive="commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_VERSION}_latest.zip"; \
    curl --fail --location --retry 3 \
      "https://dl.google.com/android/repository/${archive}" \
      --output "/tmp/${archive}"; \
    echo "${ANDROID_CMDLINE_TOOLS_SHA256}  /tmp/${archive}" | sha256sum --check -; \
    mkdir -p "${ANDROID_HOME}/cmdline-tools/latest"; \
    unzip -q "/tmp/${archive}" -d /tmp/android-cli; \
    cp -a /tmp/android-cli/cmdline-tools/. "${ANDROID_HOME}/cmdline-tools/latest/"; \
    rm -rf "/tmp/${archive}" /tmp/android-cli; \
    yes | sdkmanager --sdk_root="${ANDROID_HOME}" --licenses >/dev/null; \
    sdkmanager --sdk_root="${ANDROID_HOME}" \
      "platform-tools" \
      "platforms;android-36" \
      "build-tools;36.0.0"; \
    chown -R vscode:vscode "${ANDROID_HOME}"

USER vscode
