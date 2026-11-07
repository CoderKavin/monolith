#!/usr/bin/env bash
# Downloads static instances of Google Sans Flex (OFL) into res/font before each build.
# Run from the project root: bash tools/fetch-fonts.sh
set -euo pipefail

OUT="app/src/main/res/font"
mkdir -p "$OUT"

fetch() {
  local name="$1" spec="$2" css url
  css=$(curl -fsSL "https://fonts.googleapis.com/css2?family=${spec}")
  url=$(printf '%s' "$css" | grep -o 'https://fonts.gstatic.com[^)]*\.ttf' | head -n1 || true)
  if [ -z "$url" ]; then
    echo "No TTF returned for ${spec}:"
    echo "$css"
    exit 1
  fi
  curl -fsSL "$url" -o "$OUT/${name}.ttf"
  echo "fetched ${name}.ttf ($(wc -c < "$OUT/${name}.ttf") bytes)"
}

# Rounded numerals (ROND 100) for times and values
fetch gsf_r580 "Google+Sans+Flex:wght,ROND@580,100"
fetch gsf_r620 "Google+Sans+Flex:wght,ROND@620,100"
# Plain text weights (ROND 0) for labels and titles
fetch gsf_t540 "Google+Sans+Flex:wght,ROND@540,0"
fetch gsf_t620 "Google+Sans+Flex:wght,ROND@620,0"
