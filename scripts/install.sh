#!/usr/bin/env sh
# Testara Agent installer
# Usage:
#   curl -fsSL https://github.com/ygrip/testara/releases/latest/download/install.sh | bash
#   TESTARA_AGENT_VERSION=2.0.0 bash install.sh   # pin a version
#   TESTARA_AGENT_INSTALL_DIR=/opt/testara bash install.sh
#   TESTARA_AGENT_BIN_DIR=/usr/local/bin bash install.sh

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

# ── Uninstall ─────────────────────────────────────────────────────
if [ "${1:-}" = "--uninstall" ]; then
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
exec java -jar "$JAR_PATH" "\$@"
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

info ""
info "Quick start:"
info "  testara-agent /test-overview ."
info "  testara-agent /test-run 'run smoke tests' --project /path/to/your/automation"
info "  testara-agent --help"
info ""
info "Docs: https://github.com/${GITHUB_REPO}/blob/main/docs/agentic-skills.md"
