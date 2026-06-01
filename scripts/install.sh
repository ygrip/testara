#!/usr/bin/env sh
# Testara Agent installer
# Usage:
#   curl -fsSL https://github.com/ygrip/testara/releases/latest/download/install.sh | bash
#   TESTARA_AGENT_VERSION=2.0.0 bash install.sh       # pin a version
#   TESTARA_AGENT_INSTALL_DIR=/opt/testara bash install.sh
#   TESTARA_AGENT_BIN_DIR=/usr/local/bin bash install.sh
#   bash install.sh --no-mcp                           # skip MCP auto-configuration
#   TESTARA_SKIP_MCP=1 bash install.sh                 # same, via env var (useful with curl | bash)

set -e

GITHUB_REPO="ygrip/testara"
INSTALL_DIR="${TESTARA_AGENT_INSTALL_DIR:-$HOME/.testara}"
BIN_DIR="${TESTARA_AGENT_BIN_DIR:-$HOME/.local/bin}"
VERSION="${TESTARA_AGENT_VERSION:-latest}"

# ── Colour helpers ────────────────────────────────────────────────
if [ -t 1 ]; then
  GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
else
  GREEN=''; YELLOW=''; RED=''; NC=''
fi

info()    { printf "${GREEN}[testara-agent]${NC} %s\n" "$*"; }
warn()    { printf "${YELLOW}[testara-agent] WARNING:${NC} %s\n" "$*"; }
err()     { printf "${RED}[testara-agent] ERROR:${NC} %s\n" "$*" >&2; exit 1; }

# ── Shell profile PATH injection ──────────────────────────────────
add_to_path() {
  local bin_dir="$1"
  local shell_rc=""
  # Detect shell profile
  if [ -n "$ZSH_VERSION" ] || [ "$(basename "$SHELL")" = "zsh" ]; then
    shell_rc="$HOME/.zshrc"
  elif [ -n "$BASH_VERSION" ] || [ "$(basename "$SHELL")" = "bash" ]; then
    shell_rc="${BASH_ENV:-$HOME/.bashrc}"
    [ -f "$HOME/.bash_profile" ] && shell_rc="$HOME/.bash_profile"
  fi

  if [ -n "$shell_rc" ] && [ -f "$shell_rc" ]; then
    if ! grep -qF "$bin_dir" "$shell_rc" 2>/dev/null; then
      printf '\n# Added by testara-agent installer\nexport PATH="%s:$PATH"\n' "$bin_dir" >> "$shell_rc"
      info "Added $bin_dir to PATH in $shell_rc"
      info "Reload with: source $shell_rc"
    fi
  fi
}

# ── Download helper ───────────────────────────────────────────────
download() {
  url="$1"; dest="$2"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL --progress-bar "$url" -o "$dest"
  elif command -v wget >/dev/null 2>&1; then
    wget -q --show-progress -O "$dest" "$url"
  else
    err "Neither curl nor wget found. Install one and retry."
  fi
}

# ── Java check ────────────────────────────────────────────────────
check_java() {
  if ! command -v java >/dev/null 2>&1; then
    warn "Java not found. testara-agent requires Java 21+."
    warn "Install Temurin: https://adoptium.net/temurin/releases/"
    return
  fi
  java_ver=$(java -version 2>&1 | head -1 | grep -oE '[0-9]+' | head -1)
  if [ "${java_ver:-0}" -lt 21 ] 2>/dev/null; then
    warn "Java ${java_ver} found — testara-agent requires Java 21+."
  else
    info "Java ${java_ver} found."
  fi
}

# ── Resolve download URL ──────────────────────────────────────────
if [ "$VERSION" = "latest" ]; then
  JAR_URL="https://github.com/${GITHUB_REPO}/releases/latest/download/testara-agent.jar"
else
  JAR_URL="https://github.com/${GITHUB_REPO}/releases/download/v${VERSION}/testara-agent.jar"
fi

# ── Argument parsing ─────────────────────────────────────────────
SKIP_MCP="${TESTARA_SKIP_MCP:-0}"
UNINSTALL=0
for _arg in "$@"; do
  case "$_arg" in
    --uninstall) UNINSTALL=1 ;;
    --no-mcp)    SKIP_MCP=1 ;;
  esac
done

