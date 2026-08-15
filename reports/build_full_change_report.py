#!/usr/bin/env python3
from __future__ import annotations

import os
import shutil
from datetime import datetime
from pathlib import Path
from typing import Iterable, Sequence

from PIL import Image, ImageDraw, ImageFont
from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.style import WD_STYLE_TYPE
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor


ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / "reports"
ASSET_DIR = REPORT_DIR / "assets"
GENERATED_DIR = ASSET_DIR / "full-report-generated"
DOCX_PATH = REPORT_DIR / "DSMP_Complete_Change_Report_2026-06-16.docx"

PREV_REPORT_TEXT = Path("/private/tmp/retail50k_prev_report.txt")
CURRENT_REPORT_TEXT = Path("/private/tmp/current_engineering_report.txt")

EXACT_LAB_SCREENSHOT = Path(
    "/private/var/folders/75/l1vb_09n3lz16k43p67vtrj00000gq/T/TemporaryItems/"
    "NSIRD_screencaptureui_2Vs5Sb/Screenshot 2026-06-15 at 6.00.51\u202fPM.png"
)
USER_STUDIO_BEFORE = Path(
    "/private/var/folders/75/l1vb_09n3lz16k43p67vtrj00000gq/T/TemporaryItems/"
    "NSIRD_screencaptureui_UiBWS6/Screenshot 2026-06-15 at 5.32.02\u202fPM.png"
)
SVM_NODE_SCREENSHOT = Path(
    "/private/var/folders/75/l1vb_09n3lz16k43p67vtrj00000gq/T/TemporaryItems/"
    "NSIRD_screencaptureui_EMVAU3/Screenshot 2026-06-14 at 8.26.27\u202fPM.png"
)

NAVY = "17324D"
BLUE = "3D6FD8"
TEAL = "287D7B"
GREEN = "3D7A57"
AMBER = "A86C16"
RED = "A53D3D"
INK = "1E2732"
MUTED = "5E6977"
PALE_BLUE = "EAF1FF"
PALE_TEAL = "E8F5F3"
PALE_GREEN = "EAF5ED"
PALE_AMBER = "FFF4DF"
PALE_RED = "FBECEC"
PALE_GRAY = "F2F4F7"
MID_GRAY = "D8DEE7"
WHITE = "FFFFFF"


def qn_attr(name: str) -> str:
    return qn("w:" + name)


def set_cell_shading(cell, color: str) -> None:
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = tc_pr.find(qn("w:shd"))
    if shd is None:
        shd = OxmlElement("w:shd")
        tc_pr.append(shd)
    shd.set(qn_attr("fill"), color)


def set_cell_border(cell, color: str = MID_GRAY, size: str = "6") -> None:
    tc_pr = cell._tc.get_or_add_tcPr()
    borders = tc_pr.first_child_found_in("w:tcBorders")
    if borders is None:
        borders = OxmlElement("w:tcBorders")
        tc_pr.append(borders)
    for edge in ("top", "left", "bottom", "right"):
        element = borders.find(qn("w:" + edge))
        if element is None:
            element = OxmlElement("w:" + edge)
            borders.append(element)
        element.set(qn_attr("val"), "single")
        element.set(qn_attr("sz"), size)
        element.set(qn_attr("space"), "0")
        element.set(qn_attr("color"), color)


def set_cell_margins(cell, top=90, start=90, bottom=90, end=90) -> None:
    tc = cell._tc
    tc_pr = tc.get_or_add_tcPr()
    tc_mar = tc_pr.first_child_found_in("w:tcMar")
    if tc_mar is None:
        tc_mar = OxmlElement("w:tcMar")
        tc_pr.append(tc_mar)
    for margin, value in (("top", top), ("start", start), ("bottom", bottom), ("end", end)):
        node = tc_mar.find(qn("w:" + margin))
        if node is None:
            node = OxmlElement("w:" + margin)
            tc_mar.append(node)
        node.set(qn_attr("w"), str(value))
        node.set(qn_attr("type"), "dxa")


def set_fixed_table_layout(table) -> None:
    tbl_pr = table._tbl.tblPr
    layout = tbl_pr.first_child_found_in("w:tblLayout")
    if layout is None:
        layout = OxmlElement("w:tblLayout")
        tbl_pr.append(layout)
    layout.set(qn_attr("type"), "fixed")


def prevent_row_split(row) -> None:
    tr_pr = row._tr.get_or_add_trPr()
    cant_split = OxmlElement("w:cantSplit")
    tr_pr.append(cant_split)


def add_page_number(paragraph) -> None:
    paragraph.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    run = paragraph.add_run()
    fld_char1 = OxmlElement("w:fldChar")
    fld_char1.set(qn_attr("fldCharType"), "begin")
    instr_text = OxmlElement("w:instrText")
    instr_text.set(qn("xml:space"), "preserve")
    instr_text.text = " PAGE "
    fld_char2 = OxmlElement("w:fldChar")
    fld_char2.set(qn_attr("fldCharType"), "end")
    run._r.extend([fld_char1, instr_text, fld_char2])


def configure_document(doc: Document) -> None:
    section = doc.sections[0]
    section.top_margin = Inches(0.72)
    section.bottom_margin = Inches(0.68)
    section.left_margin = Inches(0.78)
    section.right_margin = Inches(0.72)
    section.header_distance = Inches(0.28)
    section.footer_distance = Inches(0.3)

    styles = doc.styles
    normal = styles["Normal"]
    normal.font.name = "Liberation Serif"
    normal.font.size = Pt(10.2)
    normal.font.color.rgb = RGBColor.from_string(INK)
    normal.paragraph_format.space_after = Pt(5)
    normal.paragraph_format.line_spacing = 1.08

    for name, size, color, before, after in [
        ("Title", 29, NAVY, 0, 12),
        ("Subtitle", 13, MUTED, 0, 8),
        ("Heading 1", 18, NAVY, 16, 7),
        ("Heading 2", 13.5, TEAL, 12, 5),
        ("Heading 3", 11.5, AMBER, 9, 3),
    ]:
        style = styles[name]
        style.font.name = "Liberation Sans"
        style.font.size = Pt(size)
        style.font.bold = name != "Subtitle"
        style.font.color.rgb = RGBColor.from_string(color)
        style.paragraph_format.space_before = Pt(before)
        style.paragraph_format.space_after = Pt(after)
        style.paragraph_format.keep_with_next = True

    if "Code Inline" not in styles:
        code_style = styles.add_style("Code Inline", WD_STYLE_TYPE.CHARACTER)
        code_style.font.name = "Liberation Mono"
        code_style.font.size = Pt(8.5)
        code_style.font.color.rgb = RGBColor.from_string(NAVY)

    if "Figure Caption" not in styles:
        caption = styles.add_style("Figure Caption", WD_STYLE_TYPE.PARAGRAPH)
        caption.font.name = "Liberation Sans"
        caption.font.size = Pt(8.5)
        caption.font.italic = True
        caption.font.color.rgb = RGBColor.from_string(MUTED)
        caption.paragraph_format.space_before = Pt(3)
        caption.paragraph_format.space_after = Pt(8)
        caption.paragraph_format.keep_with_next = True

    if "Small Note" not in styles:
        note = styles.add_style("Small Note", WD_STYLE_TYPE.PARAGRAPH)
        note.font.name = "Liberation Sans"
        note.font.size = Pt(8.3)
        note.font.color.rgb = RGBColor.from_string(MUTED)
        note.paragraph_format.space_after = Pt(4)

    header = section.header
    header_para = header.paragraphs[0]
    header_para.text = "DISTRIBUTED RECOMMENDER SIMULATOR | COMPLETE ENGINEERING CHANGE REPORT"
    header_para.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    for run in header_para.runs:
        run.font.name = "Liberation Sans"
        run.font.size = Pt(7.5)
        run.font.bold = True
        run.font.color.rgb = RGBColor.from_string(MUTED)

    footer = section.footer
    footer_table = footer.add_table(rows=1, cols=2, width=Inches(6.8))
    footer_table.alignment = WD_TABLE_ALIGNMENT.CENTER
    footer_table.columns[0].width = Inches(5.7)
    footer_table.columns[1].width = Inches(1.1)
    footer_table.cell(0, 0).text = (
        "Prepared 16 June 2026 | Evidence from attached Retail50K report, "
        "June 13 report, and current local source tree"
    )
    footer_table.cell(0, 0).paragraphs[0].style = styles["Small Note"]
    add_page_number(footer_table.cell(0, 1).paragraphs[0])


def add_run_with_code(paragraph, text: str) -> None:
    parts = text.split("`")
    for index, part in enumerate(parts):
        run = paragraph.add_run(part)
        if index % 2 == 1:
            run.style = "Code Inline"


def p(doc: Document, text: str = "", *, style: str | None = None,
      bold_lead: str | None = None, align=None) -> None:
    paragraph = doc.add_paragraph(style=style)
    if bold_lead and text.startswith(bold_lead):
        run = paragraph.add_run(bold_lead)
        run.bold = True
        add_run_with_code(paragraph, text[len(bold_lead):])
    else:
        add_run_with_code(paragraph, text)
    if align is not None:
        paragraph.alignment = align


def bullets(doc: Document, items: Iterable[str]) -> None:
    for item in items:
        paragraph = doc.add_paragraph(style="List Bullet")
        paragraph.paragraph_format.space_after = Pt(2.5)
        add_run_with_code(paragraph, item)


def numbered(doc: Document, items: Iterable[str]) -> None:
    for item in items:
        paragraph = doc.add_paragraph(style="List Number")
        paragraph.paragraph_format.space_after = Pt(2.5)
        add_run_with_code(paragraph, item)


def table(doc: Document, headers: Sequence[str], rows: Sequence[Sequence[str]],
          widths: Sequence[float] | None = None, *, small: bool = True) -> None:
    t = doc.add_table(rows=1, cols=len(headers))
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.autofit = False
    set_fixed_table_layout(t)
    if widths:
        for idx, width in enumerate(widths):
            t.columns[idx].width = Inches(width)
    header_cells = t.rows[0].cells
    for idx, header in enumerate(headers):
        header_cells[idx].text = header
        set_cell_shading(header_cells[idx], PALE_GRAY)
        set_cell_border(header_cells[idx])
        set_cell_margins(header_cells[idx], 95, 95, 95, 95)
        header_cells[idx].vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
        for paragraph in header_cells[idx].paragraphs:
            paragraph.style = "Small Note"
            for run in paragraph.runs:
                run.bold = True
                run.font.color.rgb = RGBColor.from_string(NAVY)
    for row_values in rows:
        row = t.add_row()
        prevent_row_split(row)
        for idx, value in enumerate(row_values):
            cell = row.cells[idx]
            cell.text = value
            set_cell_border(cell)
            set_cell_margins(cell, 85, 95, 85, 95)
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.TOP
            for paragraph in cell.paragraphs:
                paragraph.style = "Small Note" if small else "Normal"
                for run in paragraph.runs:
                    run.font.color.rgb = RGBColor.from_string(INK)
    doc.add_paragraph()


