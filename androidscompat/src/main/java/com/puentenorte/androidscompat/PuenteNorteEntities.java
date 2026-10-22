package com.puentenorte.androidscompat;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.thunderbear06.entity.android.AndroidEntity;
import com.thunderbear06.entity.android.RogueDroidEntity;

@Mod.EventBusSubscriber(modid = AndroidsCompatMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PuenteNorteEntities {
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(
            ForgeRegistries.ENTITY_TYPES, AndroidsCompatMod.MOD_ID);

    public static final RegistryObject<EntityType<PuenteNorteSoldier>> SOLDIER = TYPES.register(
            "puente_norte_soldier", () -> EntityType.Builder
                    .<PuenteNorteSoldier>of(PuenteNorteSoldier::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).clientTrackingRange(8).updateInterval(2)
                    .build("androidscompat:puente_norte_soldier"));
    public static final RegistryObject<EntityType<PuenteNorteBoss>> BOSS = TYPES.register(
            "puente_norte_boss", () -> EntityType.Builder
                    .<PuenteNorteBoss>of(PuenteNorteBoss::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).clientTrackingRange(10).updateInterval(2)
                    .build("androidscompat:puente_norte_boss"));
    public static final RegistryObject<EntityType<PuenteNorteBasicAndroid>> BASIC_ANDROID = TYPES.register(
            "puente_norte_basic_android", () -> EntityType.Builder
                    .<PuenteNorteBasicAndroid>of(PuenteNorteBasicAndroid::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).clientTrackingRange(8).updateInterval(2)
                    .build("androidscompat:puente_norte_basic_android"));
    public static final RegistryObject<EntityType<PuenteNorteRogueAndroid>> ROGUE_ANDROID = TYPES.register(
            "puente_norte_rogue_android", () -> EntityType.Builder
                    .<PuenteNorteRogueAndroid>of(PuenteNorteRogueAndroid::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).clientTrackingRange(8).updateInterval(2)
                    .build("androidscompat:puente_norte_rogue_android"));
    public static final RegistryObject<EntityType<VillageDefender>> VILLAGE_DEFENDER = TYPES.register(
            "village_defender", () -> EntityType.Builder
                    .<VillageDefender>of(VillageDefender::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).clientTrackingRange(8).updateInterval(2)
                    .build("androidscompat:village_defender"));
    public static final RegistryObject<EntityType<VillageDefenderChief>> VILLAGE_DEFENDER_CHIEF = TYPES.register(
            "village_defender_chief", () -> EntityType.Builder
                    .<VillageDefenderChief>of(VillageDefenderChief::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).clientTrackingRange(10).updateInterval(2)
                    .build("androidscompat:village_defender_chief"));

    private PuenteNorteEntities() {}

    public static void register(IEventBus bus) { TYPES.register(bus); }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(SOLDIER.get(), Pillager.createAttributes()
                .add(Attributes.MAX_HEALTH, 28.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.FOLLOW_RANGE, 40.0D).build());
        event.put(BOSS.get(), Pillager.createAttributes()
                .add(Attributes.MAX_HEALTH, 180.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.34D)
                .add(Attributes.ARMOR, 12.0D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D).build());
        event.put(BASIC_ANDROID.get(), PuenteNorteBasicAndroid.createFactionAttributes().build());
        event.put(ROGUE_ANDROID.get(), RogueDroidEntity.createAndroidAttributes()
                .add(Attributes.MAX_HEALTH, 44.0D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.ARMOR, 7.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D).build());
        event.put(VILLAGE_DEFENDER.get(), Pillager.createAttributes()
                .add(Attributes.MAX_HEALTH, 26.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D).build());
        event.put(VILLAGE_DEFENDER_CHIEF.get(), Pillager.createAttributes()
                .add(Attributes.MAX_HEALTH, 42.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.31D)
                .add(Attributes.ARMOR, 5.0D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D).build());
    }
}
