# Amethyst icon font

Builds `amethyst_icons.ttf` from the Kotlin `ImageVector` icons in
`commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/icons/`.

## Why

`Icon(imageVector = …)` calls `rememberVectorPainter`, and a `VectorPainter`
rasterises its paths into a cached graphics layer **per instance**. A feed therefore
re-rasterised the same handful of glyphs once for every card scrolled in. A font glyph
is a blit from the shared text atlas instead, shared across every call site in the app
for free — no `CompositionLocal` plumbing, no per-screen scoping.

Measured on the uniform-corpus macrobenchmark (SM-T220, three arms, 0.2% noise floor):

| approach | frame P90 | overrun P90 | artwork |
|---|---|---|---|
| one shared `VectorPainter` per icon | −8.2% | −14.2% | unchanged |
| MaterialSymbols glyph substitutes | −10.4% | −16.0% | **changes** |
| **this font** | **−10.7%** | **−17.4%** | unchanged |
| ceiling: draw no icons at all | −12.7% | −22.9% | n/a |

## Usage

    pip install fonttools
    python3 tools/icon-font/build_icon_font.py <icons-dir> <out.ttf> <out.kt>

See the "Amethyst's own icons are also a font" section of `AGENTS.md` for the
mandatory regeneration step and why both outputs must be committed together.

## How it works

The `ImageVector` builder DSL maps 1:1 onto SVG path commands (`moveTo` → `M`,
`curveToRelative` → `c`, …; none of the icons use `arcTo`), so the script extracts the
path data, emits an SVG `d` string, and draws it into a TrueType glyph via fontTools —
converting cubics to quadratics and flipping the y axis, since SVG grows downward and
font outlines grow upward from the baseline.

Font metrics deliberately mirror the bundled `material_symbols_outlined.ttf`
(unitsPerEm 960, ascent 1056, descent −96, advance 960) so the glyphs align with
existing MaterialSymbols call sites and `Icon()` sizing. Generated outlines land within
a few units of Google's own: our `Like` spans (78,94)–(882,851), their heart
(80,120)–(880,854).

An icon whose path data the parser cannot reach is reported and skipped rather than
silently emitted empty; it must keep using its `ImageVector`.