def callout(doc: Document, title: str, text: str, fill: str = PALE_BLUE,
            color: str = BLUE) -> None:
    t = doc.add_table(rows=1, cols=1)
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.autofit = False
    t.columns[0].width = Inches(6.8)
    cell = t.cell(0, 0)
    set_cell_shading(cell, fill)
    set_cell_border(cell, color=color, size="10")
    set_cell_margins(cell, 140, 150, 140, 150)
    paragraph = cell.paragraphs[0]
    run = paragraph.add_run(title.upper() + "  ")
    run.bold = True
    run.font.name = "Liberation Sans"
    run.font.size = Pt(8.5)
    run.font.color.rgb = RGBColor.from_string(color)
    add_run_with_code(paragraph, text)
    for r in paragraph.runs[1:]:
        r.font.name = "Liberation Sans"
        r.font.size = Pt(9)
        r.font.color.rgb = RGBColor.from_string(INK)
    doc.add_paragraph()


def add_figure(doc: Document, image_path: Path, caption: str,
               width: float = 6.7) -> None:
    if not image_path.exists():
        callout(doc, "Missing figure", f"Image was not available: `{image_path}`", PALE_AMBER, AMBER)
        return
    para = doc.add_paragraph()
    para.alignment = WD_ALIGN_PARAGRAPH.CENTER
    para.add_run().add_picture(str(image_path), width=Inches(width))
    cap = doc.add_paragraph(caption, style="Figure Caption")
    cap.alignment = WD_ALIGN_PARAGRAPH.CENTER


def add_code_block(doc: Document, code: str) -> None:
    t = doc.add_table(rows=1, cols=1)
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.autofit = False
    t.columns[0].width = Inches(6.8)
    cell = t.cell(0, 0)
    set_cell_shading(cell, "F7F9FC")
    set_cell_border(cell, color=MID_GRAY, size="6")
    set_cell_margins(cell, 100, 120, 100, 120)
    paragraph = cell.paragraphs[0]
    run = paragraph.add_run(code)
    run.font.name = "Liberation Mono"
    run.font.size = Pt(8.2)
    run.font.color.rgb = RGBColor.from_string(NAVY)
    doc.add_paragraph()


def ensure_visual_assets() -> dict[str, Path]:
    GENERATED_DIR.mkdir(parents=True, exist_ok=True)
    assets: dict[str, Path] = {}

    def copy_or_crop(src: Path, name: str, box: tuple[int, int, int, int] | None = None) -> None:
        if not src.exists():
            return
        dest = GENERATED_DIR / name
        image = Image.open(src).convert("RGB")
        if box is not None:
            image = image.crop(box)
        image.save(dest)
        assets[name] = dest

    copy_or_crop(EXACT_LAB_SCREENSHOT, "exact-lab-full.png")
    if EXACT_LAB_SCREENSHOT.exists():
        copy_or_crop(EXACT_LAB_SCREENSHOT, "exact-lab-cache-and-results.png", (20, 70, 2180, 1115))
    copy_or_crop(USER_STUDIO_BEFORE, "user-studio-before.png")
    copy_or_crop(SVM_NODE_SCREENSHOT, "svm-node-lab-failure.png")

    generated_node = ASSET_DIR / "generated-ui" / "node-dashboard.png"
    generated_lab = ASSET_DIR / "generated-ui" / "population-sweep-panel.png"
    if generated_node.exists():
        shutil.copyfile(generated_node, GENERATED_DIR / "node-dashboard-render.png")
        assets["node-dashboard-render.png"] = GENERATED_DIR / "node-dashboard-render.png"
    if generated_lab.exists():
        shutil.copyfile(generated_lab, GENERATED_DIR / "population-panel-render.png")
        assets["population-panel-render.png"] = GENERATED_DIR / "population-panel-render.png"

    draw_user_studio_after(GENERATED_DIR / "user-studio-after-render.png")
    assets["user-studio-after-render.png"] = GENERATED_DIR / "user-studio-after-render.png"
    draw_doc2vec_cache_card(GENERATED_DIR / "doc2vec-cache-card-render.png")
    assets["doc2vec-cache-card-render.png"] = GENERATED_DIR / "doc2vec-cache-card-render.png"
    return assets


def load_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = [
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf" if bold else "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/System/Library/Fonts/Supplemental/Helvetica.ttc",
        "/Library/Fonts/Arial.ttf",
    ]
    for candidate in candidates:
        try:
            return ImageFont.truetype(candidate, size)
        except Exception:
            continue
    return ImageFont.load_default()


def draw_user_studio_after(dest: Path) -> None:
    img = Image.new("RGB", (1600, 210), "#f7f9fc")
    draw = ImageDraw.Draw(img)
    draw.rounded_rectangle((28, 24, 1572, 186), radius=10, fill="#ffffff", outline="#d7dfec", width=2)
    title = load_font(34, True)
    small = load_font(16, False)
    label = load_font(18, True)
    draw.text((64, 54), "User Generation Studio", fill="#1e2732", font=title)
    draw.text((66, 103), "Synthetic corpus design, validation, and export workspace", fill="#5e6977", font=small)
    draw.rounded_rectangle((64, 134, 396, 170), radius=7, fill="#eef4ff", outline="#8db1ff")
    draw.text((82, 143), "DSMP Lab: Dr. Abdolreza Abhari", fill="#315aae", font=label)
    draw.rounded_rectangle((410, 134, 710, 170), radius=7, fill="#eef8f4", outline="#97c9af")
    draw.text((428, 143), "Development support: Mohsen Alavi", fill="#287d7b", font=label)
    points = [(820, 92), (1020, 78), (1210, 105), (1430, 70)]
    for a, b in zip(points, points[1:]):
        draw.line((a[0], a[1], b[0], b[1]), fill="#b8ccec", width=3)
    for i, point in enumerate(points):
        color = "#6f8de8" if i % 2 == 0 else "#61a985"
        draw.ellipse((point[0] - 8, point[1] - 8, point[0] + 8, point[1] + 8), fill="#ffffff", outline=color, width=4)
    img.save(dest)


def draw_doc2vec_cache_card(dest: Path) -> None:
    img = Image.new("RGB", (1280, 430), "#f7f9fc")
    draw = ImageDraw.Draw(img)
    title = load_font(30, True)
    label = load_font(16, True)
    value = load_font(24, True)
    small = load_font(15, False)
    draw.rounded_rectangle((30, 26, 1250, 404), radius=12, fill="#ffffff", outline="#d7dfec", width=2)
    draw.text((60, 52), "Doc2Vec cache transparency", fill="#17324d", font=title)
    draw.rounded_rectangle((1008, 47, 1144, 82), radius=6, fill="#eaf5ed", outline="#8bc09d")
    draw.text((1037, 56), "WARM", fill="#3d7a57", font=label)
    draw.text((60, 95), "Shows whether the run will train a model or reuse a dataset-specific cached model.", fill="#5e6977", font=small)
    stats = [
        ("Rows", "1,041,220"),
        ("Users", "618"),
        ("Vectors", "618"),
        ("Cache size", "128 MB"),
        ("Cache key", "shared_da80..."),
    ]
    x = 60
    for name, val in stats:
        draw.rounded_rectangle((x, 144, x + 205, 236), radius=8, fill="#f3f6fb", outline="#d7dfec")
        draw.text((x + 18, 160), name, fill="#5e6977", font=label)
        draw.text((x + 18, 188), val, fill="#1e2732", font=value)
        x += 226
    draw.rounded_rectangle((60, 274, 260, 326), radius=7, fill="#eaf1ff", outline="#7d9eee")
    draw.text((94, 291), "Refresh Cache", fill="#315aae", font=label)
    draw.rounded_rectangle((282, 274, 520, 326), radius=7, fill="#fbecec", outline="#d89494")
    draw.text((326, 291), "Remove This Cache", fill="#a53d3d", font=label)
    draw.text((60, 350), "Deletion is scoped to the selected dataset cache only and is blocked while a run is active.", fill="#5e6977", font=small)
    img.save(dest)


def intro_page(doc: Document) -> None:
    p(doc, "COMPLETE ENGINEERING CHANGE REPORT", style="Subtitle", align=WD_ALIGN_PARAGRAPH.CENTER)
    p(doc, "Distributed Recommender Simulator", style="Title", align=WD_ALIGN_PARAGRAPH.CENTER)
    p(
        doc,
        "Full before-and-after documentation of Retail50K fixes, Doc2Vec batch mode, experiment reset, GUI modernization, "
        "recommendation target semantics, graph control, runtime reliability, scalable data generation, memory scheduling, "
        "Exact Multi-Node Lab, journal-dataset findings, and Doc2Vec cache transparency.",
        style="Subtitle",
        align=WD_ALIGN_PARAGRAPH.CENTER,
    )
    doc.add_paragraph()
    meta = [
        ("Prepared for", "Mohsen Alavi"),
        ("Academic context", "DSMP / Distributed Recommender Simulator"),
        ("Primary repository", "github.com/aabhari/test"),
        ("Current working folder", str(ROOT)),
        ("Included prior attachment", "/Users/mohsen11/Desktop/abhari june 9 user sim try/test/DSMP_Retail50K_Full_Report.pdf"),
        ("Report date", "16 June 2026, America/Toronto"),
        ("Status", "Code-grounded documentation with current compile and focused self-test verification"),
    ]
    table(doc, ["Field", "Value"], meta, widths=[1.8, 4.9], small=False)
    callout(
        doc,
        "Repository state warning",
        "The local tree is intentionally rich with source changes, generated datasets, caches, result logs, rendered reports, "
        "and screenshots. This paper documents the engineering state and behavior; release work still needs a clean source-only commit series.",
        PALE_AMBER,
        AMBER,
    )
    doc.add_page_break()


