#!/usr/bin/env python3
"""
Generate a visual preview PNG for a Minecraft 9x6 GUI from a JSON layout.

Usage:
  python3 scripts/generate_gui_preview.py \
    --layout testasset/city_hub_layout.json \
    --output testasset/city_hub_preview.png \
    --background testasset/generic_54.png \
    --scale 4
"""

from __future__ import annotations

import argparse
import json
import math
import os
from pathlib import Path
from typing import Any, Dict, Tuple

from PIL import Image, ImageColor, ImageDraw, ImageFont


ROLE_COLORS = {
    "city_summary": "#5f6f86",
    "primary_cta": "#3a8b60",
    "secondary_cta": "#5c78a8",
    "footer_nav": "#8d4e4e",
    "footer_help": "#56608f",
    "footer_status": "#7a7a55",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate a GUI preview image from JSON.")
    parser.add_argument("--layout", required=True, help="Path to the input JSON layout file.")
    parser.add_argument("--output", required=True, help="Path to the output PNG file.")
    parser.add_argument(
        "--background",
        default="",
        help="Optional background image (e.g. testasset/generic_54.png).",
    )
    parser.add_argument("--scale", type=int, default=1, help="Pixel scale for background/slot math.")
    parser.add_argument("--offset-x", type=int, default=0, help="Grid offset X in pixels.")
    parser.add_argument("--offset-y", type=int, default=0, help="Grid offset Y in pixels.")
    parser.add_argument(
        "--cell-size",
        type=int,
        default=18,
        help="Base cell size in source texture pixels before scaling.",
    )
    return parser.parse_args()


def load_json(path: Path) -> Dict[str, Any]:
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def ensure_parent(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)


def load_font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = [
        "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
        "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/Library/Fonts/Arial.ttf",
        "/System/Library/Fonts/Supplemental/Helvetica.ttf",
    ]
    for font_path in candidates:
        if os.path.exists(font_path):
            try:
                return ImageFont.truetype(font_path, size=size)
            except OSError:
                continue
    return ImageFont.load_default()


def create_base_canvas(
    background_path: Path | None,
    scale: int,
    grid_w: int,
    grid_h: int,
    cell_size: int,
) -> Image.Image:
    if background_path and background_path.exists():
        bg = Image.open(background_path).convert("RGBA")
        if scale > 1:
            bg = bg.resize((bg.width * scale, bg.height * scale), Image.Resampling.NEAREST)
        return bg

    # Fallback canvas if no background is supplied.
    slot = cell_size * scale
    width = slot * grid_w + 220
    height = slot * grid_h + 180
    return Image.new("RGBA", (width, height), (26, 28, 33, 255))


def compute_grid_origin(
    canvas_size: Tuple[int, int],
    grid_w: int,
    grid_h: int,
    slot_size: int,
    offset_x: int,
    offset_y: int,
) -> Tuple[int, int]:
    canvas_w, canvas_h = canvas_size
    grid_px_w = grid_w * slot_size
    grid_px_h = grid_h * slot_size
    left = int((canvas_w - grid_px_w) / 2) + offset_x
    top = int((canvas_h - grid_px_h) / 2) + offset_y
    return left, top


def pick_role_color(role: str) -> Tuple[int, int, int, int]:
    hex_color = ROLE_COLORS.get(role, "#6a6a6a")
    rgb = ImageColor.getrgb(hex_color)
    return (rgb[0], rgb[1], rgb[2], 180)


def short_label(entry: Dict[str, Any]) -> str:
    if "short" in entry and isinstance(entry["short"], str):
        return entry["short"]
    if "label" in entry and isinstance(entry["label"], str):
        return entry["label"]
    if "id" in entry and isinstance(entry["id"], str):
        return entry["id"].replace("hub_", "").replace("_", " ").upper()
    return "BTN"


def draw_slot_overlay(
    draw: ImageDraw.ImageDraw,
    x: int,
    y: int,
    slot_size: int,
    entry: Dict[str, Any],
    label_font: ImageFont.ImageFont | ImageFont.FreeTypeFont,
    index_font: ImageFont.ImageFont | ImageFont.FreeTypeFont,
) -> None:
    role = str(entry.get("role", "unknown"))
    fill = pick_role_color(role)
    border = (255, 255, 255, 210)
    draw.rounded_rectangle(
        (x + 1, y + 1, x + slot_size - 1, y + slot_size - 1),
        radius=max(3, slot_size // 8),
        fill=fill,
        outline=border,
        width=max(1, slot_size // 14),
    )

    label = short_label(entry)
    max_chars = 12 if slot_size < 60 else 16
    if len(label) > max_chars:
        label = label[: max_chars - 1] + "…"

    text_bbox = draw.textbbox((0, 0), label, font=label_font)
    tw = text_bbox[2] - text_bbox[0]
    th = text_bbox[3] - text_bbox[1]
    tx = x + (slot_size - tw) // 2
    ty = y + (slot_size - th) // 2
    draw.text((tx, ty), label, font=label_font, fill=(255, 255, 255, 245))

    slot_num = str(entry.get("_slot", ""))
    draw.text((x + 3, y + 2), slot_num, font=index_font, fill=(10, 10, 10, 220))


def draw_header(
    draw: ImageDraw.ImageDraw,
    data: Dict[str, Any],
    canvas_size: Tuple[int, int],
    title_font: ImageFont.ImageFont | ImageFont.FreeTypeFont,
    subtitle_font: ImageFont.ImageFont | ImageFont.FreeTypeFont,
) -> None:
    title = (
        data.get("context", {}).get("title_default")
        or data.get("context", {}).get("title")
        or "City Hub"
    )
    title_clean = (
        str(title)
        .replace("<gold>", "")
        .replace("</gold>", "")
        .replace("<gray>", "")
        .replace("</gray>", "")
    )
    subtitle = f"{data.get('screen_id', 'city_hub')} - 9x6 preview"

    canvas_w, _ = canvas_size
    t_bbox = draw.textbbox((0, 0), title_clean, font=title_font)
    t_w = t_bbox[2] - t_bbox[0]
    draw.text(((canvas_w - t_w) // 2, 18), title_clean, font=title_font, fill=(240, 240, 240, 255))

    s_bbox = draw.textbbox((0, 0), subtitle, font=subtitle_font)
    s_w = s_bbox[2] - s_bbox[0]
    draw.text(((canvas_w - s_w) // 2, 52), subtitle, font=subtitle_font, fill=(180, 180, 180, 240))


def main() -> int:
    args = parse_args()
    layout_path = Path(args.layout).resolve()
    output_path = Path(args.output).resolve()
    background_path = Path(args.background).resolve() if args.background else None

    data = load_json(layout_path)
    grid_w = int(data.get("context", {}).get("columns", 9))
    grid_h = int(data.get("context", {}).get("rows", 6))

    canvas = create_base_canvas(background_path, args.scale, grid_w, grid_h, args.cell_size)
    draw = ImageDraw.Draw(canvas, "RGBA")

    title_font = load_font(max(16, 5 * args.scale))
    subtitle_font = load_font(max(12, 3 * args.scale))
    label_font = load_font(max(10, int(2.7 * args.scale)))
    index_font = load_font(max(8, int(2.1 * args.scale)))

    draw_header(draw, data, canvas.size, title_font, subtitle_font)

    slot_size = args.cell_size * args.scale
    grid_left, grid_top = compute_grid_origin(
        canvas.size, grid_w, grid_h, slot_size, args.offset_x, args.offset_y
    )

    # Draw subtle grid overlay.
    for row in range(grid_h):
        for col in range(grid_w):
            x0 = grid_left + col * slot_size
            y0 = grid_top + row * slot_size
            x1 = x0 + slot_size
            y1 = y0 + slot_size
            draw.rectangle((x0, y0, x1, y1), outline=(255, 255, 255, 45), width=1)

    interactive_slots = data.get("layout", {}).get("interactive_slots", {})
    for slot_str, entry in interactive_slots.items():
        try:
            slot = int(slot_str)
        except (ValueError, TypeError):
            continue
        if slot < 0 or slot >= grid_w * grid_h:
            continue
        col = slot % grid_w
        row = slot // grid_w
        x = grid_left + col * slot_size
        y = grid_top + row * slot_size
        entry = dict(entry)
        entry["_slot"] = slot
        draw_slot_overlay(draw, x, y, slot_size, entry, label_font, index_font)

    ensure_parent(output_path)
    canvas.save(output_path, format="PNG")
    print(f"Generated GUI preview: {output_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
