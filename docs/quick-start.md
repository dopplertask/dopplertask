# Quick start

On macOS or Linux, paste this in a terminal. Java comes with it, so you need nothing else:

```bash
curl -fsSL https://raw.githubusercontent.com/dopplertask/dopplertask/main/install.sh | sh
```

DopplerTask opens at http://localhost:8090. Run the same line again to update.

On Windows or a server, use Docker. Your tasks are kept in the volume `dopplertask`:

```bash
docker run -d --name dopplertask -p 8090:8090 -v dopplertask:/root dopplertask/dopplertask
```

To build from source, you need JDK 25:

```bash
git clone https://github.com/dopplertask/dopplertask.git
cd dopplertask/backend
./gradlew bootRun
```

Your tasks are then saved in `~/dopplerdb`.

To let an agent build tasks, connect it to `http://localhost:8090/mcp`. With Claude Code:

```bash
claude mcp add --transport http dopplertask http://localhost:8090/mcp
```

What the agent builds waits for you in the editor, ready for you to approve or reject.
