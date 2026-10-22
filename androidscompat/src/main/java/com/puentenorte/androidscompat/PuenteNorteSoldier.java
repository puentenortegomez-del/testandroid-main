package com.puentenorte.androidscompat;

import net.minecraft.world.entity.EntityType;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class PuenteNorteSoldier extends PuenteNorteRaider {
    public static final String VARIANT_TAG = "androidscompat_pn_soldier_variant";
    public static final String SUBFACTION_TAG = "androidscompat_pn_subfaction";
    public static final String ARCHETYPE_TAG = "androidscompat_pn_archetype";
    public static final String THREAT_RANK_TAG = "androidscompat_pn_threat_rank";
    public static final String TRAIT_TAG = "androidscompat_pn_adaptation";
    public static final String TRAIT_LEVEL_TAG = "androidscompat_pn_adaptation_level";
    public static final String LAST_METHOD_TAG = "androidscompat_pn_last_method";
    public static final String METHOD_COUNT_TAG = "androidscompat_pn_method_count";
    public static final String FIRE_ADAPTATION_TAG = "androidscompat_pn_adapt_fire";
    public static final String PROJECTILE_ADAPTATION_TAG = "androidscompat_pn_adapt_projectile";
    public static final String MELEE_ADAPTATION_TAG = "androidscompat_pn_adapt_melee";
    public static final String FIRE_HIT_COUNT_TAG = "androidscompat_pn_hits_fire";
    public static final String PROJECTILE_HIT_COUNT_TAG = "androidscompat_pn_hits_projectile";
    public static final String MELEE_HIT_COUNT_TAG = "androidscompat_pn_hits_melee";
    private static final EntityDataAccessor<Integer> SKIN_VARIANT =
            SynchedEntityData.defineId(PuenteNorteSoldier.class, EntityDataSerializers.INT);

    public PuenteNorteSoldier(EntityType<? extends PuenteNorteSoldier> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SKIN_VARIANT, 0);
    }

    public void configure(String variant, String subfaction, String archetype, int threatRank) {
        getPersistentData().putString(VARIANT_TAG, variant);
        getPersistentData().putString(SUBFACTION_TAG, subfaction);
        getPersistentData().putString(ARCHETYPE_TAG, archetype);
        getPersistentData().putInt(THREAT_RANK_TAG, threatRank);
        getPersistentData().putInt(RANK_TAG, 1);
        setSkinVariant(getRandom().nextInt(3));
    }

    public int getSkinVariant() {
        return Math.floorMod(entityData.get(SKIN_VARIANT), 3);
    }

    private void setSkinVariant(int variant) {
        int safeVariant = Math.floorMod(variant, 3);
        getPersistentData().putInt("androidscompat_pn_skin_variant", safeVariant);
        entityData.set(SKIN_VARIANT, safeVariant);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSkinVariant(getPersistentData().contains("androidscompat_pn_skin_variant")
                ? getPersistentData().getInt("androidscompat_pn_skin_variant") : getRandom().nextInt(3));
    }

    public String getVariant() {
        String variant = getPersistentData().getString(VARIANT_TAG);
        return variant.isEmpty() ? "linea" : variant;
    }

    public String getArchetype() {
        String archetype = getPersistentData().getString(ARCHETYPE_TAG);
        if (getPersistentData().getBoolean(NEMESIS_TAG)
                && getPersistentData().getInt(RANK_TAG) >= AndroidsCompatMod.PN_MINIBOSS_RANK.get()) return "mini_boss";
        if (getPersistentData().getBoolean(NEMESIS_TAG)
                && getPersistentData().getInt(RANK_TAG) >= AndroidsCompatMod.PN_STALKER_RANK.get()) return "stalker";
        return archetype.isEmpty() ? "normal" : archetype;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || !"apoyo".equals(getVariant()) || tickCount % 60 != 0) {
            return;
        }
        MobEffectInstance supportEffect = switch (getPersistentData().getString(SUBFACTION_TAG)) {
            case "superintendente" -> new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 0);
            case "filemon" -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 0);
            case "bacterio" -> new MobEffectInstance(MobEffects.DAMAGE_BOOST, 80, 0);
            default -> new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 0);
        };
        for (LivingEntity ally : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(8.0D), entity -> entity != this && entity.isAlive()
                        && entity.getPersistentData().getBoolean(PuenteNorteRaider.FACTION_TAG))) {
            ally.addEffect(new MobEffectInstance(supportEffect));
        }
        addEffect(new MobEffectInstance(supportEffect));
    }
}
