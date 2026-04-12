package by.deokma.notely.neoforge;

import by.deokma.notely.gui.NotelyScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * NeoForge 1.21.11 wrapper that overrides the new event-based input methods.
 */
public class NotelyScreenNeoForge extends NotelyScreen {

    @Override
    public boolean keyPressed(KeyEvent event) {
        return handleKeyPressed(event.key(), event.scancode(), event.modifiers());
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        // CharacterEvent.codepoint() returns an int codepoint; cast to char for BMP characters
        int cp = event.codepoint();
        if (cp > 0 && cp <= 0xFFFF) {
            return charTyped((char) cp, event.modifiers());
        }
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isInside) {
        boolean handled = handleMouseClicked(event.x(), event.y(), event.button());
        if (!handled) {
            return super.mouseClicked(event, isInside);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return handleMouseReleased();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return handleMouseDragged(event.x(), event.y(), dx, dy);
    }
}
