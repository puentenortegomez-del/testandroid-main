package com.puentenorte.androidscompat;

import com.thunderbear06.entity.android.AndroidEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.npc.Villager;

/** Faction-only hostile reskin of the basic CC:Androids unit. */
public final class PuenteNorteBasicAndroid extends AndroidEntity {
    public PuenteNorteBasicAndroid(EntityType<? extends AndroidEntity> type, Level level) {
        super(type, level);
        getPersistentData().putBoolean(PuenteNorteRaider.FACTION_TAG, true);
        getPersistentData().putBoolean("androidscompat_pn_enemy_android", true);
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15D, true));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Villager.class, true));
    }

    public static AttributeSupplier.Builder createFactionAttributes() {
        return AndroidEntity.createAndroidAttributes()
                .add(Attributes.MAX_HEALTH, 36.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.31D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.ARMOR, 5.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }
}
