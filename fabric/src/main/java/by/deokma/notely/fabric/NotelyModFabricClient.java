package by.deokma.notely.fabric;

import by.deokma.notely.NotelyModClient;
import by.deokma.notely.gui.PinnedNotesOverlay;
import by.deokma.notely.gui.NotelyScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.lwjgl.glfw.GLFW;

public class NotelyModFabricClient implements ClientModInitializer {

    private boolean mouseWasDown = false;

    @Override
    public void onInitializeClient() {
        NotelyModClient.init();
        KeyBindingHelper.registerKeyBinding(NotelyModClient.createKeyMapping());
        NotelyModClient.setScreenFactory(NotelyScreenFabric::new);

        // World/server context switching
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
            ServerData server = mc.getCurrentServer();
            if (server != null) {
                NotelyModClient.onJoinServer(server.ip);
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
                NotelyModClient.onJoinWorld(folderName);
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) ->
            NotelyModClient.onLeave()
        );

        ClientTickEvents.END_CLIENT_TICK.register(mc -> NotelyModClient.onKeyTick());

        // Init GLFW scroll callback once window is ready (first tick)
        ClientTickEvents.START_CLIENT_TICK.register(new ClientTickEvents.StartTick() {
            private boolean initialized = false;
            @Override
            public void onStartTick(Minecraft mc) {
                if (!initialized && mc.getWindow() != null) {
                    NotelyModClient.initWindow();
                    initialized = true;
                }
            }
        });

        HudRenderCallback.EVENT.register((gfx, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (!NotelyModClient.isStickersAllowedOnScreen(mc)) return;
            if (!(mc.screen instanceof NotelyScreen)) {
                PinnedNotesOverlay.render(gfx,
                    mc.getWindow().getGuiScaledWidth(),
                    mc.getWindow().getGuiScaledHeight());
            }
        });

        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            if (!NotelyModClient.isStickersAllowedOnScreen(mc)) return;
            if (mc.getWindow() == null) return;

            long win = NotelyModClient.getGlfwWindow(mc);
            boolean down = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

            if (down && !mouseWasDown) {
                NotelyModClient.onMousePress(mc.mouseHandler.xpos(), mc.mouseHandler.ypos());
            } else if (down && PinnedNotesOverlay.isDragging()) {
                NotelyModClient.onMouseHeld(mc.mouseHandler.xpos(), mc.mouseHandler.ypos());
            } else if (!down && mouseWasDown) {
                NotelyModClient.onMouseRelease();
            }

            mouseWasDown = down;
        });
    }
}
