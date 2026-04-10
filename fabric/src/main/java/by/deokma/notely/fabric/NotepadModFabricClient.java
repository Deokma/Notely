package by.deokma.notely.fabric;

import by.deokma.notely.NotepadModClient;
import by.deokma.notely.gui.PinnedNotesOverlay;
import by.deokma.notely.gui.NotepadScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.lwjgl.glfw.GLFW;

public class NotepadModFabricClient implements ClientModInitializer {

    private boolean mouseWasDown = false;

    @Override
    public void onInitializeClient() {
        NotepadModClient.init();
        KeyBindingHelper.registerKeyBinding(NotepadModClient.createKeyMapping());

        // World/server context switching
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
            ServerData server = mc.getCurrentServer();
            if (server != null) {
                NotepadModClient.onJoinServer(server.ip);
            } else if (mc.getSingleplayerServer() != null) {
                String folderName;
                try {
                    var field = mc.getSingleplayerServer().getClass().getSuperclass().getDeclaredField("storageSource");
                    field.setAccessible(true);
                    var storage = field.get(mc.getSingleplayerServer());
                    folderName = (String) storage.getClass().getMethod("getLevelId").invoke(storage);
                } catch (Exception e) {
                    folderName = mc.getSingleplayerServer().getWorldData().getLevelName();
                }
                NotepadModClient.onJoinWorld(folderName);
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) ->
            NotepadModClient.onLeave()
        );

        ClientTickEvents.END_CLIENT_TICK.register(mc -> NotepadModClient.onKeyTick());

        // Init GLFW scroll callback once window is ready (first tick)
        ClientTickEvents.START_CLIENT_TICK.register(new ClientTickEvents.StartTick() {
            private boolean initialized = false;
            @Override
            public void onStartTick(Minecraft mc) {
                if (!initialized && mc.getWindow() != null) {
                    NotepadModClient.initWindow();
                    initialized = true;
                }
            }
        });

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
