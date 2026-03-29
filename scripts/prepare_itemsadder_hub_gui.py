#!/usr/bin/env python3
from __future__ import annotations

import argparse
import shutil
from pathlib import Path
from PIL import Image

PROJECT_DIR = Path("/Users/patric/Documents/Minecraft/CittaEXP")
SOURCE_PACK_ROOT = PROJECT_DIR / "itemsadder"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Copy ready-made city GUI assets into ItemsAdder runtime")
    parser.add_argument(
        "--runtime-itemsadder-dir",
        default="/Users/patric/Documents/Minecraft/SERVER-TEST/runtime/current/plugins/ItemsAdder",
        help="ItemsAdder plugin data folder",
    )
    parser.add_argument(
        "--namespace",
        default="cittaexp_gui",
        help="ItemsAdder namespace",
    )
    return parser.parse_args()


def ensure_font_image_entry(guis_path: Path, namespace: str) -> None:
    entries = {
        "hub_gui_bg": ("gui/hub_gui_256", 12), # OK
        "quest_main_bg": ("gui/quest_main_256", 12),  # OK
        "story_mode_bg": ("gui/story_mode_256", 18),  # OK
        "territori_claim_bg": ("gui/territori_claim_9x6", 18),  # DA VERIFICARE OFFSET
        "diplomazia_9x6": ("gui/diplomazia_9x6", 12),
        "livelli_9x6": ("gui/livelli_9x6", 12),
        "city_list_9x6": ("gui/city_list_9x6", 12),
        "claim_rules_bg": ("gui/claim_rules_9x4", 12),  # # DA VERIFICARE OFFSET
        "defense_wars_bg": ("gui/defense_wars_9x6", 12),  # # DA VERIFICARE OFFSET
        "mob_invasion_bg": ("gui/mob_invasion_9x6", 12),  # # DA VERIFICARE OFFSET
        "mob_invasion_active_bg": ("gui/mob_invasion_active_9x4", 12),  # # DA VERIFICARE OFFSET
        "deposito_banca_vault_9x5": ("gui/deposito_banca_vault_9x5", 12),# # DA VERIFICARE OFFSET
        "vault_items_9x6": ("gui/vault_items_9x6", 12),  # DA VERIFICARE OFFSET
        "war_list_9x4": ("gui/war_list_9x4", 12),  # OK
        "war_active_overview_9x5": ("gui/war_active_overview_9x5", 12),# OK
        "settimanali_main_bg": ("gui/settimanali_9x3", 18), # # DA VERIFICARE OFFSET
        "mensili_main_bg": ("gui/mensili_9x3", 18), # # DA VERIFICARE OFFSET
        "stagionali_main_bg": ("gui/stagionali_9x6", 18), # # DA VERIFICARE OFFSET
        "eventi_main_bg": ("gui/eventi_9x6", 18), # # DA VERIFICARE OFFSET
    }
    guis_path.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        "info:",
        f"  namespace: {namespace}",
        "",
        "font_images:",
    ]
    for key, (path, y_position) in entries.items():
        lines.extend([
            f"  {key}:",
            f"    path: {path}",
            "    suggest_in_command: false",
            f"    y_position: {y_position}",
        ])
    guis_path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def ensure_transparent_item_entry(items_path: Path, namespace: str) -> None:
    if not items_path.exists():
        items_path.parent.mkdir(parents=True, exist_ok=True)
        items_path.write_text(f"info:\n  namespace: {namespace}\n\nitems:\n", encoding="utf-8")

    content = items_path.read_text(encoding="utf-8")
    if "transparent_button:" in content:
        return

    append = (
        "\n"
        "  transparent_button:\n"
        "    display_name: \"&f\"\n"
        "    resource:\n"
        "      material: PAPER\n"
        "      generate: true\n"
        "      textures:\n"
        "        - items/transparent_button.png\n"
    )

    if "items:" not in content:
        content = content.rstrip() + "\n\nitems:\n"
    content = content.rstrip() + append
    items_path.write_text(content + "\n", encoding="utf-8")


