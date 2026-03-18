package com.example.notepad.fabric;

import com.example.notepad.NotepadMod;
import net.fabricmc.api.ModInitializer;

public class NotepadModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        NotepadMod.LOGGER.info("Notepad Fabric init");
    }
}
