#!/usr/bin/env bash
# install.sh — Install eval-harness CLI
set -euo pipefail

# Colors
C_G='\033[0;32m'; C_C='\033[0;36m'; C_BOLD='\033[1m'; C_X='\033[0m'
ok()   { printf "${C_G}✓${C_X} %s\n" "$*"; }
info() { printf "${C_C}ℹ${C_X} %s\n" "$*"; }

EVAL_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"

echo
echo "${C_BOLD}eval-harness — Installation${C_X}"
echo

# Check dependencies
info "Checking dependencies..."

if ! command -v claude >/dev/null 2>&1; then
  echo "  ✗ claude CLI not found"
  echo "  Ensure Claude CLI is installed and in PATH"
  exit 1
fi
ok "  claude CLI found"

if ! command -v jq >/dev/null 2>&1; then
  echo "  ✗ jq not found"
  echo "  Install: brew install jq"
  exit 1
fi
ok "  jq found"

# Add to PATH
echo
info "Add to your PATH (optional):"
echo "  export PATH=\"\$PATH:$EVAL_HOME/bin\""
echo
echo "Or:"
echo "  sudo ln -sf $EVAL_HOME/bin/eval-harness /usr/local/bin/eval-harness"

echo
ok "Installation complete!"
echo
info "Usage:"
echo "  $EVAL_HOME/bin/eval-harness /path/to/migrated-repo"
echo "  $EVAL_HOME/bin/eval-harness --compare /path/to/repo1 /path/to/repo2"