def write_transparent_item_texture(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.new("RGBA", (16, 16), (0, 0, 0, 0)).save(path, format="PNG")


def sync_source_pack(runtime_dir: Path, namespace: str) -> list[Path]:
    source_root = SOURCE_PACK_ROOT / namespace
    if not source_root.exists():
        return []

    target_root = runtime_dir / "contents" / namespace
    copied: list[Path] = []
    for source in sorted(source_root.rglob("*")):
        if not source.is_file():
            continue
        relative = source.relative_to(source_root)
        target = target_root / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        copied.append(target)
    return copied


def main() -> int:
    args = parse_args()
    runtime_dir = Path(args.runtime_itemsadder_dir)
    namespace = args.namespace
    if not runtime_dir.exists():
        raise FileNotFoundError(f"ItemsAdder runtime folder missing: {runtime_dir}")

    assets_root = runtime_dir / "contents" / namespace / "resourcepack" / "assets" / namespace / "textures"
    gui_assets_root = assets_root / "gui"
    item_assets_root = assets_root / "items"
    gui_assets_root.mkdir(parents=True, exist_ok=True)
    item_assets_root.mkdir(parents=True, exist_ok=True)

    copied = sync_source_pack(runtime_dir, namespace)
    asset_map = {
        Path("/Users/patric/Documents/Minecraft/CittaEXP/assets/HUB-GUI.png"): gui_assets_root / "hub_gui_256.png",
        Path("/Users/patric/Documents/Minecraft/CittaEXP/assets/QUEST-MAIN.png"): gui_assets_root / "quest_main_256.png",
        Path("/Users/patric/Documents/Minecraft/CittaEXP/assets/STORY-MODE.png"): gui_assets_root / "story_mode_256.png",
        Path("/Users/patric/Documents/Minecraft/CittaEXP/assets/TERRITORI-CLAIM9x6.png"): gui_assets_root / "territori_claim_9x6.png",
        Path("/Users/patric/Documents/Minecraft/CittaEXP/assets/DIPLOMAZIA-9x6.png"): gui_assets_root / "diplomazia_9x6.png",
        Path("/Users/patric/Documents/Minecraft/CittaEXP/assets/LIVELLI-9x6.png"): gui_assets_root / "livelli_9x6.png",
        Path("/Users/patric/Documents/Minecraft/CittaEXP/assets/CITY-LIST-9x6.png"): gui_assets_root / "city_list_9x6.png",
        Path("/Users/patric/Documents/Minecraft/CittaEXP/assets/CLAIM-RULES-9x4.png"): gui_assets_root / "claim_rules_9x4.png",
        Path("/Users/patric/Documents/Minecraft/CittaEXP/assets/DIFESA-GUERRE.png"): gui_assets_root / "defense_wars_9x6.png",
        Path("/Users/patric/Documents/Minecraft/CittaEXP/assets/MOB-INVASION-9x6.png"): gui_assets_root / "mob_invasion_9x6.png",
        Path("/Users/patric/Documents/Minecraft/CittaEXP/assets/MOB-INVASION-ACTIVE-9x4.png"): gui_assets_root / "mob_invasion_active_9x4.png",
        Path("/Users/patric/Documents/Minecraft/CittaEXP/assets/DEPOSITO-BANCA-VAULT-9x5.png"): gui_assets_root / "deposito_banca_vault_9x5.png",
        Path("/Users/patric/Documents/Minecraft/CittaEXP/assets/VAULT-ITEMS-9x6.png"): gui_assets_root / "vault_items_9x6.png",
        Path("/Users/patric/Documents/Minecraft/CittaEXP/assets/WAR-LIST-9x4.png"): gui_assets_root / "war_list_9x4.png",
        Path("/Users/patric/Documents/Minecraft/CittaEXP/assets/WAR-ACTIVE-OVERVIEW-9x5.png"): gui_assets_root / "war_active_overview_9x5.png",
        Path("/Users/patric/Documents/Minecraft/CittaEXP/assets/SETTIMANALI-9x3.png"): gui_assets_root / "settimanali_9x3.png",
        Path("/Users/patric/Documents/Minecraft/CittaEXP/assets/MENSILI-9x3.png"): gui_assets_root / "mensili_9x3.png",
        Path("/Users/patric/Documents/Minecraft/CittaEXP/assets/STAGIONALI-9x6.png"): gui_assets_root / "stagionali_9x6.png",
        Path("/Users/patric/Documents/Minecraft/CittaEXP/assets/EVENTI-9x6.png"): gui_assets_root / "eventi_9x6.png",
    }
    for source, target in asset_map.items():
        if not source.exists():
            continue
        shutil.copy2(source, target)
        copied.append(target)

    guis_yml = runtime_dir / "contents" / namespace / "configs" / "guis.yml"
    ensure_font_image_entry(guis_yml, namespace)

    items_yml = runtime_dir / "contents" / namespace / "configs" / "items.yml"
    ensure_transparent_item_entry(items_yml, namespace)

    transparent_path = item_assets_root / "transparent_button.png"
    write_transparent_item_texture(transparent_path)

    for target in copied:
        print(f"[ok] copied gui asset to: {target}")
    print(f"[ok] ensured font image entry in: {guis_yml}")
    print(f"[ok] ensured transparent item in: {items_yml}")
    print(f"[ok] wrote transparent item texture: {transparent_path}")
    print("[next] run in server console: /iazip then /iareload")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
