#!/usr/bin/env sh

set -eu

GRADLE_VERSION="7.6.3"
DISTRIBUTION_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)"
WRAPPER_DIR="${SCRIPT_DIR}/.gradle-wrapper"
DIST_ZIP="${WRAPPER_DIR}/gradle-${GRADLE_VERSION}-bin.zip"
GRADLE_HOME="${WRAPPER_DIR}/gradle-${GRADLE_VERSION}"
GRADLE_LAUNCHER="${GRADLE_HOME}/bin/gradle"

mkdir -p "${WRAPPER_DIR}"

download_distribution() {
    printf 'Downloading Gradle %s...\n' "${GRADLE_VERSION}" >&2
    if command -v curl >/dev/null 2>&1; then
        curl -fLso "${DIST_ZIP}" "${DISTRIBUTION_URL}"
    elif command -v wget >/dev/null 2>&1; then
        wget -qO "${DIST_ZIP}" "${DISTRIBUTION_URL}"
    else
        printf 'Neither curl nor wget is available to download Gradle.\n' >&2
        printf 'Download %s manually and place it at %s.\n' "${DISTRIBUTION_URL}" "${DIST_ZIP}" >&2
        exit 1
    fi
}

extract_distribution() {
    printf 'Extracting Gradle %s...\n' "${GRADLE_VERSION}" >&2
    rm -rf "${GRADLE_HOME}"
    if command -v unzip >/dev/null 2>&1; then
        unzip -q "${DIST_ZIP}" -d "${WRAPPER_DIR}"
    elif command -v jar >/dev/null 2>&1; then
        (cd "${WRAPPER_DIR}" && jar xf "${DIST_ZIP}")
    else
        printf 'Neither unzip nor jar is available to extract the Gradle distribution.\n' >&2
        printf 'Install unzip or ensure the JDK jar tool is on your PATH.\n' >&2
        exit 1
    fi
}

if [ ! -f "${DIST_ZIP}" ]; then
    download_distribution
fi

if [ ! -x "${GRADLE_LAUNCHER}" ]; then
    extract_distribution
fi

if [ ! -x "${GRADLE_LAUNCHER}" ]; then
    printf 'Gradle launcher not found at %s after extraction.\n' "${GRADLE_LAUNCHER}" >&2
    exit 1
fi

exec "${GRADLE_LAUNCHER}" "$@"
