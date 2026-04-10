# Notely

**Notely** is a client-side notepad mod for Minecraft with Markdown rendering and pinnable sticky notes on your HUD.

---

## Features

- 📝 **In-game notepad** — open with `N` (rebindable in controls)
- ✨ **Markdown rendering** — headings, quotes, code blocks, dividers, checkboxes
- 📌 **HUD sticky notes** — pin any note as a floating sticker over the game
- 🎨 **6 sticker colors** to choose from
- 🔲 **Todo checkboxes** — clickable both in the editor and on stickers
- 🔄 **Undo** — `Ctrl+Z` to revert edits
- 🖱️ **Drag & resize** stickers with your mouse
- 👁️ **Transparency toggle** per sticker
- 🌍 **Localization** — EN, BY, UA, RU, DE, FR, ES, PT, PL, IT, NL, ZH, JA, KO and more
- 💾 **Auto-save** to `notepad/notes.json` in your game folder

---

## Markdown Syntax

| Syntax | Result |
|---|---|
| `# Text` | Heading H1 |
| `## Text` | Heading H2 |
| `### Text` | Heading H3 |
| `---` | Horizontal rule |
| `> Text` | Blockquote |
| `` `code` `` | Code block |
| `[ ] Text` | Open checkbox |
| `[x] Text` | Checked checkbox |

Press `?` inside the notepad to create a syntax reference note in-game.

---

## Controls

| Key | Action |
|---|---|
| `N` | Open / close notepad |
| `Escape` | Close notepad |
| `Ctrl+Z` | Undo last edit |
| `Ctrl+Backspace` | Delete word to the left |
| `Ctrl+Delete` | Delete word to the right |
| `Ctrl+←/→` | Jump by word |
| `Ctrl+Home/End` | Jump to start / end of note |
| RMB on a note | Context menu (open, rename, delete) |

---

## Installation

Drop the `.jar` into your `mods/` folder — that's it.

- **Fabric:** requires [Fabric API](https://modrinth.com/mod/fabric-api)
- **NeoForge:** no extra dependencies

---

## Compatibility

| | Version |
|---|---|
| Minecraft | 1.21.1 |
| Fabric Loader | ≥ 0.16.9 |
| NeoForge | 21.1.x |
| Side | Client only |

---

**Author:** Deokma · **License:** MPL-2.0
