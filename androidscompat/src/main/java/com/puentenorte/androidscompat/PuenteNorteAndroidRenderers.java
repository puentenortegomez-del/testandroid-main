package com.puentenorte.androidscompat;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Separate dark faction colorways for faction-only Android entity types. */
@Mod.EventBusSubscriber(modid = AndroidsCompatMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PuenteNorteAndroidRenderers {
    private static final ResourceLocation BASIC_TEXTURE = new ResourceLocation(
            AndroidsCompatMod.MOD_ID, "textures/entity/puente_norte_basic_android.png");
    private static final ResourceLocation ROGUE_TEXTURE = new ResourceLocation(
            AndroidsCompatMod.MOD_ID, "textures/entity/puente_norte_rogue_android.png");

    private PuenteNorteAndroidRenderers() {}

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PuenteNorteEntities.BASIC_ANDROID.get(), BasicRenderer::new);
        event.registerEntityRenderer(PuenteNorteEntities.ROGUE_ANDROID.get(), RogueRenderer::new);
    }

    private static final class BasicRenderer extends HumanoidMobRenderer<PuenteNorteBasicAndroid,
            PlayerModel<PuenteNorteBasicAndroid>> {
        private BasicRenderer(EntityRendererProvider.Context context) {
            super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        }
        @Override public ResourceLocation getTextureLocation(PuenteNorteBasicAndroid entity) {
            return BASIC_TEXTURE;
        }
    }

    private static final class RogueRenderer extends HumanoidMobRenderer<PuenteNorteRogueAndroid,
            PlayerModel<PuenteNorteRogueAndroid>> {
        private RogueRenderer(EntityRendererProvider.Context context) {
            super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        }
        @Override public ResourceLocation getTextureLocation(PuenteNorteRogueAndroid entity) {
            return ROGUE_TEXTURE;
        }
    }
}
