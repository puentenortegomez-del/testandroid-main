package com.puentenorte.androidscompat;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** A village guard that is friendly until its villages' shared diplomacy turns hostile. */
public class VillageDefender extends Pillager {
    public static final String DEFENDER_TAG = "androidscompat_village_defender";
    public static final String HOME_X_TAG = "androidscompat_village_home_x";
    public static final String HOME_Y_TAG = "androidscompat_village_home_y";
    public static final String HOME_Z_TAG = "androidscompat_village_home_z";
    public static final String RECRUITED_TAG = "androidscompat_village_recruited";
    public static final String SKIN_TAG = "androidscompat_village_skin";
    public static final String AMMO_RESERVE_TAG = "androidscompat_village_ammo_reserve";
    public static final String NEXT_RELOAD_TAG = "androidscompat_village_next_reload";
    private static final EntityDataAccessor<Integer> SKIN_VARIANT =
            SynchedEntityData.defineId(VillageDefender.class, EntityDataSerializers.INT);
        private static final EntityDataAccessor<Boolean> COMMANDER_SKIN =
            SynchedEntityData.defineId(VillageDefender.class, EntityDataSerializers.BOOLEAN);

    public VillageDefender(EntityType<? extends Pillager> type, Level level) {
        super(type, level);
        getPersistentData().putBoolean(DEFENDER_TAG, true);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SKIN_VARIANT, 0);
        entityData.define(COMMANDER_SKIN, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal<>(
            this, 1.0D, 18.0F));
        goalSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(this, 1.0D, false));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class,
                10, true, false, target -> VillageDefenderFaction.isHostileToPlayers(level())));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, PuenteNorteRaider.class,
                10, true, false, target -> VillageDefenderFaction.isAtWar(level())));
    }

    public void setVillageHome(net.minecraft.core.BlockPos home) {
        var data = getPersistentData();
        data.putInt(HOME_X_TAG, home.getX());
        data.putInt(HOME_Y_TAG, home.getY());
        data.putInt(HOME_Z_TAG, home.getZ());
    }

    public void setSkinVariant(int skin) {
        int variant = Math.floorMod(skin, 5);
        getPersistentData().putInt(SKIN_TAG, variant);
        entityData.set(SKIN_VARIANT, variant);
    }

    public int getSkinVariant() {
        return Math.floorMod(entityData.get(SKIN_VARIANT), 5);
    }

    public void setCommanderSkin(boolean commander) {
        getPersistentData().putBoolean("androidscompat_village_commander_skin", commander);
        entityData.set(COMMANDER_SKIN, commander);
    }

    public boolean hasCommanderSkin() { return entityData.get(COMMANDER_SKIN); }

    @Override
    protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random,
                                                  net.minecraft.world.DifficultyInstance difficulty) {
        super.populateDefaultEquipmentSlots(random, difficulty);
        VillageDefenderFaction.equipDefenderWeapon(this, random, this instanceof VillageDefenderChief);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSkinVariant(getPersistentData().contains(SKIN_TAG)
            ? getPersistentData().getInt(SKIN_TAG) : getRandom().nextInt(5));
        setCommanderSkin(this instanceof VillageDefenderChief
            || getPersistentData().getBoolean("androidscompat_village_commander_skin"));
        getPersistentData().putBoolean(DEFENDER_TAG, true);
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(SKIN_TAG, getSkinVariant());
        tag.putBoolean("androidscompat_village_commander_skin", hasCommanderSkin());
    }

    static boolean isDefender(LivingEntity entity) {
        return entity instanceof VillageDefender
                || entity.getPersistentData().getBoolean(DEFENDER_TAG);
    }
}