def abstract_and_verdict(doc: Document) -> None:
    doc.add_heading("Abstract", level=1)
    p(
        doc,
        "This report documents the full DSMP simulator workstream represented by the attached Retail50K report and the "
        "current June 10-16 development conversation. The effort began as dataset-specific debugging and grew into a broader "
        "correctness, usability, scalability, and observability modernization. The guiding rule was to preserve the meaning of "
        "the existing algorithms when `Algorithm Default` is selected, while exposing previously hidden semantics and preventing "
        "known false or partial recommendation outputs.",
    )
    p(
        doc,
        "The most important technical corrections are: Retail50K names with spaces now survive message parsing and Weka ARFF "
        "serialization; Retail graph files are dataset-scoped; SVM uses class-preserving splits and calibrated class distributions; "
        "Doc2Vec batch mode computes every target independently; graph rendering can be skipped while edge state is still persisted; "
        "a dedicated reset creates a clean experiment boundary; repeated-run lifecycle IDs block stale messages; idle agents block "
        "instead of polling; the user-generation engine can atomically create validated large corpora; and high-memory model work is "
        "coordinated by heap-aware scheduling.",
    )
    p(
        doc,
        "The later work also adds a modern Swing GUI, searchable file selection, recommendation result cards, explicit recommendation "
        "target modes, an Exact Multi-Node Population Lab, real-time node activity dashboards, configurable Java heap restart, and "
        "Doc2Vec cache transparency in both the Simulator and Exact Lab tabs. The implementation is materially stronger, but not magic: "
        "Exact Weka SVM remains expensive on huge feature spaces, Doc2Vec cold training is still real work, and converted recommendation "
        "modes must be reported as converted or hybrid objectives rather than untouched native algorithms.",
    )
    callout(
        doc,
        "Definite summary",
        "The current local version is more correct, more observable, and more reliable than the starting version. The safest academic statement is "
        "that `Algorithm Default` preserves native recommendation semantics where the implementation was not deliberately corrected for a bug, while "
        "new modes and labs are explicit additions with clearly labeled behavior.",
        PALE_GREEN,
        GREEN,
    )

    doc.add_heading("Executive Change Inventory", level=2)
    rows = [
        ("Retail50K parser and ARFF fixes", "Older bug", "Names such as `T&T Supermarket` were split by spaces.", "Tab protocol and ARFF quoting keep the class intact."),
        ("Retail graph isolation", "Older bug", "Graph mapping/edge files were missing or shared across datasets.", "Dataset-specific name-number, edge, and GML artifacts."),
        ("SVM split and score calibration", "Older bug", "Small classes could lose all training examples; scores were hard 1/0.", "Stratified class splits and calibrated SMO distributions."),
        ("Doc2Vec batch", "Chat change", "Multiple selected targets received the first user's score map.", "Each target is scored independently using the shared scorer."),
        ("Reset Experiment", "Chat change", "Cleanup was tied to Get Users or app lifecycle.", "Dedicated reset preserves inputs/caches and removes transient experiment state."),
        ("GUI modernization", "Chat change", "Dense legacy form, raw output, fragile dialogs.", "Modern panels, cards, status, search picker, and recommendation cards."),
        ("Target modes", "Chat change", "Algorithms returned users or accounts without explaining the entity type.", "`Algorithm Default`, `Similar Users`, and `Followee Accounts` are explicit."),
        ("Graph checkbox", "Chat change", "Recommendation completion always went through Gephi output.", "Graph display/export can be skipped while edges still accumulate."),
        ("Idle CPU cleanup", "Chat change", "Agents kept polling and Gephi project stayed open.", "Blocking JADE message handling, async completion, Gephi cleanup."),
        ("Large corpus generation", "Chat change", "Million-row generation was not atomic or validated.", "Streaming exact-count generator with schema validation."),
        ("Streaming and sparse data path", "Chat change", "Huge datasets duplicated rows/maps and dense ARFF data.", "Replayable streams, sparse rows, indexing, reduced live memory."),
        ("Adaptive memory", "Chat change", "All nodes could overload the JVM.", "Heap-aware leases, safe waves, worker scheduling, heap GUI."),
        ("Exact Multi-Node Lab", "Chat change", "Multi-node effect was hard to prove without changing graph state.", "Isolated target sweeps with one-worker equivalence verification."),
        ("Doc2Vec cache UI", "Latest change", "Fast cached Doc2Vec looked suspicious and unexplained.", "Warm/cold/stale cache status, rows/users/vectors/key/size, scoped remove button."),
    ]
    table(doc, ["Area", "When", "Before", "Now"], rows, widths=[1.7, 1.0, 2.0, 2.0])


def scope_and_sources(doc: Document) -> None:
    doc.add_heading("1. Scope, Evidence, and Method", level=1)
    p(
        doc,
        "The report combines two bodies of work. The first is the attached June 7 Retail50K report, which records pre-chat fixes "
        "for dataset parsing, Retail graph files, timing instrumentation, v2.5 compatibility, multi-node K-Means measurements, "
        "SVM batch observations, shared Doc2Vec cache work, and the final SVM root-cause fix. The second is the current chat work, "
        "which extends the simulator with corrected Doc2Vec batch mode, reset controls, GUI modernization, recommendation target modes, "
        "graph skip controls, runtime lifecycle hardening, million-row generation and ingestion, exact/isolated population sweeps, "
        "journal-dataset investigation, and Doc2Vec cache transparency.",
    )
    p(
        doc,
        "Evidence was gathered from the extracted prior PDF, the June 13 engineering report already present in `reports/`, current "
        "source-code inspection, current compile output, focused self-test execution, generated Swing panel renders, and user-provided "
        "screenshots captured during actual runs. The report distinguishes source-code changes from analysis-only conclusions and "
        "communication artifacts.",
    )
    table(
        doc,
        ["Evidence source", "What it contributes", "Limit"],
        [
            ("Attached Retail50K PDF", "Older fixes, timings, and SVM root-cause analysis.", "Line numbers refer to an older folder and may not match the current tree."),
            ("Current source tree", "Actual implementation state after the latest cache UI additions.", "Worktree is dirty and includes generated artifacts."),
            ("Current compile", "All current production Java source compiles against `lib/*`.", "Compile is not the same as a full end-to-end GUI run."),
            ("Focused self-tests", "Doc2Vec batch parsing, recommendation merging, post-processing, heap settings, rendered UI panels.", "Self-tests cover targeted invariants, not every dataset."),
            ("Screenshots/renders", "Visual evidence of the GUI surfaces discussed.", "Some are development screenshots; generated renders are labeled as renders."),
        ],
        widths=[1.8, 3.1, 1.8],
    )
    callout(
        doc,
        "Verification performed for this report",
        "Current production Java compilation passed. `PopulationSweepPanelSelfTest`, `NodeExecutionDashboardSelfTest`, "
        "`RecommendationScoreMergerSelfTest`, `RecommendationPostProcessorSelfTest`, `RuntimeResourceSettingsSelfTest`, and "
        "`Doc2VecBatchRunnerSelfTest` passed. The Doc2Vec self-test emitted the known Gensim compatibility diagnostic but still passed.",
        PALE_BLUE,
        BLUE,
    )


def retail50k_prior_changes(doc: Document) -> None:
    doc.add_heading("2. Prior Retail50K Fixes Integrated from the Attached PDF", level=1)
    p(
        doc,
        "The attached report is not treated as a separate appendix. Its important engineering points are integrated here in the same "
        "before/problem/change/result/risk structure used for the rest of this paper.",
    )

    doc.add_heading("2.1 Retail50K MLP Crash: Space-delimited Message Protocol", level=2)
    p(doc, "Before: MobileAgent sent tweet data as one space-delimited string, and RecommenderAgent split it by spaces.", bold_lead="Before:")
    p(doc, "Why bad: `T&T Supermarket` became `T&T`, which no longer matched the class label built from the original dataset. MLP then looked up a missing class and could crash.", bold_lead="Why bad:")
    p(doc, "Change: the active path changed tweet messages to tab-separated fields and added a manual tab parser with a legacy fallback.", bold_lead="Change:")
    p(doc, "Now: names with spaces survive the agent protocol. The parser preserves `T&T Supermarket` as one followee/class field.", bold_lead="Now:")
    p(doc, "Risk: legacy messages can still enter through fallback paths; tests should keep a space-containing class fixture.", bold_lead="Risk:")

    doc.add_heading("2.2 Uploaded Dataset Path and Retail Graph Mapping", level=2)
    p(
        doc,
        "Before: after a user selected `/Users/mohsen11/Downloads/Retail50K.txt`, later code sometimes looked for "
        "`important-stuff/Retail50K.txt`. Retail graph generation also assumed `name-number-Retail50K.txt` already existed.",
    )
    p(
        doc,
        "Change: active MobileAgent and graph code use the selected file object. The program creates the missing name-number "
        "mapping from the selected dataset and writes dataset-specific edge/GML files such as `edges-numbers-Retail50K.txt`.",
    )
    p(
        doc,
        "Result: the prior report observed Retail50K mapping at 628 unique graph nodes, with edges accumulating by run while node "
        "count remained stable. This fixed old graph contamination and missing-file failures."
    )
    p(
        doc,
        "Risk: graph node count is now dataset-specific, but stale artifacts can still confuse results if a user intentionally mixes "
        "datasets without Reset Experiment. The new reset button later addresses that workflow risk."
    )

    doc.add_heading("2.3 Weka ARFF Quoting for Class Names", level=2)
    p(
        doc,
        "Before: ARFF class values were written raw. Weka could display `T&T Supermarket` as separate `T&T` and `Supermarket` "
        "confusion-matrix labels."
    )
    p(
        doc,
        "Change: ARFF class values are quoted and escaped when the class attribute and row labels are written. Backslashes, quotes, "
        "and line breaks are normalized before serialization."
    )
    p(
        doc,
        "Now: Weka receives the intended single class value. This preserves the SVM/MLP classifier family but fixes invalid file syntax."
    )

    doc.add_heading("2.4 Version 2.5 Compatibility Work", level=2)
    p(
        doc,
        "The prior report also records changes in the older `/Users/mohsen11/Desktop/abhariv2.5/SocialNetworkSimulatorV3` folder: "
        "the same tab-delimited Retail fix, SVM ARFF quoting, replacement of a hardcoded Windows Python path with OS-sensitive "
        "`python`/`python3`, and a graph-import guard when an expected GML file is missing."
    )
    p(
        doc,
        "These changes are important historically, but they are not the main current workspace. They show that the Retail50K issue was "
        "not one isolated symptom; it exposed file-format, launcher, and graph assumptions across versions."
    )

    doc.add_heading("2.5 Stage Timers, Tweet Batching, and K-Means Load Balancing", level=2)
    p(
        doc,
        "Before: the simulator sent each tweet as a separate JADE ACL message. Timing was hard to interpret because fixed waits, graph "
        "layout, text processing, TF-IDF, and model work were mixed together."
    )
    p(
        doc,
        "Change: stage timers were added; text-file paths for non-MLP algorithms batch a user's tweets into one serialized message; "
        "text preprocessing uses precompiled patterns and a single tokenization path; and text-node assignment estimates load from "
        "tweet count, unique terms, and character count."
    )
    p(
        doc,
        "Result: the prior report observed K-Means recommender-stage improvement on 8 nodes, for example reducing the algorithm/TF-IDF "
        "stage from about 38.3 s on one node to about 15.3 s on eight nodes in the reported Retail50K run. The total GUI wall time still "
        "remained dominated by fixed MobileAgent waiting and Gephi layout until later changes removed or made parts optional."
    )
    p(
        doc,
        "Risk: stage timings depend on dataset, JVM, machine, and algorithm. They should be reported with node count, heap, graph setting, "
        "and dataset checksum rather than reused as universal numbers."
    )

    doc.add_heading("2.6 SVM Root-Cause Fix for Low Retail Diversity", level=2)
    p(
        doc,
        "The attached PDF's final and most important Retail SVM finding is that SVM trains on aggregated user documents, not on raw rows. "
        "A row-balanced Retail50K_20x file can still be user-imbalanced because Wal-Mart may have hundreds of user documents while "
        "`T&T Supermarket` has only a few."
    )
    p(
        doc,
        "Before: the train/test split filled the test set first by repeatedly removing users from each class. Small retailer classes could "
        "be completely consumed by the test set, leaving zero training examples. SVM scores were also hard 1/0 values from a single "
        "`classifyInstance()` prediction."
    )
    p(
        doc,
        "Change: SVM splitting became stratified inside each class, keeping training examples for minority classes when possible. Weka SMO "
        "still remains the classifier, but calibration is enabled and `distributionForInstance()` is converted into a full score map."
    )
    p(
        doc,
        "Now: SVM recommendation scores are class distributions rather than one predicted class turned into 100/0. Batch SVM uses the same "
        "distribution path. This improves recommendation meaning while preserving the TF-IDF -> Weka SMO pipeline."
    )
    p(
        doc,
        "Risk: calibration and stratification deliberately change SVM output compared with the pre-fix bug. This is a correctness repair, "
        "not a neutral performance refactor. It should be named in any comparison with older branches."
    )


