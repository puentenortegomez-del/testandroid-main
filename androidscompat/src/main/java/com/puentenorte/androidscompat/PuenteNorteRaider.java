package com.puentenorte.androidscompat;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class PuenteNorteRaider extends Pillager {
    public static final String FACTION_TAG = "androidscompat_puente_norte";
    public static final String NEMESIS_TAG = "androidscompat_nemesis";
    public static final String RANK_TAG = "androidscompat_nemesis_rank";

    protected PuenteNorteRaider(EntityType<? extends Pillager> type, Level level) {
        super(type, level);
        getPersistentData().putBoolean(FACTION_TAG, true);
    }

    @Override
    protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random,
                                                net.minecraft.world.DifficultyInstance difficulty) {
        super.populateDefaultEquipmentSlots(random, difficulty);
        ItemStack rifle = PuenteNorteFaction.findCgsWeapon(random);
        if (!rifle.isEmpty()) {
            setItemSlot(EquipmentSlot.MAINHAND, rifle);
            setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        } else {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        }
    }

    public boolean isNemesis() {
        return getPersistentData().getBoolean(NEMESIS_TAG);
    }
}
