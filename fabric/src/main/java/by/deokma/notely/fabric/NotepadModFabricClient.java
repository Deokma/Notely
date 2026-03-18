package by.deokma.notely.fabric;

import by.deokma.notely.NotepadModClient;
import by.deokma.notely.gui.PinnedNotesOverlay;
import by.deokma.notely.gui.NotepadScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class NotepadModFabricClient implements ClientModInitializer {

    private boolean mouseWasDown = false;

    @Override
    public void onInitializeClient() {
        NotepadModClient.init();
        KeyBindingHelper.registerKeyBinding(NotepadModClient.createKeyMapping());

        ClientTickEvents.END_CLIENT_TICK.register(mc -> NotepadModClient.onKeyTick());

        HudRenderCallback.EVENT.register((gfx, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.screen instanceof NotepadScreen)) {
                PinnedNotesOverlay.render(gfx,
                    mc.getWindow().getGuiScaledWidth(),
                    mc.getWindow().getGuiScaledHeight());
            }
        });

        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            if (mc.screen instanceof NotepadScreen) return;
            if (mc.getWindow() == null) return;

            long win = mc.getWindow().getWindow();
            boolean down = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

            if (down && !mouseWasDown) {
                NotepadModClient.onMousePress(mc.mouseHandler.xpos(), mc.mouseHandler.ypos());
            } else if (down && PinnedNotesOverlay.isDragging()) {
                NotepadModClient.onMouseHeld(mc.mouseHandler.xpos(), mc.mouseHandler.ypos());
            } else if (!down && mouseWasDown) {
                NotepadModClient.onMouseRelease();
            }

            mouseWasDown = down;
        });
    }
}
