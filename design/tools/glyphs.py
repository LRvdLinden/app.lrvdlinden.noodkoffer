# Turns text into SVG path data using the real font outlines.
import sys
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
def text_path(fontfile, text, size, x, y, anchor="middle", tracking=0):
    f = TTFont(fontfile); gs = f.getGlyphSet(); cmap = f.getBestCmap(); upm = f["head"].unitsPerEm
    s = size / upm; names = [cmap[ord(c)] for c in text]
    adv = [gs[n].width * s + tracking for n in names]; total = sum(adv) - tracking
    cx = x - (total / 2 if anchor == "middle" else 0)
    pen = SVGPathPen(gs)
    for n, a in zip(names, adv):
        gs[n].draw(TransformPen(pen, (s, 0, 0, -s, cx, y))); cx += a
    return pen.getCommands()
if __name__ == "__main__":
    print(text_path(*sys.argv[1:3], float(sys.argv[3]), float(sys.argv[4]), float(sys.argv[5])))
