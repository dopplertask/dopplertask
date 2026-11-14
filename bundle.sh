#!/bin/sh
# Builds DopplerTask with its own small Java runtime into dist/dopplertask,
# so that a computer without Java can run it with dist/dopplertask/bin/dopplertask.
# Needs JDK 25. The Docker image and the release downloads are both made from it.
set -eu
cd "$(dirname "$0")"

out=dist/dopplertask
rm -rf "$out"
mkdir -p "$out/bin"

rm -f backend/build/libs/doppler-*.jar
(cd backend && ./gradlew bootJar --quiet)
cp "$(ls backend/build/libs/doppler-[0-9]*.jar)" "$out/dopplertask.jar"

jlink \
  --add-modules java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.security.jgss,java.sql,java.xml,jdk.jfr,jdk.management.agent,jdk.unsupported \
  --compress zip-6 \
  --no-header-files \
  --no-man-pages \
  --output "$out/runtime"

cat > "$out/bin/dopplertask" <<'LAUNCHER'
#!/bin/sh
# Starts DopplerTask with the Java runtime that comes with it. JAVA_OPTS adds Java settings.
here=$(cd "$(dirname "$0")/.." && pwd)
exec "$here/runtime/bin/java" -XX:+ExitOnOutOfMemoryError ${JAVA_OPTS:-} -jar "$here/dopplertask.jar" "$@"
LAUNCHER
chmod +x "$out/bin/dopplertask"

echo "Built $out"