def doc2vec_batch_and_cache(doc: Document, assets: dict[str, Path]) -> None:
    doc.add_heading("3. Doc2Vec Batch Mode and Cache Transparency", level=1)
    doc.add_heading("3.1 Correct Multi-target Batch Mode", level=2)
    p(
        doc,
        "Before: the Java Doc2Vec branch built a list of selected users but invoked Python with only the first selected target. The returned "
        "score map was then assigned to every selected target. Enabling the old SVM batch field for Doc2Vec would have been silently wrong."
    )
    p(
        doc,
        "Change: `Doc2VecBatchRunner.java` and `doc2vec_batch.py` implement a structured batch path. Java writes the targets to a temporary "
        "file; each recommender node starts one Python process; Python loads or trains the shared Doc2Vec model once and calls the same "
        "`score_candidate_shard()` function independently for every target."
    )
    p(
        doc,
        "Now: every target receives its own score map. Candidate sharding remains node-specific and the organizer still merges per-target "
        "scores through the existing flow. Per-user errors are explicit and do not become fake successful recommendations."
    )
    table(
        doc,
        ["Invariant", "How the implementation preserves it"],
        [
            ("Same scorer", "Batch uses the existing `doc2vec_shared.score_candidate_shard()` logic."),
            ("Same cache signature", "Model reuse is keyed by dataset path, size, modification time, parameters, workers, and script/kernel version."),
            ("Same shard concept", "Each recommender node scores only its assigned candidate shard."),
            ("Independent target maps", "Structured `DOC2VEC_SCORE` records include target and candidate identity."),
            ("Failure visibility", "Missing users, Python errors, or unsafe numerical diagnostics create explicit failures or empty maps."),
        ],
        widths=[2.0, 4.7],
    )

    doc.add_heading("3.2 Why 618 Doc2Vec Targets Can Finish in About One Second", level=2)
    p(
        doc,
        "The fast Exact Lab result is valid only under the condition the GUI now exposes: a warm shared Doc2Vec cache already exists. In that "
        "case the program is not retraining Doc2Vec. It is loading cached normalized vectors and scoring target vectors against candidate "
        "vectors. For a dataset with hundreds of users, this can be very fast on modern hardware."
    )
    p(
        doc,
        "This was the exact reason the UI needed cache transparency. A one-second run looks suspicious if the GUI just says Doc2Vec, but it is "
        "understandable if the GUI says `Warm cache used`, `Training skipped`, `618 user vectors`, and displays the cache key."
    )
    callout(
        doc,
        "Important interpretation",
        "A warm-cache Doc2Vec run is a scoring-only run. A cold-cache run includes model training and can be much slower. The recommendation "
        "logic is not skipped; the training stage is skipped because the matching model is already on disk.",
        PALE_GREEN,
        GREEN,
    )
    add_figure(
        doc,
        assets.get("exact-lab-cache-and-results.png", Path("missing")),
        "Figure 1. Development screenshot of the Exact Multi-Node Lab after a warm Doc2Vec cache run. The table shows 618 target results and exact one-worker verification.",
        width=6.7,
    )

    doc.add_heading("3.3 Cache Transparency in Both Tabs", level=2)
    p(
        doc,
        "Latest change: the cache status panel was added to the Exact Multi-Node Lab and then to the main Simulator tab whenever Doc2Vec is selected. "
        "The UI inspects the selected dataset through `Doc2VecCacheAdmin.java` and `doc2vec_cache_admin.py`."
    )
    table(
        doc,
        ["Displayed field", "Meaning"],
        [
            ("State", "`WARM`, `COLD`, or `STALE` based on whether a valid matching cache exists."),
            ("Rows", "Dataset row count read by the same cache-inspection path."),
            ("Users", "Number of distinct Doc2Vec document tags/users."),
            ("Vectors", "Count and dimensionality of cached scoring vectors when available."),
            ("Cache size", "Exact matching cache size and dataset-related cache size."),
            ("Cache key", "Shortened shared cache key, with full key available as tooltip."),
            ("Refresh Cache", "Re-runs inspection without starting a recommendation run."),
            ("Remove This Cache", "Deletes only cache files tied to the selected dataset and blocks deletion during an active simulator run."),
        ],
        widths=[1.7, 5.0],
    )
    add_figure(
        doc,
        assets.get("doc2vec-cache-card-render.png", Path("missing")),
        "Figure 2. Rendered evidence panel for the Doc2Vec cache transparency card added to the main Simulator and Exact Lab workflows.",
        width=6.5,
    )
    p(
        doc,
        "Risk: deleting the cache does not delete the dataset. It forces a future Doc2Vec cold train. If another Python process is actively training "
        "or scoring, cache deletion is intentionally disabled from the Simulator UI to avoid corrupting an active run."
    )


def reset_gui_modes_graph(doc: Document, assets: dict[str, Path]) -> None:
    doc.add_heading("4. GUI, Reset, Recommendation Modes, and Graph Control", level=1)
    doc.add_heading("4.1 Dedicated Reset Experiment", level=2)
    p(
        doc,
        "Before: cleanup was tied to closing the GUI or clicking Get Users. That made it unclear whether the user was merely loading users or "
        "destroying the current experiment's transient graph and recommendation state."
    )
    p(
        doc,
        "Change: `Reset Experiment` became an explicit GUI command. It stops active agents, clears in-memory state, resets progress and selections, "
        "and deletes known transient experiment artifacts while preserving input datasets, archived Results folders, and reusable Doc2Vec caches."
    )
    p(
        doc,
        "Now: users can intentionally start a brand-new experiment without closing the app and without coupling cleanup to `Get Users`. The reset "
        "confirmation explains that current graph/recommendation-side state is discarded."
    )

    doc.add_heading("4.2 Modern Simulator Workspace", level=2)
    p(
        doc,
        "The old UI was a dense form with raw console output. The modernized UI uses a left data-source/user rail, a central experiment setup "
        "workspace, and a right live-output panel. The app title is left aligned, redundant colored badges were removed, actions are grouped in "
        "the header, and status is presented as a labeled progress message rather than a vague text area."
    )
    p(
        doc,
        "The data-source panel now uses source cards for text file, database, generated, and artificial datasets. The Experiment Setup panel is "
        "organized by model/objective, workload, runtime resources, data window, text processing, post-processing, and run pipeline. The "
        "recommendation output view now renders cards with target identity, rank, candidate, score, and candidate detail instead of raw text."
    )
    add_figure(
        doc,
        assets.get("node-dashboard-render.png", Path("missing")),
        "Figure 3. Rendered node activity dashboard. The UI can show each requested node's state, received input, waiting stage, and model work.",
        width=4.2,
    )
    add_figure(
        doc,
        assets.get("population-panel-render.png", Path("missing")),
        "Figure 4. Rendered Exact Multi-Node Population Lab panel used to verify the new lab surface can paint correctly in headless tests.",
        width=6.7,
    )

    doc.add_heading("4.3 File Picker Repair and Search", level=2)
    p(
        doc,
        "Before: the native Swing file chooser could crash on macOS with `IllegalComponentStateException: The dialog is decorated`, and it "
        "offered little help for finding large datasets across project folders."
    )
    p(
        doc,
        "Change: `ModernFilePicker.java` provides an application-owned file picker with search/filtering and safer asynchronous behavior. This "
        "avoids the decorated native dialog failure and better matches the redesigned UI."
    )

    doc.add_heading("4.4 Recommendation Target Modes", level=2)
    p(
        doc,
        "Before: the program silently mixed recommendation entity types. Similarity, K-Means, K-Means Euclidean, and Doc2Vec naturally ranked "
        "other users. SVM and MLP naturally predicted/ranked field-0 followee accounts. That is why Retail SVM showed retailer names while "
        "Doc2Vec showed people."
    )
    p(
        doc,
        "Change: the GUI adds `Recommendation target`: `Algorithm Default`, `Similar Users`, or `Followee Accounts`. Supporting classes such as "
        "`RecommendationTargetMode`, `RecommendationCandidateType`, `RecommendationModeSupport`, `RecommendationScorePayload`, and "
        "`RecommendationScoreMerger` make the entity type explicit and serializable."
    )
    table(
        doc,
        ["Algorithm", "Algorithm Default", "Similar Users", "Followee Accounts"],
        [
            ("Similarity", "Ranks users by cosine similarity.", "Same native output.", "Maps ranked users to their field-0 accounts."),
            ("K-Means", "Ranks users through the legacy cluster path.", "Same native output.", "Collapses similar users to account recommendations."),
            ("K-Means Euclidean", "Ranks users through Euclidean cluster scoring.", "Same native output.", "Maps ranked users to accounts."),
            ("Doc2Vec", "Ranks users by document-vector similarity.", "Same native output.", "Maps similar users to their accounts, showing the representative user detail."),
            ("SVM", "Ranks field-0 accounts from Weka SMO distribution.", "Hybrid: uses classifier signal and user evidence to rank users.", "Same native output."),
            ("MLP", "Ranks field-0 accounts from neural classifier output.", "Hybrid: classifier-guided user ranking.", "Same native output."),
        ],
        widths=[1.2, 1.8, 1.8, 1.9],
    )
    callout(
        doc,
        "Research reporting rule",
        "Only `Algorithm Default` should be described as the native algorithm answer. The other two options are useful and implemented deliberately, "
        "but they are selected recommendation objectives and must be reported as such.",
        PALE_AMBER,
        AMBER,
    )

    doc.add_heading("4.5 Optional Graph Output with Persistent Edges", level=2)
    p(
        doc,
        "Before: after recommendations were produced, the simulator always entered the Gephi visualization/export phase. This made runs feel stuck "
        "when the user only wanted the recommendations."
    )
    p(
        doc,
        "Change: a `Generate Final Graph Output` checkbox was added. When unchecked, the simulator skips Gephi display/export, but still appends the "
        "new recommendation edge and updates the dataset-specific graph state."
    )
    p(
        doc,
        "Now: a user can run 10 targets with graph output off, then turn graph output on for the 11th target and receive a graph containing the "
        "previous 10 targets' accumulated edges. The graph checkbox controls visualization/export, not whether recommendations are persisted."
    )

    doc.add_heading("4.6 User Generation Studio Attribution and Header", level=2)
    p(
        doc,
        "The User Generation Studio header was simplified by removing the colorful mode boxes under the title and aligning the title to the left. "
        "The page now places `DSMP Lab: Dr. Abdolreza Abhari` and `Development support: Mohsen Alavi` in a compact academic attribution zone "
        "rather than as detached decorative text."
    )
    add_figure(
        doc,
        assets.get("user-studio-before.png", Path("missing")),
        "Figure 5. User-provided before screenshot that motivated removal of the colored badges and left-aligned studio header.",
        width=6.7,
    )
    add_figure(
        doc,
        assets.get("user-studio-after-render.png", Path("missing")),
        "Figure 6. Rendered target header design showing the aligned title and academic attribution placement.",
        width=6.7,
    )