# ── Uninstall ─────────────────────────────────────────────────────
if [ "$UNINSTALL" = "1" ]; then
  info "Uninstalling testara-agent..."
  rm -f "$BIN_DIR/testara-agent"
  rm -f "$INSTALL_DIR/testara-agent.jar"
  info "Removed wrapper: $BIN_DIR/testara-agent"
  info "Removed JAR: $INSTALL_DIR/testara-agent.jar"
  info "Uninstall complete. Remove PATH entry from your shell rc manually if desired."
  exit 0
fi

# ── Main install ──────────────────────────────────────────────────
info "Installing testara-agent..."
check_java

mkdir -p "$INSTALL_DIR" "$BIN_DIR"

info "Downloading testara-agent.jar from GitHub Releases..."
download "$JAR_URL" "$INSTALL_DIR/testara-agent.jar"
info "Saved to: $INSTALL_DIR/testara-agent.jar"

# Write wrapper script — resolves INSTALL_DIR at install time so the
# wrapper works regardless of where the user later runs it from.
WRAPPER="$BIN_DIR/testara-agent"
JAR_PATH="$INSTALL_DIR/testara-agent.jar"

cat > "$WRAPPER" << WRAPPER_EOF
#!/usr/bin/env sh
JAVA_CMD="\${JAVA_HOME:+\$JAVA_HOME/bin/}java"
exec "\$JAVA_CMD" -jar "$JAR_PATH" "\$@"
WRAPPER_EOF
chmod +x "$WRAPPER"
info "Wrapper installed: $WRAPPER"

# ── PATH setup ────────────────────────────────────────────────────
case ":${PATH}:" in
  *":${BIN_DIR}:"*) ;;
  *) add_to_path "$BIN_DIR" ;;
esac

# ── Verify ────────────────────────────────────────────────────────
info "Verifying installation..."
if "$WRAPPER" --version >/dev/null 2>&1; then
  installed_ver=$("$WRAPPER" --version 2>&1 | head -1)
  info "Success! ${installed_ver}"
else
  info "Installed. Run: java -jar $JAR_PATH --version"
fi

# ── MCP config for agentic providers ──────────────────────────────
setup_mcp() {
  local config_file="$1"
  local provider="$2"

  # Skip if file doesn't exist and parent dir doesn't exist
  local parent_dir
  parent_dir="$(dirname "$config_file")"
  [ -d "$parent_dir" ] || return 0

  # Build the server entry — uses the wrapper directly
  local entry
  entry="$(cat << ENTRY_EOF
    "testara": {
      "type": "stdio",
      "command": "$WRAPPER",
      "args": ["mcp"],
      "env": {
        "TESTARA_AGENT_RUN_ENABLED": "true",
        "TESTARA_AGENT_WRITE_ENABLED": "true"
      }
    }
ENTRY_EOF
)"

  # Already configured?
  if [ -f "$config_file" ] && grep -q '"testara"' "$config_file" 2>/dev/null; then
    info "$provider: testara MCP already configured — skipping"
    return 0
  fi

  # VS Code / Cursor style: { "servers": { ... } }
  if echo "$config_file" | grep -qE "Code|Cursor|cursor"; then
    if [ -f "$config_file" ]; then
      # Insert into existing servers block using Python (safe JSON edit)
      if command -v python3 >/dev/null 2>&1; then
        python3 - "$config_file" "$WRAPPER" << 'PYEOF' || { warn "$provider: failed to update $config_file — configure manually (see docs/agentic-skills.md)"; return 0; }
import sys, json
path, wrapper = sys.argv[1], sys.argv[2]
cfg = {}
try:
    with open(path) as f:
        cfg = json.load(f)
except Exception:
    pass
cfg.setdefault("servers", {})["testara"] = {
    "type": "stdio", "command": wrapper, "args": ["mcp"],
    "env": {"TESTARA_AGENT_RUN_ENABLED": "true", "TESTARA_AGENT_WRITE_ENABLED": "true"}
}
with open(path, "w") as f:
    json.dump(cfg, f, indent=2)
    f.write("\n")
PYEOF
        info "$provider: testara MCP server added to $config_file"
      else
        warn "$provider: python3 not found — skipping JSON edit"
      fi
    else
      # Create new config
      cat > "$config_file" << CFG_EOF
{
  "servers": {
    "testara": {
      "type": "stdio",
      "command": "$WRAPPER",
      "args": ["mcp"],
      "env": {
        "TESTARA_AGENT_RUN_ENABLED": "true",
        "TESTARA_AGENT_WRITE_ENABLED": "true"
      }
    }
  }
}
CFG_EOF
      info "$provider: MCP config created at $config_file"
    fi
    return 0
  fi

  # Claude Desktop style: { "mcpServers": { ... } }
  if [ -f "$config_file" ]; then
    if command -v python3 >/dev/null 2>&1; then
      python3 - "$config_file" "$WRAPPER" << 'PYEOF' || { warn "$provider: failed to update $config_file — configure manually (see docs/agentic-skills.md)"; return 0; }
