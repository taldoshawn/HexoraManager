#!/bin/sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$JAR" ]; then
  echo "Gradle wrapper JAR ausente. Execute 'gradle wrapper --gradle-version 8.13' uma vez." >&2
  exit 1
fi
JAVA_CMD="${JAVA_HOME:+$JAVA_HOME/bin/}java"
exec "$JAVA_CMD" -Dorg.gradle.appname=gradlew -jar "$JAR" "$@"