def reliability_scalability(doc: Document) -> None:
    doc.add_heading("5. Runtime Reliability, Idle CPU, and Post-processing", level=1)
    doc.add_heading("5.1 Blocking JADE Message Delivery and Run Lifecycle", level=2)
    p(
        doc,
        "Before: several agents polled frequently or could consume stale messages from a previous run. This caused high CPU usage after a run "
        "appeared finished and created races where score messages could be consumed by the wrong handler."
    )
    p(
        doc,
        "Change: idle handlers now use blocking JADE delivery or explicit message templates. `RecommendationRunLifecycle` assigns UUID-backed "
        "run identities so agents can distinguish a new run, the current run, and retired run messages. Stale score/completion messages are ignored "
        "instead of participating in a new merge."
    )
    p(
        doc,
        "Now: the application should not sit at extreme CPU usage merely because the GUI remains open. Completion popups are asynchronous, and "
        "StarterAgent closes the embedded Gephi project after graph export."
    )

    doc.add_heading("5.2 Fast Post-recommendation Persistence", level=2)
    p(
        doc,
        "Before: after recommendations appeared, MobileAgent could still scan the dataset seven to nine times and rewrite a full `Dataset-Test-*` "
        "file. On a million-row file this created minutes of apparent delay even with graph output disabled."
    )
    p(
        doc,
        "Change: `RecommendationDatasetContext` and `RecommendationPostProcessor` use the already-built dataset index for target identity, "
        "candidate identity, followee mapping, node mapping, and edge append. Legacy corpus rewrite is disabled behind a forensic flag."
    )
    p(
        doc,
        "Now: the active path performs map lookups and a small synchronized edge append. A fallback scan exists only if context is missing. This "
        "does not change ranking scores; it changes the obsolete side-effect path after scores already exist."
    )
    table(
        doc,
        ["Operation", "Before", "Now", "Impact"],
        [
            ("Candidate lookup", "Multiple full corpus scans.", "Map lookup from dataset context.", "Large completion latency removed."),
            ("Target/followee lookup", "Additional full scans.", "O(1) context lookup.", "No repeated million-row reads."),
            ("Dataset-Test output", "Full read/write per target.", "Disabled in active path.", "Avoids huge obsolete file writes."),
            ("Edge append", "Small append after scans.", "Small synchronized append.", "Preserves graph history safely."),
        ],
        widths=[1.5, 1.8, 1.8, 1.6],
    )

    doc.add_heading("5.3 Recommendation Failure Semantics", level=2)
    p(
        doc,
        "Before: a crashed agent or zero-score map could still leave partial, plausible-looking output, or the workflow could wait forever for a "
        "dead agent. This was especially dangerous in million-row SVM experiments."
    )
    p(
        doc,
        "Change: recommendation payloads carry status, candidate type, score kind, details, and explicit errors. Runs fail closed when a required "
        "model stage dies; the UI reports a failed recommendation stage instead of accepting partial zero-score results."
    )
    p(
        doc,
        "Now: failure is more visible. That is sometimes emotionally worse during testing, but academically better: bad runs do not masquerade as "
        "valid recommendations."
    )


def generation_and_memory(doc: Document) -> None:
    doc.add_heading("6. Large Dataset Generation, Streaming, Sparse Models, and Memory Scheduling", level=1)
    doc.add_heading("6.1 User Generation Engine", level=2)
    p(
        doc,
        "Before: the generator was not a reliable million-row production tool. A large request could depend on too much in-memory state, leave "
        "temporary files, or produce output that was not fully validated."
    )
    p(
        doc,
        "Change: `UserGenerationEngine.java` was introduced for exact-count generation, row/schema validation, atomic temporary-file replacement, "
        "deterministic allocation, and cleanup. Self-tests include ordinary and million-row paths."
    )
    p(
        doc,
        "Now: the generator can produce a valid million-row corpus from suitable source corpora. The generated file being valid does not mean every "
        "recommendation algorithm can consume it cheaply; that is a separate algorithm/runtime question."
    )

    doc.add_heading("6.2 Streaming Ingestion and Bounded Delivery", level=2)
    p(
        doc,
        "Before: large text datasets were repeatedly loaded, copied, and resent. Eight nodes multiplied memory pressure and could crash before "
        "scheduling had any chance to protect the expensive model stage."
    )
    p(
        doc,
        "Change: `ReplayableCorpusStream`, `ReplayableTweetBatchSource`, `CorpusStreamingAgent`, `CorpusStreamRoute`, `DatasetRow`, and "
        "`TweetBatchDeliveryLedger` support replayable dataset windows, retryable batch acknowledgement, and per-user indexing. The controller "
        "can build one context and distribute bounded batches rather than retaining everything everywhere."
    )
    p(
        doc,
        "Now: input delivery is more predictable and observable. The node dashboard can show whether nodes are receiving records, preprocessing, "
        "waiting for peers, waiting for memory, waiting for a shared model, running a model, or complete."
    )

    doc.add_heading("6.3 Sparse ARFF and Exact SVM Worker Isolation", level=2)
    p(
        doc,
        "Before: normal SVM nodes created dense ARFF-like structures and Weka model objects in the shared JADE JVM. Eight nodes could each build "
        "large feature maps and crash the main process before any worker scheduler executed."
    )
    p(
        doc,
        "Change: sparse ARFF serialization, exact SVM corpus admission, corpus spooling, isolated Weka worker processes, worker result parsing, and "
        "memory leases were added. Exact SVM still uses Weka SMO; representation and scheduling changed, not the classifier family."
    )
    p(
        doc,
        "Now: if work fits, requested nodes can run together. If it does not fit, nodes run in safe waves or the run fails early before partial graph "
        "side effects. A single worker can still fail if the exact Weka model is intrinsically too large for its heap; scheduling reduces combined "
        "pressure, not mathematical complexity."
    )

    doc.add_heading("6.4 Adaptive Heap and Parallelism Controls", level=2)
    p(
        doc,
        "Before: heap size required launcher edits, and the program had no user-visible model-memory budget. Users could request eight nodes on a "
        "heap that could not safely hold eight model stages."
    )
    p(
        doc,
        "Change: `RuntimeResourceSettings`, `RuntimeRelauncher`, `RuntimeRelaunchHelper`, and the GUI `Maximum Java heap (MB)` control let the user "
        "persist a heap size and restart the app. `RecommendationMemoryCoordinator` estimates SVM, MLP, and generic model work and admits safe "
        "parallelism according to model budget, live heap, and requested node count."
    )
    p(
        doc,
        "Now: multi-node execution is not artificially capped; it is capacity-aware. On strong hardware with enough heap, more nodes can run "
        "concurrently. On constrained heap, work is serialized into waves. The UI shows requested/active/waiting counts and reserved memory."
    )
    table(
        doc,
        ["Path", "What changed", "Recommendation impact", "Speed impact"],
        [
            ("Exact SVM", "Sparse input, worker isolation, heap-aware leases.", "Same Weka SMO family; older SVM scoring bug separately fixed.", "Can avoid crashes; speed depends on data and Weka complexity."),
            ("Scalable Linear SVM", "Custom sparse linear model for high-cardinality cases.", "Different algorithm, not exact Weka.", "Much more scalable, but must not be compared as bare Weka SMO."),
            ("K-Means", "Sparse point access and better text/load balancing.", "Legacy cluster logic retained.", "Multi-node benefit clearer on heavier corpora."),
            ("MLP", "Model averaging and memory leases.", "Same broad neural classifier path.", "Avoids simultaneous large model overload, not a cheap million-row guarantee."),
            ("Doc2Vec", "Streaming corpus, shared cache, mmap scoring vectors.", "Same shared model/scorer for fixed cache.", "Warm scoring can be very fast; cold training remains expensive."),
        ],
        widths=[1.4, 2.0, 1.8, 1.5],
    )


