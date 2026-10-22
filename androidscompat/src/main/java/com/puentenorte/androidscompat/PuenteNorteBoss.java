package com.puentenorte.androidscompat;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;

public final class PuenteNorteBoss extends PuenteNorteRaider {
    public static final String BOSS_KIND_TAG = "androidscompat_pn_boss_kind";
    private static final String BOSS_PHASE_TAG = "androidscompat_pn_boss_phase";
    private static final EntityDataAccessor<String> SYNCED_BOSS_KIND =
            SynchedEntityData.defineId(PuenteNorteBoss.class, EntityDataSerializers.STRING);

    public PuenteNorteBoss(EntityType<? extends PuenteNorteBoss> type, Level level) {
        super(type, level);
        getPersistentData().putBoolean(NEMESIS_TAG, true);
        setBossKind("mortadelo");
        setCustomNameVisible(true);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SYNCED_BOSS_KIND, "mortadelo");
    }

    public void setBossKind(String kind) {
        String safeKind = switch (kind) {
            case "superintendente", "filemon", "bacterio", "mortadelo" -> kind;
            default -> "mortadelo";
        };
        getPersistentData().putString(BOSS_KIND_TAG, safeKind);
        entityData.set(SYNCED_BOSS_KIND, safeKind);
        String name = switch (safeKind) {
            case "superintendente" -> "El Superintendente";
            case "filemon" -> "Filemón";
            case "bacterio" -> "Profesor Bacterio";
            default -> "Mortadelo";
        };
        setCustomName(Component.literal(name));
    }

    public String getBossKind() {
        return entityData.get(SYNCED_BOSS_KIND);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setBossKind(getPersistentData().getString(BOSS_KIND_TAG));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || tickCount % 40 != 0) return;
        int phase = getHealth() <= getMaxHealth() * 0.25F ? 2
                : getHealth() <= getMaxHealth() * 0.60F ? 1 : 0;
        int previousPhase = getPersistentData().getInt(BOSS_PHASE_TAG);
        if (phase > previousPhase) {
            getPersistentData().putInt(BOSS_PHASE_TAG, phase);
            enterPhase(phase);
        }
        switch (getBossKind()) {
            case "superintendente" -> {
                for (PuenteNorteRaider ally : level().getEntitiesOfClass(PuenteNorteRaider.class,
                        getBoundingBox().inflate(12.0D), entity -> entity != this && entity.isAlive())) {
                    ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0));
                }
            }
            case "filemon" -> {
                if (getHealth() <= getMaxHealth() * 0.5F) {
                    addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1));
                    addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 0));
                }
            }
            case "bacterio" -> {
                LivingEntity target = getTarget();
                if (target instanceof Player && distanceToSqr(target) <= 100.0D && hasLineOfSight(target)) {
                    target.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
                }
            }
            case "mortadelo" -> {
                LivingEntity target = getTarget();
                if (target instanceof Player && distanceToSqr(target) <= 144.0D
                        && phase >= 1) {
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, phase - 1));
                }
            }
            default -> { }
        }
    }

    private void enterPhase(int phase) {
        int duration = phase == 1 ? 160 : 240;
        switch (getBossKind()) {
            case "superintendente" -> addEffect(new MobEffectInstance(
                    MobEffects.DAMAGE_RESISTANCE, duration, phase - 1));
            case "filemon" -> {
                addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, phase));
                addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, duration, phase - 1));
            }
            case "bacterio" -> addEffect(new MobEffectInstance(
                    MobEffects.REGENERATION, duration, phase - 1));
            default -> {
                addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, duration, phase));
                addEffect(new MobEffectInstance(MobEffects.REGENERATION, duration, phase - 1));
            }
        }
        String label = phase == 1 ? "entra en combate intensificado" : "desata su fase final";
        level().getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class,
                        getBoundingBox().inflate(40.0D), player -> player.isAlive())
                .forEach(player -> player.displayClientMessage(Component.literal(
                        getName().getString() + " " + label + "!"), true));
    }
}
