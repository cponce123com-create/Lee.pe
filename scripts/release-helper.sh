#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
read -r -p 'Versión del release (ej. v0.3.8): ' version
read -r -p 'Mensaje del release: ' message

if [[ ! "$version" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ || ! "$message" =~ [^[:space:]] ]]; then
    echo 'Ingresa una versión vMAJOR.MINOR.PATCH y un mensaje.' >&2
    exit 1
fi

if [[ -n "$(git status --porcelain)" ]]; then
    echo 'Guarda los cambios en Git antes de crear el tag.' >&2
    exit 1
fi

expected_version="$(sed -n 's/^versionName=//p' version.properties | tr -d '\r')"
if [[ "$version" != "v$expected_version" ]]; then
    echo "El tag debe coincidir con version.properties (v$expected_version)." >&2
    exit 1
fi

git tag -a "$version" -m "$message"
if ! git push origin "refs/tags/$version"; then
    echo "El tag quedó guardado localmente. Reintenta con git push origin refs/tags/$version." >&2
    exit 1
fi
printf 'Tag %s publicado. Ve a GitHub Actions → Release bundle para seguir el build y descargar el APK cuando termine.\n' "$version"
