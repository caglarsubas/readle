"""Reproducible, self-authored CC0 publications for reader acceptance testing."""
from pathlib import Path
import zipfile
from reportlab.pdfgen import canvas
from reportlab.lib.colors import HexColor

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'tests/fixtures'
DEST.mkdir(parents=True, exist_ok=True)
chapters = [
    ('1. A place for an idea', 'A stable anchor identifies the passage, even when its appearance changes. Select this sentence, add a note, then change the font size or rotate your device. Your note should return to these same words.'),
    ('2. A simple learning rule', 'The weighted average is y = a*x + (1-a)*z. The parameter a controls the contribution of x. When a equals one, y equals x. When a equals zero, y equals z. A checkpoint records your confidence. An assessment records evidence from your answers.'),
    ('3. Independent sessions', 'A reader can explore the same source along several paths. Session one can remain at the equation. Session two can open this chapter. Shared highlights belong to the book. Each session keeps its own position and questions. A repeated phrase is not always the same passage.'),
]
pdf = DEST / 'readle-sample.pdf'
c = canvas.Canvas(str(pdf), pagesize=(432, 648), invariant=1)
c.setTitle('Readle - A Small Book of Learning'); c.setAuthor('Readle fixture authors - CC0')
for index, (title, body) in enumerate(chapters):
    c.bookmarkPage(f'chapter{index}'); c.addOutlineEntry(title, f'chapter{index}', level=0)
    c.setFillColor(HexColor('#355742')); c.setFont('Helvetica', 10)
    c.drawString(36, 606, 'READLE / A SMALL BOOK OF LEARNING')
    c.setFont('Helvetica-Bold', 20); c.drawString(36, 566, title)
    c.setFillColor(HexColor('#28352B')); c.setFont('Helvetica', 12)
    y = 520
    import textwrap
    for paragraph in [body, body, 'A stable anchor identifies the passage. This second occurrence tests quote disambiguation.']:
        for line in textwrap.wrap(paragraph, 53):
            c.drawString(36, y, line); y -= 20
        y -= 16
    if index == 1:
        c.setStrokeColor(HexColor('#355742')); c.line(60, 170, 350, 170); c.line(60, 170, 60, 260); c.line(60, 180, 340, 250)
        c.setFont('Helvetica', 10); c.drawString(60, 145, 'Figure 1. A hypothetical linear response as a increases.')
    c.setFont('Helvetica', 9); c.drawString(36, 28, f'CC0 - Self-authored test fixture                                  {index + 1}')
    c.showPage()
c.save()
with zipfile.ZipFile(DEST / 'readle-sample.epub', 'w') as z:
    z.writestr('mimetype', 'application/epub+zip', compress_type=zipfile.ZIP_STORED)
    z.writestr('META-INF/container.xml', '<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="EPUB/package.opf" media-type="application/oebps-package+xml"/></rootfiles></container>')
    manifest = '<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>'
    manifest += ''.join(f'<item id="c{i}" href="c{i}.xhtml" media-type="application/xhtml+xml"/>' for i in range(3))
    spine = ''.join(f'<itemref idref="c{i}"/>' for i in range(3))
    z.writestr('EPUB/package.opf', f'<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="uid">urn:readle:fixture:v1</dc:identifier><dc:title>A Small Book of Learning</dc:title><dc:creator>Readle fixture authors</dc:creator><dc:language>en</dc:language><meta property="dcterms:modified">2026-11-04T00:00:00Z</meta></metadata><manifest>{manifest}</manifest><spine>{spine}</spine></package>')
    def html(title, body):
        return f'<?xml version="1.0"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>{title}</title><style>body{{font-family:serif;line-height:1.6;padding:1em}}h1{{font-size:1.6em}}figure{{margin:1em 0}}</style></head><body>{body}</body></html>'
    toc = ''.join(f'<li><a href="c{i}.xhtml">{title}</a></li>' for i, (title, _) in enumerate(chapters))
    z.writestr('EPUB/nav.xhtml', html('Contents', f'<nav epub:type="toc"><h1>Contents</h1><ol>{toc}</ol></nav>'))
    for i, (title, body) in enumerate(chapters):
        figure = '<figure><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 300 120"><path d="M20 10V100H290M20 90L280 20" stroke="#355742" fill="none"/></svg><figcaption>Figure 1. A hypothetical linear response.</figcaption></figure>' if i == 1 else ''
        z.writestr(f'EPUB/c{i}.xhtml', html(title, '<h1>' + title + '</h1>' + ''.join('<p>' + body + '</p>' for _ in range(9)) + figure + '<p>A stable anchor identifies the passage. This repeated phrase tests exact location.</p>'))
assets = ROOT / 'apps/mobile/android/app/src/main/assets/fixtures'
assets.mkdir(parents=True, exist_ok=True)
for name in ['readle-sample.pdf', 'readle-sample.epub']:
    (assets / name).write_bytes((DEST / name).read_bytes())
print('Generated self-authored PDF and EPUB fixtures.')
