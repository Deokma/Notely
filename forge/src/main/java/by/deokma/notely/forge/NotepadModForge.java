package by.deokma.notely.forge;

import by.deokma.notely.NotelyMod;
import by.deokma.notely.NotelyModClient;
import by.deokma.notely.gui.NotelyScreen;
import by.deokma.notely.gui.PinnedNotesOverlay;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod(NotelyMod.MOD_ID)
public class NotepadModForge {

    private boolean mouseWasDown = false;
    private boolean windowInitialized = false;

    public NotepadModForge(IEventBus modBus) {
        modBus.addListener(this::onClientSetup);
        modBus.addListener(this::onRegisterKeys);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        NotelyModClient.init();
        MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(this::onRenderTick);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
    }

    private void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(NotelyModClient.createKeyMapping());
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();

        if (!windowInitialized && mc.getWindow() != null) {
            NotelyModClient.initWindow();
            windowInitialized = true;
        }

        NotelyModClient.onKeyTick();

        if (mc.screen instanceof NotelyScreen) return;
        if (mc.getWindow() == null) return;

        long win = mc.getWindow().getWindow();
        boolean down = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (down && !mouseWasDown) {
            NotelyModClient.onMousePress(mc.mouseHandler.xpos(), mc.mouseHandler.ypos());
        } else if (down && PinnedNotesOverlay.isDragging()) {
            NotelyModClient.onMouseHeld(mc.mouseHandler.xpos(), mc.mouseHandler.ypos());
        } else if (!down && mouseWasDown) {
            NotelyModClient.onMouseRelease();
        }

        mouseWasDown = down;
    }

    private void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof NotelyScreen) return;
        if (mc.getWindow() == null) return;
        if (!RenderSystem.isOnRenderThread()) return;

        GuiGraphics gfx = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        PinnedNotesOverlay.render(gfx,
                mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight());
        gfx.flush();
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!event.getEntity().level().isClientSide()) return;
        Minecraft mc = Minecraft.getInstance();
        ServerData server = mc.getCurrentServer();
        if (server != null) {
            NotelyModClient.onJoinServer(server.ip);
        } else if (mc.getSingleplayerServer() != null) {
            try {
                var field = mc.getSingleplayerServer().getClass().getSuperclass().getDeclaredField("storageSource");
                field.setAccessible(true);
                var storage = field.get(mc.getSingleplayerServer());
                NotelyModClient.onJoinWorld((String) storage.getClass().getMethod("getLevelId").invoke(storage));
            } catch (Exception e) {
                NotelyModClient.onJoinWorld(mc.getSingleplayerServer().getWorldData().getLevelName());
            }
        }
    }

    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.getEntity().level().isClientSide()) return;
        NotelyModClient.onLeave();
    }
}
