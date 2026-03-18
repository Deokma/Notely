# 📓 Notepad Mod — Minecraft 1.21.1

Multiplatform notepad mod built with **Architecture API**.  
Works on **Forge**, **Fabric**, **NeoForge**, and **Quilt**.

---

## ✨ Features

| Feature | Description |
|---|---|
| 📒 **Portable Notepad** | Hold in hand, right-click to open. Stores 3 pages of text. Saved in item NBT. |
| 🪵 **Notepad Stand** | Placeable block. Stores 5 pages. Shared between all players nearby. |
| 📄 **Multi-page UI** | Tab buttons to switch pages, auto-save on close. |
| 🌐 **Multiplatform** | One codebase via Architecture API — Forge / Fabric / NeoForge / Quilt. |
| 🌍 **Localization** | English (`en_us`) and Russian (`ru_ru`) built-in. |

---

## 🔨 Crafting

### Portable Notepad
```
P P P
P S P
P P P
```
`P` = Paper, `S` = Stick

### Notepad Stand (Block)
```
N N N
W S W
W S W
```
`N` = Notepad, `W` = Oak Planks, `S` = Stick

---

## 🏗️ Project Structure

```
notepad-mod/
├── common/          ← Shared code (blocks, items, GUI, network)
│   └── src/main/java/com/example/notepad/
│       ├── NotepadMod.java              ← Main init
│       ├── NotepadModClient.java        ← Client-side init
│       ├── block/
│       │   ├── NotepadBlock.java        ← Placeable block
│       │   ├── ModBlocks.java           ← Block registry
│       │   └── entity/
│       │       ├── NotepadBlockEntity.java  ← Stores 5 pages, opens menu
│       │       └── ModBlockEntities.java
│       ├── item/
│       │   ├── NotepadItem.java         ← Portable notepad
│       │   └── ModItems.java
│       ├── gui/
│       │   ├── NotepadMenu.java         ← Block notepad container
│       │   ├── NotepadItemMenu.java     ← Item notepad container
│       │   └── NotepadScreen.java       ← Client GUI (parchment style)
│       └── network/
│           ├── ModNetwork.java          ← Menu + packet registration
│           └── packets/
│               ├── SaveNotepadPacket.java      ← Save block notepad (C2S)
│               └── SaveNotepadItemPacket.java  ← Save item notepad (C2S)
│
├── forge/           ← Forge-specific entrypoint
├── fabric/          ← Fabric-specific entrypoint
├── neoforge/        ← NeoForge-specific entrypoint
└── quilt/           ← Quilt-specific entrypoint
```

---

## 🚀 Building

### Prerequisites
- Java 21+
- Gradle 8+

### Build all platforms
```bash
./gradlew build
```

### Build specific platform
```bash
./gradlew :forge:build
./gradlew :fabric:build
./gradlew :neoforge:build
./gradlew :quilt:build
```

Output JARs will be in `<platform>/build/libs/`.

### Run in dev environment
```bash
./gradlew :fabric:runClient
./gradlew :forge:runClient
./gradlew :neoforge:runClient
```

---

## 📦 Dependencies

| Dependency | Version |
|---|---|
| Minecraft | 1.21.1 |
| Architecture API | 13.0.8 |
| Fabric Loader | 0.16.9 |
| Fabric API | 0.110.0+1.21.1 |
| ~~Forge~~ | ~~не поддерживается Architectury для 1.21.1~~ |
| NeoForge | 21.1.86 |
| Quilt Loader | 0.27.1 |

---

## 🎨 Adding Custom Textures

Replace the placeholder textures at:
- `common/src/main/resources/assets/notepad/textures/item/notepad.png` (16×16)
- `common/src/main/resources/assets/notepad/textures/block/notepad_block.png` (16×16)

---

## 🔧 Architecture API Key Concepts Used

- `DeferredRegister` — platform-agnostic registry
- `MenuRegistry` — cross-platform GUI/menu registration
- `NetworkManager` — C2S packet sending/receiving
- `EnvExecutor` — client-only code isolation
- `CreativeTabRegistry` — creative tab creation

---

## 📝 License

MIT — free to use, modify, and distribute.

---

## 🔧 Устранение ошибок сборки

### Cache lock / "ACQUIRED_ALREADY_OWNED"
Если видите ошибку с блокировкой кэша:
```
"Lock for cache=...fabric-loom..." is currently held by pid '...'
```
Выполните:
```bash
# Windows
taskkill /F /IM java.exe
# Затем удалите папку кэша
rmdir /s /Q "%USERPROFILE%\.gradle\caches\fabric-loom"

# Linux/Mac
pkill -f gradle
rm -rf ~/.gradle/caches/fabric-loom
```
Затем запустите сборку снова.

### Версии совместимости
| Компонент | Версия |
|---|---|
| Gradle | **8.8** (обязательно, не выше) |
| Loom | **1.9-SNAPSHOT** |
| Architectury Plugin | **3.4-SNAPSHOT** |
| Java | **21** |
