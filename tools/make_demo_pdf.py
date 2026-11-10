"""Generate the generic 12-slide sample deck used by the debug-only demo mode.

Writes app/src/debug/assets/demo/product-launch-2026.pdf. Requires Pillow.
All content is made up: no real names, logos or company data.

    python tools/make_demo_pdf.py
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

W, H = 1600, 900
OUT = Path(__file__).resolve().parent.parent / "app/src/debug/assets/demo/product-launch-2026.pdf"

INK = (31, 41, 55)
MUTED = (107, 114, 128)
BG = (250, 250, 252)
ACCENT = (79, 70, 229)
ACCENT_2 = (14, 165, 233)
ACCENT_3 = (16, 185, 129)
ACCENT_4 = (245, 158, 11)
LIGHT = (229, 231, 235)
PALETTE = [ACCENT, ACCENT_2, ACCENT_3, ACCENT_4]

FONT_DIRS = [Path("C:/Windows/Fonts"), Path("/usr/share/fonts/truetype/dejavu"), Path("/Library/Fonts")]
REGULAR = ["segoeui.ttf", "DejaVuSans.ttf", "Arial.ttf"]
BOLD = ["segoeuib.ttf", "DejaVuSans-Bold.ttf", "Arial Bold.ttf"]


def font(size, bold=False):
    for d in FONT_DIRS:
        for name in BOLD if bold else REGULAR:
            p = d / name
            if p.exists():
                return ImageFont.truetype(str(p), size)
    return ImageFont.load_default(size)


def canvas():
    img = Image.new("RGB", (W, H), BG)
    return img, ImageDraw.Draw(img)


def header(d, title, number):
    d.rectangle([0, 0, W, 14], fill=ACCENT)
    d.text((90, 70), title, font=font(64, bold=True), fill=INK)
    d.rectangle([90, 160, 210, 166], fill=ACCENT_2)
    d.text((W - 90, H - 60), str(number), font=font(28), fill=MUTED, anchor="rs")


def bullets(d, items, top=230, left=110, size=40, gap=78):
    f = font(size)
    for i, item in enumerate(items):
        y = top + i * gap
        d.ellipse([left, y + 14, left + 18, y + 32], fill=PALETTE[i % len(PALETTE)])
        d.text((left + 44, y), item, font=f, fill=INK)


def title_slide(title, subtitle, number):
    img, d = canvas()
    d.rectangle([0, 0, W, H], fill=ACCENT)
    d.ellipse([1050, -250, 1850, 550], fill=(99, 102, 241))
    d.ellipse([1250, 450, 1750, 950], fill=ACCENT_2)
    d.text((120, 330), title, font=font(96, bold=True), fill="white")
    d.text((124, 470), subtitle, font=font(44), fill=(224, 231, 255))
    d.text((W - 90, H - 60), str(number), font=font(28), fill=(199, 210, 254), anchor="rs")
    return img


def bullet_slide(title, items, number):
    img, d = canvas()
    header(d, title, number)
    bullets(d, items)
    return img


def bar_chart(d, box, labels, values, colors=None, suffix=""):
    x0, y0, x1, y1 = box
    d.line([x0, y1, x1, y1], fill=LIGHT, width=3)
    top = max(values)
    slot = (x1 - x0) / len(values)
    bw = slot * 0.55
    for i, (label, v) in enumerate(zip(labels, values)):
        cx = x0 + slot * (i + 0.5)
        h = (y1 - y0 - 50) * v / top
        color = (colors or PALETTE)[i % len(colors or PALETTE)]
        d.rounded_rectangle([cx - bw / 2, y1 - h, cx + bw / 2, y1], radius=10, fill=color)
        d.text((cx, y1 - h - 12), f"{v}{suffix}", font=font(30, bold=True), fill=INK, anchor="ms")
        d.text((cx, y1 + 44), label, font=font(28), fill=MUTED, anchor="ms")


def chart_slide(title, caption, labels, values, number, suffix=""):
    img, d = canvas()
    header(d, title, number)
    bar_chart(d, (140, 230, W - 140, 760), labels, values, suffix=suffix)
    d.text((140, 840), caption, font=font(28), fill=MUTED, anchor="ls")
    return img


def market_slide(number):
    img, d = canvas()
    header(d, "Market Opportunity", number)
    # Nested circles: total / serviceable / obtainable market.
    cx, cy = 470, 540
    for r, color in [(300, (224, 231, 255)), (200, (165, 180, 252)), (100, ACCENT)]:
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color)
    d.text((cx, cy), "$600M", font=font(40, bold=True), fill="white", anchor="mm")
    legend = [((224, 231, 255), "Total market", "$12B"), ((165, 180, 252), "Serviceable market", "$4.8B"),
              (ACCENT, "Year-one target", "$600M")]
    for i, (color, label, value) in enumerate(legend):
        y = 300 + i * 130
        d.rounded_rectangle([900, y, 950, y + 50], radius=8, fill=color)
        d.text((980, y - 4), label, font=font(36), fill=INK)
        d.text((980, y + 44), value, font=font(30, bold=True), fill=MUTED)
    return img


def timeline_slide(number):
    img, d = canvas()
    header(d, "Launch Timeline", number)
    steps = [("Q1", "Private beta"), ("Q2", "Public preview"), ("Q3", "General release"), ("Q4", "Partner program")]
    y = 500
    d.line([180, y, W - 180, y], fill=LIGHT, width=8)
    for i, (q, label) in enumerate(steps):
        x = 180 + i * (W - 360) / (len(steps) - 1)
        c = PALETTE[i]
        d.ellipse([x - 34, y - 34, x + 34, y + 34], fill=c)
        d.text((x, y), q, font=font(28, bold=True), fill="white", anchor="mm")
        d.text((x, y + 90), label, font=font(32), fill=INK, anchor="mm")
    return img


def forecast_slide(number):
    img, d = canvas()
    header(d, "Revenue Forecast", number)
    x0, y0, x1, y1 = 160, 240, W - 160, 760
    for i in range(5):
        gy = y1 - i * (y1 - y0) / 4
        d.line([x0, gy, x1, gy], fill=LIGHT, width=2)
    years = ["2026", "2027", "2028", "2029", "2030"]
    series = [(ACCENT, [2, 5, 9, 14, 20]), (ACCENT_3, [1, 3, 6, 9, 13])]
    for color, vals in series:
        pts = [(x0 + i * (x1 - x0) / (len(vals) - 1), y1 - v / 20 * (y1 - y0)) for i, v in enumerate(vals)]
        d.line(pts, fill=color, width=8, joint="curve")
        for px, py in pts:
            d.ellipse([px - 12, py - 12, px + 12, py + 12], fill=color)
    for i, yr in enumerate(years):
        d.text((x0 + i * (x1 - x0) / 4, y1 + 50), yr, font=font(28), fill=MUTED, anchor="ms")
    for i, (color, label) in enumerate([(ACCENT, "Subscriptions"), (ACCENT_3, "Services")]):
        lx = 160 + i * 360
        d.rounded_rectangle([lx, 830, lx + 36, 856], radius=6, fill=color)
        d.text((lx + 52, 856), label, font=font(28), fill=INK, anchor="ls")
    return img


def pricing_slide(number):
    img, d = canvas()
    header(d, "Pricing", number)
    plans = [("Starter", "$9", "per user / month"), ("Team", "$19", "per user / month"), ("Enterprise", "Custom", "volume pricing")]
    cw, gap = 400, 60
    left = (W - (3 * cw + 2 * gap)) / 2
    for i, (name, price, note) in enumerate(plans):
        x = left + i * (cw + gap)
        highlighted = i == 1
        d.rounded_rectangle([x, 240, x + cw, 780], radius=24, fill=ACCENT if highlighted else "white",
                            outline=None if highlighted else LIGHT, width=3)
        fg = "white" if highlighted else INK
        sub = (224, 231, 255) if highlighted else MUTED
        d.text((x + cw / 2, 330), name, font=font(40, bold=True), fill=fg, anchor="mm")
        d.text((x + cw / 2, 480), price, font=font(88, bold=True), fill=fg, anchor="mm")
        d.text((x + cw / 2, 570), note, font=font(28), fill=sub, anchor="mm")
    return img


def features_slide(number):
    img, d = canvas()
    header(d, "Key Features", number)
    items = [("Fast setup", "Ready in minutes"), ("Works offline", "Syncs when back online"),
             ("Secure by default", "Encrypted end to end"), ("Open API", "Fits existing tools")]
    for i, (t, s) in enumerate(items):
        col, row = i % 2, i // 2
        x, y = 110 + col * 720, 240 + row * 280
        d.rounded_rectangle([x, y, x + 660, y + 230], radius=20, fill="white", outline=LIGHT, width=3)
        d.ellipse([x + 40, y + 70, x + 130, y + 160], fill=PALETTE[i])
        d.text((x + 170, y + 70), t, font=font(40, bold=True), fill=INK)
        d.text((x + 170, y + 130), s, font=font(30), fill=MUTED)
    return img


def qa_slide(number):
    img, d = canvas()
    header(d, "Backup: Q&A", number)
    bullets(d, ["How does migration work?", "What does support include?", "Which platforms are supported?",
                "How is data stored and backed up?"])
    return img


def main():
    slides = [
        title_slide("Product Launch 2026", "Quarterly business review", 1),
        bullet_slide("Agenda", ["The problem", "Market opportunity", "Product overview", "Pricing and timeline",
                                "Forecast and next steps"], 2),
        bullet_slide("The Problem", ["Teams juggle too many disconnected tools", "Setup takes weeks, not minutes",
                                     "Data is scattered and hard to trust"], 3),
        market_slide(4),
        bullet_slide("Product Overview", ["One workspace for planning and tracking", "Built-in reports and dashboards",
                                          "Runs on web, desktop and mobile"], 5),
        features_slide(6),
        chart_slide("Customer Research", "Share of surveyed teams rating each need as important (sample data)",
                    ["Speed", "Reporting", "Mobile", "Integrations", "Support"], [82, 74, 61, 68, 55], 7, suffix="%"),
        pricing_slide(8),
        timeline_slide(9),
        forecast_slide(10),
        title_slide("Thank You", "Questions welcome", 11),
        qa_slide(12),
    ]
    OUT.parent.mkdir(parents=True, exist_ok=True)
    slides[0].save(OUT, "PDF", save_all=True, append_images=slides[1:], resolution=200)
    print(f"Wrote {len(slides)} pages to {OUT}")


if __name__ == "__main__":
    main()
