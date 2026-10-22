package com.puentenorte.androidscompat;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client cache and render tint for Android skin colors. */
@Mod.EventBusSubscriber(modid = AndroidsCompatMod.MOD_ID,
        value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class SkinColorClient {
    private static final Map<UUID, Integer> COLORS = new ConcurrentHashMap<>();

    private SkinColorClient() {
    }

    static void apply(UUID entityId, int color) {
        COLORS.put(entityId, color & 0xFFFFFF);
    }

    private static Integer colorOf(LivingEntity entity) {
        if (!AndroidsCompatMod.isAndroidEntity(entity)) {
            return null;
        }
        return COLORS.getOrDefault(entity.getUUID(), 0xFFFFFF);
    }

    @SubscribeEvent
    public static void beforeRender(RenderLivingEvent.Pre<?, ?> event) {
        Integer color = colorOf(event.getEntity());
        if (color == null || color == 0xFFFFFF) {
            return;
        }
        RenderSystem.setShaderColor(
                ((color >> 16) & 0xFF) / 255.0F,
                ((color >> 8) & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F,
                1.0F);
    }

    @SubscribeEvent
    public static void afterRender(RenderLivingEvent.Post<?, ?> event) {
        Integer color = colorOf(event.getEntity());
        if (color != null && color != 0xFFFFFF) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