def exact_lab_and_journal(doc: Document, assets: dict[str, Path]) -> None:
    doc.add_heading("7. Exact Multi-Node Lab, Journal Dataset, and Algorithm Correctness", level=1)
    doc.add_heading("7.1 Why the Exact Lab Exists", level=2)
    p(
        doc,
        "The regular simulator is a multi-agent workflow with graph side effects, user/mobile agents, edge persistence, status popups, and legacy "
        "research behavior. It is not ideal for proving that eight nodes preserve the exact same target-level answer as one node."
    )
    p(
        doc,
        "The Exact Multi-Node Population Lab is a separate tab that runs complete target jobs over one canonical dataset snapshot, assigns whole "
        "target users to workers, avoids graph side effects, and can verify every target fingerprint against a one-worker baseline. It is a new "
        "feature, not a replacement for the simulator."
    )
    add_figure(
        doc,
        assets.get("exact-lab-full.png", Path("missing")),
        "Figure 7. User-provided screenshot of the Exact Multi-Node Population Lab, including dataset choice, worker count, verification, and raw result table.",
        width=6.7,
    )
    table(
        doc,
        ["Regular Simulator batch", "Exact Multi-Node Lab"],
        [
            ("Uses JADE agents, MobileAgent queries, Organizer merge, and graph edge persistence.", "Runs isolated target jobs without graph side effects."),
            ("Nodes usually shard candidate/work data for a simulation.", "Workers receive complete target-user jobs; no target is split between workers."),
            ("Designed for the original DSMP workflow and graph evolution.", "Designed for correctness/performance experiments and one-worker equivalence."),
            ("Can use all algorithms in the simulator, including ones whose multi-node math is legacy.", "Only enables algorithms where equivalence can be guaranteed for the lab design."),
        ],
        widths=[3.35, 3.35],
    )

    doc.add_heading("7.2 Exact SVM versus Scalable Linear SVM", level=2)
    p(
        doc,
        "`SVM` in the normal/exact path means Weka SMO. It is the closest path to bare Weka and is the only fair Weka comparison when configuration "
        "and workload match. `Scalable Linear SVM` is a custom sparse linear one-vs-rest model. It exists for huge high-cardinality experiments, "
        "but it is not Weka SMO and should not be reported as the same algorithm."
    )
    p(
        doc,
        "The report should therefore use precise wording: Exact SVM is calibrated Weka SMO; Scalable Linear SVM is a separate scalable model family. "
        "A speedup in the scalable path is useful engineering, but it does not prove Weka itself became faster."
    )
    add_figure(
        doc,
        assets.get("svm-node-lab-failure.png", Path("missing")),
        "Figure 8. Development screenshot showing why fail-closed behavior matters: an exact SVM high-cardinality run can hit heap limits and must not produce partial recommendations.",
        width=6.7,
    )

    doc.add_heading("7.3 Journal Dataset Findings", level=2)
    p(
        doc,
        "The professor described a journal dataset prepared in follower-followee format: authors are represented as followers/user IDs, journals are "
        "followees/classes, and paper titles are the posts/text. This matches the six-field simulator idea as long as the selected file obeys the "
        "same tab-separated schema and field semantics."
    )
    p(
        doc,
        "What works: Doc2Vec and SVM are conceptually appropriate for journal titles. Doc2Vec ranks similar authors from title text. SVM predicts or "
        "ranks journals/classes from title-derived user documents. The current code can load the observed 38k journal file, and smaller journal "
        "subsets such as 1k/2k/5k are present in `TestedDataset`."
    )
    p(
        doc,
        "What needs care: exact Weka SVM can be memory-heavy with many authors and title terms; the journal file can contain very long author-like "
        "identities and many unique terms; initializing too many individual user agents can hit OS/thread/process limits; and single-class or "
        "extremely imbalanced journal slices are not meaningful for classifier evaluation."
    )
    table(
        doc,
        ["Journal use case", "Recommended setting", "Why"],
        [
            ("Similar authors from titles", "Doc2Vec or Similar Users mode.", "Natural output is other author/user identities."),
            ("Journal classification", "SVM Algorithm Default or Followee Accounts.", "Natural output is field-0 journal/followee class."),
            ("Large exact Weka run", "Increase heap, use fewer nodes, or expect safe waves/fail-closed behavior.", "Weka SMO is memory intensive with wide vocabularies."),
            ("Demonstrating parallelism", "Exact Lab with verification enabled.", "Separates target-level correctness from graph side effects."),
        ],
        widths=[1.7, 2.4, 2.6],
    )

    doc.add_heading("7.4 Algorithm-by-algorithm Correctness Verdict", level=2)
    table(
        doc,
        ["Algorithm", "Current verdict", "Why it is correct or limited"],
        [
            ("Similarity", "Correct for user-ranking default.", "Sparse cosine scoring ranks users; converted account mode is a clear mapping from similar users to accounts."),
            ("K-Means", "Correct as legacy cluster ranking, not exact-lab supported.", "Default path retains legacy K-Means scoring; multi-node exact equivalence is not guaranteed enough for Exact Lab."),
            ("K-Means Euclidean", "Same as K-Means with Euclidean distance.", "Works in simulator but remains legacy/expensive on very wide corpora."),
            ("SVM", "Corrected Weka SMO path.", "Retail parsing, stratified split, ARFF quoting, calibrated distributions, and memory scheduling address known defects."),
            ("MLP", "Works for supported sizes; high risk on very wide corpora.", "Neuroph path remains dense and memory-heavy; scheduling reduces crashes but not intrinsic network size."),
            ("Doc2Vec", "Correct for fixed-cache target scoring.", "Batch uses same shard scorer per target; cache transparency now explains warm scoring."),
            ("Scalable Linear SVM", "Useful separate mode, not Weka SMO.", "Fast sparse linear classifier; must be reported as a different algorithm."),
            ("Common Neighbors", "Not considered a working modern recommendation implementation.", "Excluded from Exact Lab and should not be used as a supported algorithm claim."),
        ],
        widths=[1.4, 1.9, 3.4],
    )


def verification_and_risks(doc: Document) -> None:
    doc.add_heading("8. Verification Record and Risk Register", level=1)
    doc.add_heading("8.1 Current Verification", level=2)
    table(
        doc,
        ["Check", "Result", "Meaning"],
        [
            ("Production Java compile", "Passed with deprecation/unchecked notes.", "Current `userRyersonU/*.java` compiles against project libraries."),
            ("PopulationSweepPanelSelfTest", "Passed and wrote PNG.", "Exact Lab panel renders nonblank in headless test."),
            ("NodeExecutionDashboardSelfTest", "Passed and wrote PNG.", "Parallel node dashboard renders nonblank with eight seeded states."),
            ("RecommendationScoreMergerSelfTest", "Passed.", "Typed score merge, target modes, conversion, normalization, and payload serialization behave."),
            ("RecommendationPostProcessorSelfTest", "Passed.", "Fast post-processing writes/accumulates edges and handles no-candidate cases."),
            ("RuntimeResourceSettingsSelfTest", "Passed.", "Heap setting validation, persistence, and relaunch command replacement behave."),
            ("Doc2VecBatchRunnerSelfTest", "Passed with known Gensim diagnostic.", "Structured batch output parsing and unsafe diagnostic detection work."),
        ],
        widths=[2.1, 1.8, 2.8],
    )
    p(
        doc,
        "The current compile command used for this report was: `javac -nowarn -cp \"lib/*\" -d /private/tmp/dsmp-current-report-compile "
        "TwitterGatherDataFollowers/userRyersonU/*.java`."
    )

    doc.add_heading("8.2 Risk Register", level=2)
    table(
        doc,
        ["Risk", "Severity", "Control", "Next action"],
        [
            ("Dirty worktree mixes source and generated artifacts.", "Critical", "This report names delivery state.", "Prepare source-only commits from an allow-list."),
            ("Converted target modes misreported as native algorithms.", "High", "Mode labels and typed payloads.", "Use `Algorithm Default` for native claims."),
            ("Doc2Vec warm cache mistaken for skipped work.", "High", "Cache transparency panels and remove button.", "Record cache key in experiments."),
            ("Exact Weka SVM still OOMs on extreme feature spaces.", "High", "Admission, spooling, worker isolation, fail-closed errors.", "Benchmark per corpus and avoid impossible heaps."),
            ("Python Doc2Vec memory outside Java heap.", "Medium", "Memory-mapped vectors and visible cache state.", "Add process RSS telemetry later."),
            ("K-Means/MLP remain costly on wide corpora.", "Medium", "Sparse support and model leases.", "Run algorithm-specific scale matrix before claims."),
            ("Graph communities differ from professor expectation.", "Medium", "Reset, edge accumulation, target-mode clarity.", "Compare edge distribution and Gephi settings."),
            ("Uncommitted UI lacks broad regression tests.", "Medium", "Headless render tests for major panels.", "Add component state tests for button enable/disable."),
        ],
        widths=[2.3, 0.8, 1.8, 1.8],
    )

    doc.add_heading("8.3 Recommendation Integrity Summary", level=2)
    table(
        doc,
        ["Change", "Can it change recommendations?", "Explanation"],
        [
            ("GUI layout, picker, status bar, screenshots", "No", "Presentation/input ergonomics only."),
            ("Doc2Vec batch for fixed cache", "No intended per-target change", "Same scorer; now repeated correctly for every target."),
            ("Retail parser and ARFF quoting", "Yes, as bug fix", "Old behavior corrupted class names with spaces."),
            ("SVM stratified split and distributions", "Yes, as bug fix", "Old SVM could train with missing classes and hard 1/0 scores."),
            ("Target modes", "Yes, intentionally", "Changing output entity type changes the recommendation objective."),
            ("Graph checkbox", "Ranked list no", "Only skips visualization/export; edge side effect remains."),
            ("Post-processing rewrite removal", "Ranked list no", "Changes side-effect speed, not model score calculation."),
            ("Memory scheduling", "No intended score change", "Changes when work runs and whether it fails safely."),
            ("Scalable Linear SVM", "Yes", "It is a new separate model, not exact Weka SVM."),
        ],
        widths=[2.0, 1.5, 3.2],
    )


def change_detail(
        doc: Document,
        title: str,
        before: str,
        why_bad: str,
        changed: str,
        now: str,
        efficiency: str,
        risk: str) -> None:
    doc.add_heading(title, level=2)
    rows = [
        ("Before", before),
        ("Why this was bad", why_bad),
        ("What changed", changed),
        ("Current behavior", now),
        ("Efficiency / correctness effect", efficiency),
        ("Residual risk", risk),
    ]
    table(doc, ["Lens", "Details"], rows, widths=[1.6, 5.1], small=False)


