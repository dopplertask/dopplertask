# Quick start

You need JDK 25.

```bash
git clone https://github.com/dopplertask/dopplertask.git
cd dopplertask/backend
./gradlew bootRun
```

Then open http://localhost:8090. Your tasks are saved in `~/dopplerdb`.

To let an agent build tasks, connect it to `http://localhost:8090/mcp`. With Claude Code:

```bash
claude mcp add --transport http dopplertask http://localhost:8090/mcp
```

What the agent builds waits for you in the editor, ready for you to approve or reject.
