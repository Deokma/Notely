package com.example.notepad.neoforge;

import com.example.notepad.NotepadMod;
import com.example.notepad.NotepadModClient;
import com.example.notepad.gui.NotepadScreen;
import com.example.notepad.gui.PinnedNotesOverlay;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

@Mod(NotepadMod.MOD_ID)
public class NotepadModNeoForge {

    private boolean mouseWasDown = false;

    public NotepadModNeoForge(IEventBus modBus) {
        modBus.addListener(this::onClientSetup);
        modBus.addListener(this::onRegisterKeys);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        NotepadModClient.init();
        NeoForge.EVENT_BUS.addListener(this::onRenderHud);
        NeoForge.EVENT_BUS.addListener(this::onPlayerTick);
    }

    private void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(NotepadModClient.createKeyMapping());
    }

    private void onRenderHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof NotepadScreen) return;
        PinnedNotesOverlay.render(event.getGuiGraphics(),
            mc.getWindow().getGuiScaledWidth(),
            mc.getWindow().getGuiScaledHeight());
    }

    private void onPlayerTick(PlayerTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen instanceof NotepadScreen) return;

        NotepadModClient.onKeyTick();

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
    }
}
