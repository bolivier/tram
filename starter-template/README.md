# Sample Tram Application

## Claude Code + REPL Integration (clojure-mcp)

This project includes a `.mcp.json` that connects Claude Code to a live Clojure REPL via [clojure-mcp](https://github.com/bhauman/clojure-mcp). When active, Claude can evaluate code, inspect running state, and make structurally-aware edits directly against your running application.

### One-time setup

Install clojure-mcp as a global Clojure tool:

```bash
clojure -Ttools install-latest :lib io.github.bhauman/clojure-mcp :as mcp
```

### Usage

Start your nREPL before opening Claude Code:

```bash
clj -M:dev  # or however you start your REPL
```

Then open Claude Code in the project directory. It will pick up `.mcp.json` automatically and connect to the running nREPL.

> **Note:** clojure-mcp is alpha software and its API may change. If the MCP server fails to connect, ensure your nREPL is running and that you have the latest version installed (`clojure -Ttools install-latest :lib io.github.bhauman/clojure-mcp :as mcp`).

