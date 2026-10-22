package com.puentenorte.androidscompat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.ITooltip;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/** Displays the complete AndroidsCompat state in Jade's entity tooltip. */
@WailaPlugin
public final class JadePlugin implements IWailaPlugin {
    private static final ResourceLocation UID =
            new ResourceLocation(AndroidsCompatMod.MOD_ID, "android_stats");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerEntityDataProvider(new StatsDataProvider(), Entity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(new StatsTooltipProvider(), Entity.class);
    }

    private static final class StatsDataProvider
            implements snownee.jade.api.IServerDataProvider<EntityAccessor> {
        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public void appendServerData(CompoundTag data, EntityAccessor accessor) {
            AndroidsCompatMod.appendJadeStats(data, accessor.getEntity());
        }
    }

    private static final class StatsTooltipProvider implements IEntityComponentProvider {
        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor,
                                  IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.getBoolean("android")) {
                return;
            }
            tooltip.add(Component.literal("AndroidsCompat"));
            tooltip.add(Component.literal("Nivel " + data.getInt("level")
                    + " | XP " + data.getLong("xp")));
            tooltip.add(Component.literal("Vida " + format(data.getFloat("health"))
                    + "/" + format(data.getFloat("max_health"))
                    + " | armadura " + data.getInt("armor") + "/4"));
            tooltip.add(Component.literal("Precision "
                    + percent(data.getDouble("accuracy"))
                    + " | velocidad " + format(data.getDouble("speed"))));

            String weapon = data.getString("weapon");
            if (!weapon.isEmpty()) {
                tooltip.add(Component.literal("Arma: " + weapon));
            }
            String squad = data.getString("squad");
            if (!squad.isEmpty()) {
                tooltip.add(Component.literal("Escuadra: " + squad));
            }
            String order = data.getString("order");
            if (!order.isEmpty()) {
                tooltip.add(Component.literal("Orden: " + order));
            }
            tooltip.add(Component.literal("Comandante IA: "
                    + (data.getBoolean("commander") ? "activo" : "manual")));
            if (data.getBoolean("command_android")) {
                tooltip.add(Component.literal("Command Android: comandante fisico | max. 25 corazones"));
            }
            tooltip.add(Component.literal("Combate: "
                    + (data.getBoolean("combat_enabled") ? "autorizado" : "bloqueado")));
            String airRole = data.getString("air_role");
            if (!airRole.isEmpty()) {
                tooltip.add(Component.literal("Rol aereo: " + airRole));
            }
            if (data.getBoolean("spy")) {
                String cover = data.getString("cover");
                tooltip.add(Component.literal("ESPIONAJE: encubierto"
                        + (cover.isEmpty() ? "" : " | " + cover)));
            }
            if (data.getBoolean("captured")) {
                tooltip.add(Component.literal(data.getBoolean("imprisoned")
                        ? "Estado: encarcelado" : "Estado: retenido"));
            }
        }

        private static String format(double value) {
            return String.format(java.util.Locale.ROOT, "%.2f", value);
        }

        private static String percent(double value) {
            return String.format(java.util.Locale.ROOT, "%.1f%%", value * 100.0D);
        }
    }
}
