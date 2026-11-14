#!/bin/sh
# Installs DopplerTask and starts it, on macOS or Linux. Java comes with it, so nothing else is needed.
#   curl -fsSL https://raw.githubusercontent.com/dopplertask/dopplertask/main/install.sh | sh
# Run it again to update. Your tasks and credentials stay where they are.
set -eu

case "$(uname -s)" in
  Darwin) system=macos ;;
  Linux) system=linux ;;
  *) echo "DopplerTask installs this way on macOS and Linux. On other systems, use Docker: https://github.com/dopplertask/dopplertask#start-in-a-minute" >&2; exit 1 ;;
esac
case "$(uname -m)" in
  arm64 | aarch64) processor=arm64 ;;
  x86_64 | amd64) processor=x64 ;;
  *) echo "There is no download for the processor $(uname -m) yet. Use Docker instead." >&2; exit 1 ;;
esac

download=${DOPPLERTASK_DOWNLOAD:-https://github.com/dopplertask/dopplertask/releases/latest/download/dopplertask-$system-$processor.tar.gz}
home=${DOPPLERTASK_HOME:-$HOME/.dopplertask}
app="$home/app"
address=http://localhost:8090

if curl -fs "$address/health" >/dev/null 2>&1; then
  echo "DopplerTask is already running at $address. Stop it first to update it."
  exit 1
fi

echo "Downloading DopplerTask for $system ($processor)..."
unpacked=$(mktemp -d)
trap 'rm -rf "$unpacked"' EXIT
curl -fL --progress-bar "$download" | tar -xz -C "$unpacked"

mkdir -p "$home"
rm -rf "$app"
mv "$unpacked/dopplertask" "$app"

nohup "$app/bin/dopplertask" > "$home/dopplertask.log" 2>&1 &
process=$!
echo "Starting..."
seconds=0
until curl -fs "$address/health" >/dev/null 2>&1; do
  if ! kill -0 "$process" 2>/dev/null; then
    echo "DopplerTask stopped while starting. See $home/dopplertask.log" >&2
    exit 1
  fi
  if [ "$seconds" -ge 120 ]; then
    echo "DopplerTask did not answer within 2 minutes. See $home/dopplertask.log" >&2
    exit 1
  fi
  sleep 1
  seconds=$((seconds + 1))
done

echo
echo "DopplerTask is running at $address"
echo "  Stop it:      kill $process"
echo "  Start again:  $app/bin/dopplertask"
echo "  Connect an agent (Claude Code):  claude mcp add --transport http dopplertask $address/mcp"

if command -v open >/dev/null 2>&1; then open "$address"
elif command -v xdg-open >/dev/null 2>&1; then xdg-open "$address" >/dev/null 2>&1 || true
fi
