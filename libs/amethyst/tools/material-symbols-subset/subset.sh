#!/usr/bin/env bash
#
# Subset commonsUI/.../material_symbols_outlined.ttf to only the codepoints
# referenced from MaterialSymbols.kt. The full Google variable font is ~11 MB
# (≈3500 glyphs); the subset is ~410 KB.
#
# Run this whenever MaterialSymbols.kt gains or loses a codepoint, or when
# pulling a newer upstream font release.
#
# Inputs:
#   $1 (optional) — path to a full upstream MaterialSymbolsOutlined-Regular.ttf
#                   to subset. If omitted, the script downloads the variable
#                   font from Google Fonts.
#
# Output:
#   Overwrites commonsUI/src/commonMain/composeResources/font/material_symbols_outlined.ttf

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SYMBOLS_KT="$REPO_ROOT/commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/icons/symbols/MaterialSymbols.kt"
TARGET="$REPO_ROOT/commonsUI/src/commonMain/composeResources/font/material_symbols_outlined.ttf"

if ! command -v pyftsubset >/dev/null 2>&1; then
    echo "pyftsubset not found. Install with: pip install fonttools brotli" >&2
    exit 1
fi

if [ "${1:-}" ]; then
    SOURCE_TTF="$1"
else
    SOURCE_TTF="$(mktemp -t material_symbols.XXXXXX.ttf)"
    trap 'rm -f "$SOURCE_TTF"' EXIT
    echo "Downloading upstream Material Symbols Outlined variable font..."
    curl -fsSL -o "$SOURCE_TTF" \
        "https://github.com/google/material-design-icons/raw/master/variablefont/MaterialSymbolsOutlined%5BFILL%2CGRAD%2Copsz%2Cwght%5D.ttf"
fi

CODEPOINTS_FILE="$(mktemp -t material_symbols_cp.XXXXXX.txt)"
trap 'rm -f "$CODEPOINTS_FILE" ${SOURCE_TTF:+}' EXIT

# Pull every \uXXXX literal out of MaterialSymbols.kt as U+XXXX, deduplicated.
grep -oE '\\u[A-Fa-f0-9]{4}' "$SYMBOLS_KT" | sort -u | sed 's/\\u/U+/' > "$CODEPOINTS_FILE"

count=$(wc -l < "$CODEPOINTS_FILE")
echo "Subsetting to $count codepoints from MaterialSymbols.kt"

pyftsubset "$SOURCE_TTF" \
    --unicodes-file="$CODEPOINTS_FILE" \
    --output-file="$TARGET" \
    --layout-features='*' \
    --no-hinting \
    --desubroutinize \
    --notdef-outline \
    --recommended-glyphs \
    --name-IDs='*' \
    --name-legacy \
    --name-languages='*' \
    --glyph-names \
    --symbol-cmap

# Bake in the custom (non-Material) glyphs from custom/ — third-party logos
# like OpenTimestamps that the upstream font will never carry.
python3 "$REPO_ROOT/tools/material-symbols-subset/add_custom_glyphs.py" "$TARGET"

echo "Wrote $TARGET ($(du -h "$TARGET" | cut -f1))"
