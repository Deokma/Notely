package by.deokma.notely.fabric;

import by.deokma.notely.NotepadMod;
import net.fabricmc.api.ModInitializer;

public class NotepadModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        NotepadMod.LOG.info("Notepad Fabric init");
    }
}