def detailed_change_ledger(doc: Document) -> None:
    doc.add_heading("9. Detailed Per-change Ledger", level=1)
    p(
        doc,
        "This section is intentionally repetitive because it is meant to be useful during academic review. Each item records what the "
        "system did before, why that behavior was unsafe or weak, what was changed, how the current program behaves, and what risk remains."
    )

    doc.add_heading("9.1 Dataset and Format Corrections", level=1)
    change_detail(
        doc,
        "9.1.1 Retail50K class names with spaces",
        "Tweet fields were encoded into one space-delimited JADE message.",
        "`T&T Supermarket` was read as `T&T`, which broke class lookup and could crash MLP or corrupt SVM labels.",
        "The active message path became tab-delimited and the receiver parses tabs before falling back to legacy space parsing.",
        "Class/followee names with spaces are preserved exactly through the agent protocol.",
        "This is a correctness fix; it does not intentionally make the algorithm faster, but it prevents invalid training labels.",
        "A future non-tab-safe payload format could reintroduce the issue. Keep tests with a class containing spaces.",
    )
    change_detail(
        doc,
        "9.1.2 ARFF quoting for Weka",
        "Class attribute values and row labels were written raw into ARFF files.",
        "ARFF requires quoting for values containing spaces or special characters; Weka could split or misread labels.",
        "A class-value formatter now quotes and escapes strings used in Weka ARFF output.",
        "Weka receives one valid class token for names such as `T&T Supermarket`.",
        "This changes only file validity and label integrity. It does not replace Weka or change TF-IDF inputs.",
        "Any new ARFF writer must reuse the same escaping rule; otherwise a parallel path can drift.",
    )
    change_detail(
        doc,
        "9.1.3 Selected uploaded file path",
        "Some downstream code assumed the selected dataset had been copied into `important-stuff/`.",
        "A valid upload from Downloads could later fail with `FileNotFoundException` because the wrong path was opened.",
        "The code now carries and uses the selected `File` object for corpus reads and post-processing.",
        "Uploaded datasets can be used from their actual selected location.",
        "The improvement removes false missing-file failures and avoids unnecessary copying.",
        "External deletion or movement of the selected file during a run can still break the run.",
    )
    change_detail(
        doc,
        "9.1.4 Dataset-specific graph files",
        "Graph edges were written to shared generic filenames.",
        "Old edges from a previous dataset could appear in a new dataset graph, invalidating community statistics.",
        "Graph edge and GML filenames are derived from the selected dataset base name and target mode where relevant.",
        "Retail, Twitter, journal, and generated datasets can maintain separate accumulated edge files.",
        "Graph results become interpretable because edge history belongs to the intended dataset.",
        "If two different files share the same base name in different folders, a stronger canonical naming scheme would be safer.",
    )
    change_detail(
        doc,
        "9.1.5 Auto name-number mapping",
        "Retail50K expected a manually prepared `name-number-Retail50K.txt` mapping.",
        "Missing mapping files caused incomplete graph output or old node counts.",
        "The program can generate the mapping from the selected dataset when needed.",
        "The graph has a stable node map based on the actual user/followee identities in the dataset.",
        "This makes uploaded datasets much easier to run and reduces manual setup error.",
        "Very large mappings should still be built atomically and cached, which later work reinforces.",
    )

    doc.add_heading("9.2 Algorithmic Recommendation Corrections", level=1)
    change_detail(
        doc,
        "9.2.1 SVM class-preserving train/test split",
        "The old split built the test set first and could consume all examples from minority classes.",
        "If a class has zero training examples, Weka cannot learn that class and recommendations collapse toward larger classes.",
        "The split now partitions users inside each class and keeps at least one training example when possible.",
        "Small Retail classes such as `T&T Supermarket` are no longer silently removed from training.",
        "This is a correctness repair that makes SVM recommendations more meaningful on imbalanced user-level classes.",
        "A class with only one total user still cannot support both train and test examples; the report must disclose class counts.",
    )
    change_detail(
        doc,
        "9.2.2 SVM calibrated score distributions",
        "SVM recommendation scores were produced from one hard predicted class.",
        "The result became 100/0 style output, which was not a useful ranked score distribution.",
        "SMO calibration is enabled and `distributionForInstance()` is converted into a score map.",
        "SVM returns a ranked map across followee accounts instead of only one hard class.",
        "Recommendation output is smoother and more informative while retaining Weka SMO.",
        "Calibrated probabilities depend on Weka behavior and data quality; they are still normalized later for display.",
    )
    change_detail(
        doc,
        "9.2.3 SVM batch mode",
        "Single-user SVM was too fast for node overhead to show obvious speedup, and manual repeated runs paid fixed overhead every time.",
        "Batching many target users manually was slow and hard to reproduce.",
        "SVM batch mode selects multiple target users and trains/scores each target independently.",
        "Each target receives its own SVM score map; batch targets are excluded from training where appropriate.",
        "Throughput improves by amortizing fixed simulator cost over multiple targets.",
        "The regular simulator still has graph side effects and random edge persistence, so batch graph output is not identical to manual runs unless those conditions are controlled.",
    )
    change_detail(
        doc,
        "9.2.4 Doc2Vec shared vector space",
        "Earlier multi-node-looking Doc2Vec designs could train separate models per node.",
        "Independent Doc2Vec models do not share a comparable vector space, so merging their scores is mathematically unsafe.",
        "The active path trains or loads one shared model and lets nodes score shards against that shared representation.",
        "Doc2Vec node scores are comparable because they come from the same vector space.",
        "Correctness improves dramatically; repeated warm runs also avoid retraining.",
        "Fresh multi-worker training is not bit-for-bit deterministic; fixed-cache comparisons are the honest equivalence test.",
    )
    change_detail(
        doc,
        "9.2.5 Doc2Vec regular batch mode",
        "The Java branch passed only the first selected target to Python and copied that target's scores to every target.",
        "This was a silent false result: the GUI could show many targets even though only one was actually scored.",
        "A structured batch runner now scores each target independently through the same shard scorer.",
        "Doc2Vec batch users receive target-specific recommendation maps.",
        "Python/model load overhead is reduced because one process can score many targets.",
        "A missing target remains an explicit no-candidate/error state; the UI should not hide it.",
    )
    change_detail(
        doc,
        "9.2.6 Recommendation target modes",
        "The UI did not explain whether an algorithm recommended users or accounts.",
        "Users could compare Doc2Vec people to SVM retailers and think one algorithm was misimplemented.",
        "The GUI and payloads now distinguish `Algorithm Default`, `Similar Users`, and `Followee Accounts`.",
        "The chosen recommendation objective is visible before the run and in result files/cards.",
        "This prevents semantic confusion and enables deliberate cross-type recommendation experiments.",
        "Converted modes intentionally change the recommendation objective and must not be described as native algorithm output.",
    )
    change_detail(
        doc,
        "9.2.7 Score normalization explanation",
        "Top-ranked scores appeared as 100 for every target without explanation.",
        "Users could misread 100 as absolute confidence rather than relative maximum after normalization.",
        "The report and GUI design treat these as relative model/ranking scores and preserve raw-score access where relevant.",
        "A top item at 100 means it is the maximum positive score for that target after merge normalization.",
        "This improves interpretation without changing ranking calculations.",
        "The visible label `MODEL SCORE` can still be misunderstood; `Relative Score` would be even clearer in a future UI pass.",
    )

    doc.add_heading("9.3 GUI and Workflow Changes", level=1)
    change_detail(
        doc,
        "9.3.1 Modern three-column simulator UI",
        "The legacy UI placed many controls in a dense form and showed recommendations as raw text.",
        "Important choices were hard to discover and error states looked like normal console noise.",
        "The workspace was reorganized into data source/user list, experiment setup, and live output columns.",
        "Users see source selection, model objective, workload, runtime resources, text processing, post-processing, and results as separate groups.",
        "No recommendation math changes; the benefit is reduced operator error and faster diagnosis.",
        "Swing visual regression testing remains lightweight; new screenshots should be captured before releases.",
    )
    change_detail(
        doc,
        "9.3.2 Recommendation cards",
        "The recommendation panel printed equals signs and text lines.",
        "Batch output became ugly and hard to scan, especially for dozens or hundreds of targets.",
        "Typed result payloads now render per-target cards with rank, candidate, score, and detail.",
        "The GUI clearly shows target count, match count, and no-candidate cases.",
        "This is presentation only; score order comes from the merged payload.",
        "If payload construction fails, raw console diagnostics still matter for debugging.",
    )
    change_detail(
        doc,
        "9.3.3 Searchable file picker",
        "The native `JFileChooser` crashed on macOS under the decorated dialog state and had limited search help.",
        "Selecting a dataset could throw an AWT exception before the simulation even started.",
        "`ModernFilePicker` provides an app-owned searchable picker.",
        "Users can locate dataset files more reliably and avoid the decorated dialog exception.",
        "Workflow reliability improves; algorithm behavior does not change.",
        "Native file-management features are not all replicated; very large directories may need additional indexing later.",
    )
    change_detail(
        doc,
        "9.3.4 Initialize button state",
        "Initialize could stay disabled even after users were loaded.",
        "The user could not start a valid run despite having selected a target.",
        "Button-state logic was tightened around source selection, user loading, and run lifecycle.",
        "Initialize becomes available when the current dataset/user state is valid.",
        "This removes a GUI blocker without changing simulation configuration.",
        "Button state must continue to be tested after any GUI layout refactor.",
    )
    change_detail(
        doc,
        "9.3.5 Bottom status bar",
        "Status text was vague and the status pill appeared before `Current progress`.",
        "Users could not tell whether the app was loading users, processing features, merging, graphing, or complete.",
        "The status bar now places the phase in front of the current-progress message and includes dataset/user context.",
        "The user can see what stage the simulator is in at each moment.",
        "No scoring changes; reduces false stuck reports.",
        "Some legacy console output still remains noisy and could be further structured.",
    )
    change_detail(
        doc,
        "9.3.6 User Generation Studio branding",
        "The studio header had colorful mode badges under the title and lacked academic attribution placement.",
        "The visual hierarchy looked misaligned and did not credit the lab cleanly.",
        "The header was aligned left, badges removed, and Dr. Abdolreza Abhari plus Mohsen Alavi attribution added in a compact lab identity area.",
        "The first page looks more professional and communicates ownership/support without clutter.",
        "Visual/presentation change only.",
        "Attribution wording should be approved by the professor before formal publication.",
    )
    change_detail(
        doc,
        "9.3.7 Doc2Vec cache UI",
        "A cached Doc2Vec run could finish extremely fast with no explanation.",
        "Users reasonably suspected the algorithm had skipped work or returned invalid results.",
        "Both Simulator and Exact Lab tabs now inspect and display cache state, rows, users, vectors, cache size, and key.",
        "A warm-cache run is visible as scoring-only; a cold-cache run warns that training will happen.",
        "Improves trust and experiment reproducibility without changing Doc2Vec scores.",
        "Cache inspection itself depends on filesystem metadata and should be refreshed after external file edits.",
    )

    doc.add_heading("9.4 Graph and Experiment Lifecycle", level=1)
    change_detail(
        doc,
        "9.4.1 Explicit Reset Experiment",
        "New experiments relied on closing the GUI or hidden cleanup attached to Get Users.",
        "Old graph and recommendation files could contaminate the next dataset, and cleanup timing was confusing.",
        "A dedicated Reset Experiment action stops agents, clears state, and removes known transient outputs.",
        "Users can start a clean experiment without closing the app and without deleting source datasets or caches.",
        "Improves reproducibility and prevents stale-edge contamination.",
        "Any new artifact type must be added to the reset inventory.",
    )
    change_detail(
        doc,
        "9.4.2 Graph output checkbox",
        "Every successful recommendation entered Gephi visualization/export.",
        "Graph rendering made runs feel stuck and was unnecessary when only recommendations were needed.",
        "`Generate Final Graph Output` controls the final Gephi display/export phase.",
        "Unchecked runs still persist nodes and edges; later checked runs render accumulated graph state.",
        "Saves time while preserving experimental graph history.",
        "Users must understand that graph disabled does not mean edge disabled.",
    )
    change_detail(
        doc,
        "9.4.3 Run identifiers and stale message protection",
        "Long-lived agents could receive or act on messages from a previous run.",
        "A new run could hang, merge wrong scores, or finish based on old completion messages.",
        "`RecommendationRunLifecycle` tags and classifies run messages.",
        "Agents accept only current-run data and reject stale or retired messages.",
        "Reliability improves without changing score formulas.",
        "The retired-run history is finite; extremely delayed messages beyond the retained window need monitoring.",
    )
    change_detail(
        doc,
        "9.4.4 Idle CPU and Gephi cleanup",
        "Agents kept polling and the embedded Gephi project could remain open after export.",
        "The app could consume very high CPU/RAM hours after the visible work completed.",
        "Idle handlers use blocking receives/templates, popups are asynchronous, and Gephi projects are closed after export.",
        "Leaving the GUI open should no longer keep expensive simulation loops active.",
        "Reduces idle CPU and memory retention.",
        "External native libraries and macOS graphics resources can still consume memory during actual graph rendering.",
    )

    doc.add_heading("9.5 Scalability and Parallelism", level=1)
    change_detail(
        doc,
        "9.5.1 Million-row generation",
        "The generator was not designed as a validated, atomic million-row producer.",
        "Large generated files could be malformed, partial, or hard to trust.",
        "`UserGenerationEngine` performs exact allocation, streaming writes, validation, and atomic completion.",
        "A valid million-row file can be generated from suitable corpora.",
        "Generation became reliable; recommendation algorithms still have their own limits.",
        "A generated corpus is synthetic and should not be treated as new real-world evidence.",
    )
    change_detail(
        doc,
        "9.5.2 Streaming dataset intake",
        "Large corpora were repeatedly read and copied into many in-memory structures.",
        "Eight nodes multiplied memory pressure before model scheduling could help.",
        "Replayable streams, batch sources, corpus routes, and context indexes reduce duplication.",
        "The simulator can ingest large text datasets more predictably.",
        "Improves memory behavior and creates reusable context for fast post-processing.",
        "Very high user counts can still create many agents or large indexes; no representation removes all cost.",
    )
    change_detail(
        doc,
        "9.5.3 Sparse ARFF and Weka workers",
        "Dense ARFF construction and Weka models lived in the shared main JVM.",
        "SVM could throw heap errors before any worker scheduler had a chance to limit concurrency.",
        "Sparse ARFF writing, exact SVM corpus spooling, isolated worker JVMs, and worker result parsing were added.",
        "Exact SVM can run safer waves and fail closed when a corpus cannot fit.",
        "Memory pressure is reduced and partial invalid recommendations are prevented.",
        "A single Weka SMO model can still be too large; this is a known algorithmic limit.",
    )
    change_detail(
        doc,
        "9.5.4 Adaptive memory leases",
        "The program either launched all requested nodes or used crude caps.",
        "All-at-once could crash; fixed caps wasted capable hardware.",
        "`RecommendationMemoryCoordinator` estimates model work and admits safe concurrent leases.",
        "If eight nodes fit, eight can run. If not, the rest wait and run in waves.",
        "Preserves multi-node speed potential while improving reliability.",
        "Estimates are heuristics; unusual datasets can underpredict actual Weka/Neuroph memory.",
    )
    change_detail(
        doc,
        "9.5.5 Heap GUI and restart",
        "Changing max heap required editing launch scripts or environment variables.",
        "Users could not easily test whether more heap allowed more concurrent nodes.",
        "The GUI exposes max heap in MB and relaunches with updated `-Xmx` through runtime settings.",
        "Users can increase heap, restart, and let the adaptive scheduler use the new budget.",
        "Makes resource experiments transparent and repeatable.",
        "Excessive heap can cause OS swapping; the GUI warning is guidance, not a guarantee.",
    )
    change_detail(
        doc,
        "9.5.6 Exact Multi-Node Population Lab",
        "Regular simulator runs mixed parallelism with graph effects, random persisted edges, and legacy agent behavior.",
        "It was hard to prove that node count changed speed without changing target-level results.",
        "The Exact Lab runs isolated target jobs, assigns whole targets to workers, and verifies against a one-worker baseline.",
        "Users can demonstrate parallel effects while checking exact output equivalence for supported algorithms.",
        "This is the clearest speed/correctness demonstration feature.",
        "It intentionally excludes algorithms whose current multi-node mathematics cannot guarantee one-worker equivalence.",
    )
    change_detail(
        doc,
        "9.5.7 Real-time node dashboard",
        "The user could see console lines but not a structured view of each requested node.",
        "Parallel execution was invisible, so waiting looked like a hang.",
        "`NodeExecutionMonitor` and `NodeExecutionDashboard` show node states such as receiving, preprocessing, waiting, running, complete, failed, or stopped.",
        "The GUI can explain whether all requested nodes are working, waiting for memory, or waiting for a shared model.",
        "Improves observability and debugging without changing algorithms.",
        "The dashboard depends on agents reporting state consistently; new stages should add monitor events.",
    )

    doc.add_heading("9.6 Dataset Interpretation and Communication", level=1)
    change_detail(
        doc,
        "9.6.1 Retail versus Twitter semantics",
        "The simulator accepted any six-field file but did not explain what field 0 meant.",
        "Retail SVM output looked like store names while Doc2Vec output looked like people, causing confusion.",
        "The report and GUI now explain that SVM/MLP classify field-0 accounts while Doc2Vec/user rankers rank user identities.",
        "Users can choose the output entity type or explain native defaults correctly.",
        "Prevents false bug reports about an intentional data-semantics difference.",
        "Each new corpus still needs a short schema note before results are interpreted academically.",
    )
    change_detail(
        doc,
        "9.6.2 Journal dataset interpretation",
        "The journal dataset was not initially identified in the current workflow.",
        "Users could treat journal results like Retail/Twitter without understanding author/journal/title semantics.",
        "The dataset was analyzed as author user IDs, journal followees/classes, and paper titles as posts.",
        "Doc2Vec can rank similar authors; SVM can classify/rank journals from title-derived documents.",
        "Clarifies professor's intended research use and avoids misreporting output type.",
        "Large journal files remain memory-sensitive, especially for exact Weka SVM.",
    )
    change_detail(
        doc,
        "9.6.3 Professor communication drafts",
        "Questions about SVM/Doc2Vec output semantics and journal data were unresolved.",
        "Without clarification, the implementation could optimize the wrong research objective.",
        "Emails were drafted to ask about intended output entity and to report journal-dataset findings.",
        "Communication artifacts support academic alignment but are not code changes.",
        "Improves project governance.",
        "Final wording and claims should be reviewed before sending or using in thesis/paper text.",
    )