import sys, json
path, wrapper = sys.argv[1], sys.argv[2]
cfg = {}
try:
    with open(path) as f:
        cfg = json.load(f)
except Exception:
    pass
cfg.setdefault("mcpServers", {})["testara"] = {
    "command": wrapper, "args": ["mcp"],
    "env": {"TESTARA_AGENT_RUN_ENABLED": "true", "TESTARA_AGENT_WRITE_ENABLED": "true"}
}
with open(path, "w") as f:
    json.dump(cfg, f, indent=2)
    f.write("\n")
PYEOF
      info "$provider: testara MCP server added to $config_file"
    else
      warn "$provider: python3 not found — skipping JSON edit"
    fi
  else
    cat > "$config_file" << CFG_EOF
{
  "mcpServers": {
    "testara": {
      "command": "$WRAPPER",
      "args": ["mcp"],
      "env": {
        "TESTARA_AGENT_RUN_ENABLED": "true",
        "TESTARA_AGENT_WRITE_ENABLED": "true"
      }
    }
  }
}
CFG_EOF
    info "$provider: MCP config created at $config_file"
  fi
}

if [ "$SKIP_MCP" = "1" ]; then
  info "Skipping MCP auto-configuration (--no-mcp). See docs/agentic-skills.md for manual setup."
else

info ""
info "Configuring MCP servers for agentic providers (skip with --no-mcp or TESTARA_SKIP_MCP=1)..."

# Detect OS
_os="$(uname -s)"

# VS Code (user-level mcp.json)
if [ "$_os" = "Darwin" ]; then
  setup_mcp "$HOME/Library/Application Support/Code/User/mcp.json" "VS Code"
  setup_mcp "$HOME/Library/Application Support/Cursor/User/mcp.json" "Cursor"
  setup_mcp "$HOME/Library/Application Support/Claude/claude_desktop_config.json" "Claude Desktop"
else
  setup_mcp "${XDG_CONFIG_HOME:-$HOME/.config}/Code/User/mcp.json" "VS Code"
  setup_mcp "${XDG_CONFIG_HOME:-$HOME/.config}/Cursor/User/mcp.json" "Cursor"
  setup_mcp "${XDG_CONFIG_HOME:-$HOME/.config}/claude/claude_desktop_config.json" "Claude Desktop"
fi

# Claude Code (~/.claude/settings.json uses a different MCP format)
CLAUDE_SETTINGS="$HOME/.claude/settings.json"
if [ -d "$HOME/.claude" ]; then
  if [ -f "$CLAUDE_SETTINGS" ] && grep -q '"testara"' "$CLAUDE_SETTINGS" 2>/dev/null; then
    info "Claude Code: testara MCP already configured"
  elif command -v python3 >/dev/null 2>&1; then
    python3 - "$CLAUDE_SETTINGS" "$WRAPPER" << 'PYEOF' || warn "Claude Code: failed to update $CLAUDE_SETTINGS — configure manually (see docs/agentic-skills.md)"
import sys, json, os
path, wrapper = sys.argv[1], sys.argv[2]
cfg = {}
if os.path.exists(path):
    try:
        with open(path) as f:
            cfg = json.load(f)
    except Exception:
        pass
cfg.setdefault("mcpServers", {})["testara"] = {
    "command": wrapper, "args": ["mcp"],
    "env": {"TESTARA_AGENT_RUN_ENABLED": "true", "TESTARA_AGENT_WRITE_ENABLED": "true"}
}
with open(path, "w") as f:
    json.dump(cfg, f, indent=2)
    f.write("\n")
PYEOF
    info "Claude Code: testara MCP server configured in $CLAUDE_SETTINGS"
  fi
fi

fi  # end SKIP_MCP check

info ""
info "Quick start:"
info "  testara-agent test-init                    # scaffold a new project (interactive)"
info "  testara-agent test-plan 'intent' --write   # generate a Cucumber feature"
info "  testara-agent test-run  'intent' --execute # run the tests"
info ""
info "MCP: open VS Code / Cursor / Claude Desktop and look for 'testara' in the tools list."
info "Docs: https://github.com/${GITHUB_REPO}/blob/main/docs/agentic-skills.md"
