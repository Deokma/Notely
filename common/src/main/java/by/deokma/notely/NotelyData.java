package by.deokma.notely;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NotelyData {

    public static final int MAX_CONTENT_LENGTH = 4096;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final List<Note> notes = new ArrayList<>();
    public static final List<Sticker> stickers = new ArrayList<>();

    /** Current context key: world folder name or server address. Null = not in a world. */
    private static String currentContext = null;
    /** True when player is in a world/server (between loadForContext and clearContext). */
    private static boolean inWorld = false;

    // ---- Data classes ----

    public static class Note {
        public String id;
        public String title;
        public String content;

        public Note() {
            id = UUID.randomUUID().toString().substring(0, 8);
            title = "New Note";
            content = "";
        }
    }

    public static class Sticker {
        public String noteId;
        public float x, y;
        public float width, height;
        public int color;
        public boolean transparent = false;
        public int scrollOffset = 0;
        public float fontSize = 1.0f; // scale factor: 0.5 – 2.0

        public Sticker(String noteId, float x, float y, int color) {
            this.noteId = noteId;
            this.x = x;
            this.y = y;
            this.width = 180;
            this.height = 120;
            this.color = color;
        }
    }

    private static class SaveFile {
        List<Note> notes = NotelyData.notes;
        List<Sticker> stickers = NotelyData.stickers;
    }

    // ---- CRUD ----

    public static Note createNote() {
        Note note = new Note();
        notes.add(note);
        save();
        return note;
    }

    public static void deleteNote(String id) {
        notes.removeIf(n -> n.id.equals(id));
        stickers.removeIf(s -> s.noteId.equals(id));
        save();
    }

    public static Note findNote(String id) {
        return notes.stream().filter(n -> n.id.equals(id)).findFirst().orElse(null);
    }

    public static void pinNote(String noteId, float x, float y, int color) {
        stickers.removeIf(s -> s.noteId.equals(noteId));
        stickers.add(new Sticker(noteId, x, y, color));
        save();
    }

    public static void removeSticker(Sticker sticker) {
        stickers.remove(sticker);
        save();
    }

    // ---- Persistence ----

    public static void save() {
        try {
            Path dir = dataDir();
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("notes.json"), GSON.toJson(new SaveFile()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            NotelyMod.LOG.error("Failed to save notepad data", e);
        }
    }

    public static void load() {
        try {
            Path file = dataDir().resolve("notes.json");
            if (!Files.exists(file)) return;
            SaveFile data = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), SaveFile.class);
            if (data.notes != null) { notes.clear(); notes.addAll(data.notes); }
            if (data.stickers != null) { stickers.clear(); stickers.addAll(data.stickers); }
        } catch (Exception e) {
            NotelyMod.LOG.error("Failed to load notepad data", e);
        }
    }

    // ---- Context (per-world/server notes) ----

    public static boolean isInWorld() {
        return inWorld;
    }

    /**
     * Switch context to a specific world or server, saving current data first.
     * @param contextKey sanitized folder name: world name or "server_&lt;address&gt;"
     */
    public static void loadForContext(String contextKey) {
        if (inWorld) save(); // save previous context if any
        currentContext = sanitize(contextKey);
        inWorld = true;
        notes.clear();
        stickers.clear();
        load();
        NotelyMod.LOG.info("Notely: loaded context '{}'", currentContext);
    }

    /** Called on disconnect — saves current context and clears data. */
    public static void clearContext() {
        if (inWorld) save();
        currentContext = null;
        inWorld = false;
        notes.clear();
        stickers.clear();
        NotelyMod.LOG.info("Notely: cleared context (left world)");
    }

    private static Path dataDir() {
        Path base = Minecraft.getInstance().gameDirectory.toPath().resolve("notepad");
        if (currentContext != null) return base.resolve(currentContext);
        // Fallback: should not happen during normal play, but keep for safety
        return base.resolve("_global");
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }
}