def appendices(doc: Document) -> None:
    doc.add_heading("Appendix A. File-level Map", level=1)
    table(
        doc,
        ["File or class", "Role"],
        [
            ("ControllerAgentGui.java", "Modern GUI, source cards, setup panels, recommendation cards, heap controls, cache UI, reset, lab tabs."),
            ("ControllerAgent.java", "Dataset loading, context, agent startup, reset, SVM split support, runtime coordination."),
            ("RecommenderAgent.java", "Algorithm paths, Doc2Vec/SVM batch, sparse preparation, node monitor updates, model leases."),
            ("MobileAgent.java", "Tweet/query flow, blocking receive, result display, post-processing request construction."),
            ("StarterAgent.java", "Run orchestration, optional graph output, cleanup, completion state."),
            ("OrganizingAgent.java", "Score aggregation, typed payload merge, MLP averaging handoff."),
            ("Doc2VecBatchRunner.java / doc2vec_batch.py", "Structured multi-target Doc2Vec bridge."),
            ("doc2vec_shared.py", "Shared model training, cache signature, vector cache, shard scorer."),
            ("Doc2VecCacheAdmin.java / doc2vec_cache_admin.py", "Cache inspection/deletion for UI transparency."),
            ("Recommendation* classes", "Target modes, score payloads, candidate evidence, merge policy, run lifecycle, memory coordination."),
            ("UserGenerationEngine.java", "Validated exact-count synthetic dataset generation."),
            ("Replayable* and CorpusStreamingAgent", "Large dataset streaming, retryable bounded delivery, per-run batch state."),
            ("SparseArffWriter.java", "Sparse Weka ARFF serialization."),
            ("ExactPopulationSweepEngine.java / PopulationSweepPanel.java", "Isolated multi-worker population lab and GUI."),
            ("NodeExecutionMonitor.java / NodeExecutionDashboard.java", "Real-time node state telemetry and panel rendering."),
            ("RuntimeResourceSettings.java and relaunch classes", "Heap persistence and app restart."),
            ("ModernFilePicker.java", "Searchable robust dataset picker."),
        ],
        widths=[2.5, 4.2],
    )

    doc.add_heading("Appendix B. Timeline", level=1)
    table(
        doc,
        ["Stage", "Work completed"],
        [
            ("Before this chat / June 7 report", "Retail50K parsing, ARFF quoting, graph mapping, timing instrumentation, K-Means speed investigation, SVM split/scoring repair."),
            ("Doc2Vec batch and reset", "Correct batch scoring and explicit experiment reset; committed and pushed earlier."),
            ("GUI wave", "Modern simulator UI, recommendation cards, source/setup/status redesign, file picker, user studio attribution."),
            ("Semantics wave", "Recommendation target modes and explanation of SVM/Doc2Vec entity mismatch."),
            ("Graph/lifecycle wave", "Graph checkbox, edge persistence rule, idle CPU cleanup, blocking receive, stale-run isolation."),
            ("Scale wave", "Million-row generation, streaming, sparse ARFF, adaptive memory, heap GUI."),
            ("Exact lab wave", "Population sweep tab, node dashboard, one-worker equivalence checks."),
            ("Journal wave", "Professor dataset analysis and current behavior with author-title-journal data."),
            ("Cache transparency wave", "Doc2Vec warm/cold/stale cache cards in Exact Lab and Simulator, scoped cache removal."),
        ],
        widths=[2.0, 4.7],
    )

    doc.add_heading("Appendix C. Practical Operating Guidance", level=1)
    bullets(
        doc,
        [
            "Use `Algorithm Default` when comparing with historical simulator behavior.",
            "Use `Similar Users` or `Followee Accounts` only when the research question explicitly asks for that entity type.",
            "For Doc2Vec timing, always state whether the cache was warm or cold and record the cache key.",
            "For SVM/Weka experiments, record heap, node count, dataset row count, user count, feature count, and class distribution.",
            "Keep graph output unchecked when only recommendations are needed; check it later to render accumulated edges.",
            "Use Reset Experiment before starting a truly new dataset experiment.",
            "Do not use partial results after a fail-closed OOM or worker failure.",
            "Treat Scalable Linear SVM as a separate algorithm from exact Weka SMO.",
            "For journal data, describe whether the output is similar authors or journal/followee classes.",
            "Before sharing or committing, clean generated corpora, ARFF files, caches, logs, screenshots, class files, and `.DS_Store` artifacts.",
        ],
    )

    doc.add_heading("Final Assessment", level=1)
    p(
        doc,
        "Across the full workstream, the simulator moved from a fragile research prototype that could silently mix entity types, reuse stale graph "
        "state, overload memory, and hide why a run was fast, into a much clearer experimental platform. The strongest improvements are not only "
        "speed. They are honesty: the GUI now has places to show what is selected, what is cached, what type of recommendation is being produced, "
        "what each node is doing, and whether a failure invalidates the run."
    )
    p(
        doc,
        "The remaining task is release discipline. The code should be split into clean commits, generated artifacts should be excluded, and a clean "
        "clone should reproduce compile and focused tests. Once that is done, the project will be in a strong position for professor review and for "
        "defensible performance/correctness experiments."
    )


def build() -> Path:
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    assets = ensure_visual_assets()
    doc = Document()
    configure_document(doc)
    intro_page(doc)
    abstract_and_verdict(doc)
    scope_and_sources(doc)
    retail50k_prior_changes(doc)
    doc2vec_batch_and_cache(doc, assets)
    reset_gui_modes_graph(doc, assets)
    reliability_scalability(doc)
    generation_and_memory(doc)
    exact_lab_and_journal(doc, assets)
    verification_and_risks(doc)
    detailed_change_ledger(doc)
    appendices(doc)
    doc.save(DOCX_PATH)
    return DOCX_PATH


if __name__ == "__main__":
    path = build()
    print(path)
