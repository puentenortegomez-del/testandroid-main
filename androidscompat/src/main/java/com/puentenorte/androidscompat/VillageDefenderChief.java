package com.puentenorte.androidscompat;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** Veteran commander of the allied village guard. */
public final class VillageDefenderChief extends VillageDefender {
    private static final String RALLY_COOLDOWN_TAG = "androidscompat_village_chief_rally";

    public VillageDefenderChief(EntityType<? extends net.minecraft.world.entity.monster.Pillager> type,
                                Level level) {
        super(type, level);
        getPersistentData().putBoolean("androidscompat_village_chief", true);
        setCustomName(net.minecraft.network.chat.Component.literal("Jefe de la Guardia Aldeana"));
        setCustomNameVisible(true);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || tickCount % 20 != 0) return;
        long now = level().getGameTime();
        if (now < getPersistentData().getLong(RALLY_COOLDOWN_TAG)) return;
        var allies = level().getEntitiesOfClass(VillageDefender.class,
                getBoundingBox().inflate(12.0D), guard -> guard != this && guard.isAlive());
        if (allies.isEmpty()) return;
        for (LivingEntity ally : allies) {
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 0));
            ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 0));
        }
        getPersistentData().putLong(RALLY_COOLDOWN_TAG, now + 200L);
    }
}
