#!/usr/bin/env python3
"""Build a custom icon font from Amethyst's Kotlin ImageVector icons.

Why: drawing an ImageVector rasterises its paths into a per-instance cached layer,
so a feed re-rasterises the same glyph once per card. A font glyph is a blit from
the shared text atlas instead. Measured on the uniform-corpus macrobenchmark
(SM-T220, 0.2% noise floor): swapping the three reaction icons to font glyphs gave
frame P90 -10.4% vs -8.2% for per-icon shared painters, against a -12.7% ceiling.

This keeps Amethyst's own artwork -- it converts the existing ImageVector path data
rather than substituting Google's glyphs, so the icons look identical.

Font metrics deliberately mirror the bundled material_symbols_outlined.ttf
(unitsPerEm 960, ascent 1056, descent -96, advance 960) so the glyphs align with
existing MaterialSymbols call sites and Icon() sizing.

Usage:  build_icon_font.py <icons-dir> <out.ttf> <out.kt>
"""
import re, sys, os

from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.transformPen import TransformPen
from fontTools.misc.transform import Transform
from fontTools.svgLib.path.parser import parse_path

UPEM, ASCENT, DESCENT, ADVANCE = 960, 1056, -96, 960
FIRST_CODEPOINT = 0xE900
MAX_ERR = 1.0  # cubic->quadratic tolerance, in font units

# ImageVector DSL -> SVG path command. No arcTo: none of the icons use one.
CMDS = {
    "moveTo": "M", "moveToRelative": "m",
    "lineTo": "L", "lineToRelative": "l",
    "horizontalLineTo": "H", "horizontalLineToRelative": "h",
    "verticalLineTo": "V", "verticalLineToRelative": "v",
    "curveTo": "C", "curveToRelative": "c",
    "reflectiveCurveTo": "S", "reflectiveCurveToRelative": "s",
    "quadTo": "Q", "quadToRelative": "q",
    "reflectiveQuadTo": "T", "reflectiveQuadToRelative": "t",
    "close": "Z",
}
CALL_RE = re.compile(r"\b(" + "|".join(CMDS) + r")\(([^()]*)\)")
NUM_RE = re.compile(r"-?\d+(?:\.\d+)?(?:[eE][-+]?\d+)?")


STROKE_W_RE = re.compile(r"strokeLineWidth\s*=\s*([\d.]+)f?")


def is_stroked(src: str) -> bool:
    """True if the icon paints a stroke, not just a fill.

    A glyph outline is filled: there is no pen width in a TrueType glyph. Converting a
    stroked icon would emit only its fill and silently change the artwork -- Zap is drawn
    as a thin outline (strokeLineWidth 1.2) and came out as a solid bolt. Such icons must
    keep their ImageVector.
    """
    if any(float(w) > 0 for w in STROKE_W_RE.findall(src)):
        return True
    return "stroke = SolidColor" in src


def kotlin_to_svg_path(src: str):
    """Extract viewport and an SVG 'd' string from one ImageVector .kt file."""
    vw = re.search(r"viewportWidth\s*=\s*([\d.]+)f?", src)
    vh = re.search(r"viewportHeight\s*=\s*([\d.]+)f?", src)
    if vw and vh:
        viewport = (float(vw.group(1)), float(vh.group(1)))
    elif "materialIcon(" in src:
        # materialIcon() sets the viewport itself; Material's convention is 24x24.
        viewport = (24.0, 24.0)
    else:
        return None, None

    # Only look inside the vector builder, never the @Preview composable above it.
    # Only look inside the vector builder, never the @Preview composable above it, and never
    # a helper like materialOutlinedPath() declared after it.
    start = src.find(".apply {")
    if start == -1:
        start = src.find("materialIcon(")
    body = src[start:] if start != -1 else src
    end = body.find("\ninline fun ")
    if end != -1:
        body = body[:end]

    parts = []
    for m in CALL_RE.finditer(body):
        cmd, args = m.group(1), m.group(2)
        letter = CMDS[cmd]
        if cmd == "close":
            parts.append("Z")
            continue
        nums = NUM_RE.findall(args)
        if not nums:
            continue
        parts.append(letter + " " + " ".join(nums))
    return viewport, " ".join(parts)


def build_glyph(d: str, viewport):
    vw, vh = viewport
    # Uniform scale on the larger axis keeps non-square viewports undistorted.
    s = UPEM / max(vw, vh)
    pen = TTGlyphPen(None)
    # y flips: SVG grows downward, font outlines grow upward from the baseline.
    tp = TransformPen(Cu2QuPen(pen, MAX_ERR), Transform(s, 0, 0, -s, 0, vh * s))
    parse_path(d, tp)
    return pen.glyph()


def main(icons_dir, out_ttf, out_kt):
    files = sorted(f for f in os.listdir(icons_dir) if f.endswith(".kt"))
    glyphs, cmap, names, skipped = {".notdef": TTGlyphPen(None).glyph()}, {}, [".notdef"], []
    cp = FIRST_CODEPOINT
    for fn in files:
        name = fn[:-3]
        src = open(os.path.join(icons_dir, fn), encoding="utf-8").read()
        if is_stroked(src):
            skipped.append((name, "draws a stroke; a glyph can only be filled"))
            continue
        viewport, d = kotlin_to_svg_path(src)
        if not d:
            skipped.append((name, "no path data"))
            continue
        try:
            glyphs[name] = build_glyph(d, viewport)
        except Exception as e:  # noqa: BLE001 - report and continue, don't kill the build
            skipped.append((name, f"{type(e).__name__}: {e}"))
            continue
        cmap[cp] = name
        names.append(name)
        print(f"  {name:<12} U+{cp:04X}  viewport {viewport[0]:g}x{viewport[1]:g}  {len(d)} chars")
        cp += 1

    fb = FontBuilder(UPEM, isTTF=True)
    fb.setupGlyphOrder(names)
    fb.setupCharacterMap(cmap)
    fb.setupGlyf(glyphs)
    fb.setupHorizontalMetrics({n: (ADVANCE, 0) for n in names})
    fb.setupHorizontalHeader(ascent=ASCENT, descent=DESCENT)
    fb.setupNameTable({
        "familyName": "Amethyst Icons", "styleName": "Regular",
        "psName": "AmethystIcons-Regular", "version": "1.0",
    })
    fb.setupOS2(sTypoAscender=ASCENT, sTypoDescender=DESCENT,
                usWinAscent=ASCENT, usWinDescent=abs(DESCENT))
    fb.setupPost(keepGlyphNames=False)
    fb.save(out_ttf)

    with open(out_kt, "w", encoding="utf-8") as fh:
        fh.write("// GENERATED by tools/icon-font/build_icon_font.py -- do not edit by hand.\n")
        fh.write("package com.vitorpamplona.amethyst.commons.icons.symbols\n\n")
        fh.write("/** Amethyst's own icons as font glyphs. See the build script for why. */\n")
        fh.write("object AmethystIcons {\n")
        for code, name in sorted(cmap.items()):
            fh.write(f'    val {name} = MaterialSymbol("\\u{code:04X}")\n')
        fh.write("}\n")

    print(f"\nwrote {out_ttf} ({os.path.getsize(out_ttf)} bytes), {len(cmap)} glyphs")
    for name, why in skipped:
        print(f"  SKIPPED {name}: {why}")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(*sys.argv[1:])
