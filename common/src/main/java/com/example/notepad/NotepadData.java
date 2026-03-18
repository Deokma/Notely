package com.example.notepad;

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

public class NotepadData {

    public static final int MAX_CONTENT_LENGTH = 4096;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final List<Note> notes = new ArrayList<>();
    public static final List<Sticker> stickers = new ArrayList<>();

    public static class Note {
        public String id;
        public String title;
        public String content;

        public Note() {
            id = UUID.randomUUID().toString().substring(0, 8);
            title = "Новая заметка";
            content = "";
        }
    }

    public static class Sticker {
        public String noteId;
        public float x, y;
        public float width, height;
        public int color;
        public boolean transparent = false; // полупрозрачный режим

        public Sticker(String noteId, float x, float y, int color) {
            this.noteId = noteId;
            this.x = x;
            this.y = y;
            this.width = 180;
            this.height = 120;
            this.color = color;
        }
    }

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

    public static void save() {
        try {
            Path dir = dataDir();
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("notes.json"), GSON.toJson(new SaveFile()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            NotepadMod.LOG.error("Failed to save notepad data", e);
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
            NotepadMod.LOG.error("Failed to load notepad data", e);
        }
    }

    private static Path dataDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("notepad");
    }

    private static class SaveFile {
        List<Note> notes = NotepadData.notes;
        List<Sticker> stickers = NotepadData.stickers;
    }
}
