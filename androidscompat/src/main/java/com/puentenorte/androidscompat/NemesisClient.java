package com.puentenorte.androidscompat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Configurable hotkey and live roster UI for bosses, ranks and Nemesis traits. */
public final class NemesisClient {
    public static final KeyMapping KEY = new KeyMapping("key.androidscompat.nemesis",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N, "key.categories.androidscompat");
    private static NemesisScreen openScreen;
    private static int refreshTicks;

    private NemesisClient() {}

    @Mod.EventBusSubscriber(modid = AndroidsCompatMod.MOD_ID, value = net.minecraftforge.api.distmarker.Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}
        @SubscribeEvent public static void registerKey(RegisterKeyMappingsEvent event) { event.register(KEY); }
    }

    @Mod.EventBusSubscriber(modid = AndroidsCompatMod.MOD_ID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class Events {
        private Events() {}
        @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft minecraft = Minecraft.getInstance();
            if (KEY.consumeClick() && minecraft.player != null && minecraft.screen == null) {
                openScreen = new NemesisScreen();
                refreshTicks = 0;
                minecraft.setScreen(openScreen);
                NemesisNetwork.request();
            }
            if (openScreen != null && minecraft.player != null && ++refreshTicks >= 100) {
                refreshTicks = 0;
                NemesisNetwork.request();
            }
        }
    }

    static void update(String header, List<String> lines) {
        if (openScreen != null) openScreen.update(header, lines);
    }

    private static final class NemesisScreen extends Screen {
        private String header = "Conectando con el registro de Puente Norte...";
        private List<String> lines = List.of();
        private int scroll;

        private NemesisScreen() { super(Component.translatable("screen.androidscompat.nemesis")); }

        @Override protected void init() {
            int y = height - 34;
            addRenderableWidget(Button.builder(Component.literal("Actualizar"), button -> NemesisNetwork.request())
                    .bounds(width / 2 - 105, y, 100, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Cerrar"), button -> onClose())
                    .bounds(width / 2 + 5, y, 100, 20).build());
        }

        private void update(String newHeader, List<String> newLines) {
            header = newHeader;
            lines = List.copyOf(newLines);
            scroll = 0;
        }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics);
            int center = width / 2;
            graphics.drawCenteredString(font, title, center, 16, 0xFFFFFF);
            graphics.drawCenteredString(font, header, center, 36, 0xE0C060);
            graphics.drawCenteredString(font, "Rangos: recluta → veterano → stalker → mini jefe (R5)", center, 52, 0xBBBBBB);
            graphics.drawCenteredString(font, "Los Nemesis recuerdan y resisten fuego, proyectiles y ataques cuerpo a cuerpo.", center, 66, 0x999999);
            int visible = Math.max(1, (height - 120) / 13);
            for (int i = 0; i < visible && scroll + i < lines.size(); i++) {
                String line = lines.get(scroll + i);
                graphics.drawString(font, font.plainSubstrByWidth(line, width - 36), 18, 88 + i * 13, 0xFFFFFF);
            }
            if (lines.size() > visible) {
                graphics.drawCenteredString(font, (scroll + 1) + "-" + Math.min(lines.size(), scroll + visible)
                        + " / " + lines.size() + " (rueda del ratón)", center, height - 49, 0x888888);
            }
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            int visible = Math.max(1, (height - 120) / 13);
            scroll = Math.max(0, Math.min(Math.max(0, lines.size() - visible),
                    scroll - (int) Math.signum(delta) * 3));
            return true;
        }

        @Override public void onClose() {
            openScreen = null;
            refreshTicks = 0;
            super.onClose();
        }

        @Override public boolean isPauseScreen() { return false; }
    }
}
