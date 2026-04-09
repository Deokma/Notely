# Notely — Notepad Mod

A client-side Minecraft mod that adds an in-game notepad with Markdown rendering and pinnable sticky notes.

Supports **Fabric** and **NeoForge** on Minecraft **1.21.1**.

---

## Features

- **In-game notepad** — open with `N` (rebindable), write notes without leaving the game
- **Markdown rendering** — headings, quotes, code, horizontal rules, todo checkboxes
- **Pinnable sticky notes** — pin any note as a floating overlay on your HUD, pick a color
- **Drag & resize** stickers directly on screen
- **Transparency toggle** on each sticker
- **Todo checkboxes** — click `[ ]` to check/uncheck both in the editor and on stickers
- **Undo** — `Ctrl+Z` to revert edits
- **Context menu** — right-click a note in the list to open, rename, or delete
- **Persistent storage** — notes saved to `<gamedir>/notepad/notes.json`

---

## Markdown Syntax

| Syntax | Result |
|---|---|
| `# Heading` | H1 — large heading with underline |
| `## Heading` | H2 |
| `### Heading` | H3 |
| `---` | Horizontal rule |
| `> text` | Blockquote |
| `` `code` `` | Inline code block |
| `[ ] text` | Open todo checkbox |
| `[x] text` | Checked todo checkbox |

Press `?` in the notepad to create a built-in syntax reference note.

---

## Controls

| Key | Action |
|---|---|
| `N` | Open / close notepad (rebindable) |
| `Escape` | Close notepad |
| `Ctrl+Z` | Undo last edit |
| `Ctrl+Backspace` | Delete word to the left |
| `Ctrl+Delete` | Delete word to the right |
| `Ctrl+Left / Right` | Jump by word |
| `Ctrl+Home / End` | Jump to start / end of note |
| `Home / End` | Jump to start / end of line |
| `↑ / ↓` | Move cursor up / down |

---

## Installation

### Fabric
1. Install [Fabric Loader](https://fabricmc.net/use/) `>=0.16.9`
2. Install [Fabric API](https://modrinth.com/mod/fabric-api) `0.110.0+1.21.1`
3. Drop the mod jar into your `mods/` folder

### NeoForge
1. Install [NeoForge](https://neoforged.net/) `21.1.86`
2. Drop the mod jar into your `mods/` folder

---

## Building from Source

Requirements: **JDK 21**, internet connection (Gradle downloads dependencies automatically).

```bash
# Clone
git clone https://github.com/yourname/notely.git
cd notely

# Build Fabric jar
./gradlew :fabric:build

# Build NeoForge jar
./gradlew :neoforge:build
```

Output jars are in `fabric/build/libs/` and `neoforge/build/libs/`.

> **Note:** If you get `Error: could not open clientRunVmArgs.txt` when running from IntelliJ,
> run the Gradle task `:neoforge:createRunFiles` once to generate the dev run configuration.

---

## Project Structure

```
common/          Shared code (GUI, data, utilities)
  gui/
    NotepadScreen.java        Main notepad screen
    PinnedNotesOverlay.java   HUD sticky notes overlay
  util/
    MarkdownRenderer.java     MD parsing + rendering (shared)
    TextCursor.java           Cursor navigation helpers
  NotepadData.java            Data model + JSON persistence
  NotepadModClient.java       Client init, keybind, mouse hooks

fabric/          Fabric platform entrypoints
neoforge/        NeoForge platform entrypoints
```

---

## License

MIT
