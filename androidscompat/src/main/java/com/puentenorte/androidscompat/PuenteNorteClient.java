package com.puentenorte.androidscompat;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AndroidsCompatMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD,
        value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class PuenteNorteClient {
    private PuenteNorteClient() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PuenteNorteEntities.SOLDIER.get(), context ->
                new RaiderRenderer<>(context, "puente_norte_soldier.png", 0.5F));
        event.registerEntityRenderer(PuenteNorteEntities.BOSS.get(), context ->
                new RaiderRenderer<>(context, "puente_norte_boss.png", 0.55F));
        event.registerEntityRenderer(PuenteNorteEntities.VILLAGE_DEFENDER.get(),
            VillageGuardRenderer::new);
        event.registerEntityRenderer(PuenteNorteEntities.VILLAGE_DEFENDER_CHIEF.get(),
            VillageGuardChiefRenderer::new);
    }

    private static final class RaiderRenderer<T extends PuenteNorteRaider>
            extends HumanoidMobRenderer<T, HumanoidModel<T>> {
        private final ResourceLocation defaultTexture;

        RaiderRenderer(EntityRendererProvider.Context context, String texture, float shadow) {
            super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), shadow);
            this.defaultTexture = new ResourceLocation(AndroidsCompatMod.MOD_ID, "textures/entity/" + texture);
        }

        @Override
        public ResourceLocation getTextureLocation(T entity) {
            if (entity instanceof PuenteNorteSoldier soldier) {
                String file = switch (soldier.getSkinVariant()) {
                    case 1 -> "puente_norte_soldier_b.png";
                    case 2 -> "puente_norte_soldier.png";
                    default -> "puente_norte_soldier_a.png";
                };
                ResourceLocation variantTexture = new ResourceLocation(AndroidsCompatMod.MOD_ID,
                        "textures/entity/" + file);
                // Keep entities renderable while optional/user-supplied skin files are absent.
                return net.minecraft.client.Minecraft.getInstance().getResourceManager()
                        .getResource(variantTexture).isPresent() ? variantTexture : defaultTexture;
            }
            if (entity instanceof PuenteNorteBoss boss) {
                String file = switch (boss.getBossKind()) {
                    case "superintendente" -> "puente_norte_superintendente.png";
                    case "filemon" -> "puente_norte_filemon.png";
                    case "bacterio" -> "puente_norte_bacterio.png";
                    default -> "puente_norte_boss.png";
                };
                return new ResourceLocation(AndroidsCompatMod.MOD_ID, "textures/entity/" + file);
            }
            return defaultTexture;
        }
    }

    private static class VillageGuardRenderer
            extends HumanoidMobRenderer<VillageDefender, HumanoidModel<VillageDefender>> {
        VillageGuardRenderer(EntityRendererProvider.Context context) {
            super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        }

        @Override
        public ResourceLocation getTextureLocation(VillageDefender entity) {
            if (entity instanceof VillageDefenderChief) {
                return new ResourceLocation(AndroidsCompatMod.MOD_ID,
                    "textures/entity/village_defenders/defender_chief.png");
            }
            if (entity.hasCommanderSkin()) {
                return new ResourceLocation(AndroidsCompatMod.MOD_ID,
                        "textures/entity/village_defenders/defender_commander.png");
            }
            String file = String.format(java.util.Locale.ROOT, "defender_%02d.png",
                    entity.getSkinVariant() + 1);
            ResourceLocation skin = new ResourceLocation(AndroidsCompatMod.MOD_ID,
                    "textures/entity/village_defenders/" + file);
            return net.minecraft.client.Minecraft.getInstance().getResourceManager()
                    .getResource(skin).isPresent() ? skin : new ResourceLocation(AndroidsCompatMod.MOD_ID,
                    "textures/entity/village_defenders/defender_01.png");
        }
    }

    private static final class VillageGuardChiefRenderer extends VillageGuardRenderer {
        VillageGuardChiefRenderer(EntityRendererProvider.Context context) { super(context); }
    }
}
