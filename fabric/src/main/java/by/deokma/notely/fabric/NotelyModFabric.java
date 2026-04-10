package by.deokma.notely.fabric;

import by.deokma.notely.NotelyMod;
import net.fabricmc.api.ModInitializer;

public class NotelyModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        NotelyMod.LOG.info("Notely Fabric init");
    }
}
