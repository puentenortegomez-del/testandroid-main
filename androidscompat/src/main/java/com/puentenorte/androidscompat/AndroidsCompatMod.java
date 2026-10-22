package com.puentenorte.androidscompat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.items.IItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.joml.Vector3f;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Server-side bridge for CC:Androids, NTGL, Immersive Aircraft and optional
 * Simple Voice Chat speech output.
 *
 * NTGL already installs GunAttackGoal on PathfinderMobs holding an IWeapon.
 * The missing piece for CC:Androids is a vanilla Mob target. This class adds
 * that target without calling the Android Lua attack() method, so it cannot
 * turn a ranged weapon into a melee attack. When an Android is mounted in an
 * Immersive Aircraft vehicle, the same bridge supplies flight inputs and calls
 * the vehicle's mounted weapons directly on the server.
 */
@Mod(AndroidsCompatMod.MOD_ID)
public final class AndroidsCompatMod {
    public static final String MOD_ID = "androidscompat";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final ForgeConfigSpec SERVER_CONFIG;
    private static final ForgeConfigSpec.ConfigValue<String> TTS_ENDPOINT;
    private static final ForgeConfigSpec.IntValue TTS_TIMEOUT_MS;
    private static final ForgeConfigSpec.DoubleValue TTS_VOICE_DISTANCE;
    static final ForgeConfigSpec.BooleanValue PN_ENABLED;
    static final ForgeConfigSpec.BooleanValue PN_AUTONOMOUS_RAIDS;
    static final ForgeConfigSpec.BooleanValue PN_BUILD_BASES;
    static final ForgeConfigSpec.BooleanValue PN_CGS_WEAPONS;
    static final ForgeConfigSpec.BooleanValue PN_BOSSES;
    static final ForgeConfigSpec.BooleanValue PN_NEMESIS;
    static final ForgeConfigSpec.BooleanValue PN_ADAPTATION;
    static final ForgeConfigSpec.BooleanValue PN_AIR_SUPPORT;
    static final ForgeConfigSpec.BooleanValue PN_VILLAGE_CONQUEST;
    static final ForgeConfigSpec.IntValue PN_GRACE_PERIOD_TICKS;
    static final ForgeConfigSpec.DoubleValue PN_AIR_SUPPORT_CHANCE;
    static final ForgeConfigSpec.BooleanValue PN_PLAYER_BASE_RAIDS;
    static final ForgeConfigSpec.BooleanValue PN_PLAYER_THEFT;
    static final ForgeConfigSpec.DoubleValue PN_THEFT_CHANCE;
    static final ForgeConfigSpec.IntValue PN_BASE_LOOT_LIMIT;
    static final ForgeConfigSpec.IntValue PN_AIRCRAFT_PER_BASE;
    static final ForgeConfigSpec.IntValue PN_AIRCRAFT_CRAFT_INTERVAL;
    static final ForgeConfigSpec.IntValue PN_RAID_INTERVAL_MINUTES;
    static final ForgeConfigSpec.IntValue PN_RAID_SCAN_TICKS;
    static final ForgeConfigSpec.IntValue PN_ACTIVATION_RADIUS;
    static final ForgeConfigSpec.IntValue PN_ROSTER_RADIUS;
    static final ForgeConfigSpec.IntValue PN_CONQUEST_TICK_INTERVAL;
    static final ForgeConfigSpec.IntValue PN_CONQUEST_POINTS;
    static final ForgeConfigSpec.IntValue PN_CONQUEST_REPEL_POINTS;
    static final ForgeConfigSpec.IntValue PN_CONQUEST_MIN_RAIDERS;
    static final ForgeConfigSpec.IntValue PN_MAX_ACTIVE_SIEGES;
    static final ForgeConfigSpec.IntValue PN_MAX_OCCUPIED_VILLAGES;
    static final ForgeConfigSpec.IntValue PN_CONQUEST_COOLDOWN_TICKS;
    static final ForgeConfigSpec.IntValue PN_OUTPOST_BUILD_STEP_TICKS;
    static final ForgeConfigSpec.DoubleValue PN_OUTPOST_PRISONER_CHANCE;
    static final ForgeConfigSpec.BooleanValue VILLAGE_DEFENDERS_ENABLED;
    static final ForgeConfigSpec.DoubleValue VILLAGE_SKIRMISH_CHANCE;
    static final ForgeConfigSpec.IntValue PN_MAX_SQUAD_SIZE;
    static final ForgeConfigSpec.DoubleValue PN_SQUAD_SIZE_MULTIPLIER;
    static final ForgeConfigSpec.DoubleValue PN_BOSS_CHANCE;
    static final ForgeConfigSpec.IntValue PN_MAX_NEMESIS_RANK;
    static final ForgeConfigSpec.IntValue PN_STALKER_RANK;
    static final ForgeConfigSpec.IntValue PN_MINIBOSS_RANK;
    static final ForgeConfigSpec.IntValue PN_RANK_UPS_PER_KILL;
    static final ForgeConfigSpec.IntValue PN_ADAPTATION_HITS;
    static final ForgeConfigSpec.DoubleValue PN_MAX_ADAPTATION_RESISTANCE;
    static final ForgeConfigSpec.IntValue PN_HEALTH_PER_RANK;
    static final ForgeConfigSpec.DoubleValue PN_ARMOR_PER_RANK;
    static final ForgeConfigSpec.DoubleValue PN_STALKER_SPEED;
    static final ForgeConfigSpec.DoubleValue PN_SUPER_LINE_RATIO;
    static final ForgeConfigSpec.DoubleValue PN_FILEMON_LINE_RATIO;
    static final ForgeConfigSpec.DoubleValue PN_BACTERIO_LINE_RATIO;
    static final ForgeConfigSpec.DoubleValue PN_MORTADELO_LINE_RATIO;
    static final ForgeConfigSpec.DoubleValue PN_SUPER_STALKER_RATIO;
    static final ForgeConfigSpec.DoubleValue PN_FILEMON_STALKER_RATIO;
    static final ForgeConfigSpec.DoubleValue PN_BACTERIO_STALKER_RATIO;
    static final ForgeConfigSpec.DoubleValue PN_MORTADELO_STALKER_RATIO;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("tts");
        TTS_ENDPOINT = builder
                .comment("HTTP(S) endpoint: POST UTF-8 text/plain, return a PCM WAV audio response.")
                .define("endpoint", "");
        TTS_TIMEOUT_MS = builder
                .comment("Connect and response timeout for the TTS service.")
                .defineInRange("timeoutMs", 10000, 1000, 60000);
        TTS_VOICE_DISTANCE = builder
                .comment("Maximum distance at which Simple Voice Chat listeners hear Android speech.")
                .defineInRange("voiceDistance", 32.0D, 1.0D, 128.0D);
        builder.pop();
        builder.push("puenteNorte");
        PN_ENABLED = builder.comment("Enables the Puente Norte faction.").define("enabled", true);
        PN_AUTONOMOUS_RAIDS = builder.comment("Server-side village raids while the world is running.").define("autonomousRaids", true);
        PN_BUILD_BASES = builder.comment("Allows raids to generate fortified faction outposts.").define("buildBases", true);
        PN_CGS_WEAPONS = builder.comment("Equip CGS NTGL weapons when available; otherwise use crossbows.").define("cgsWeapons", true);
        PN_BOSSES = builder.comment("Allows named subfaction bosses and Mortadelo to appear.").define("bossesEnabled", true);
        PN_NEMESIS = builder.comment("Enables persistent Nemesis ranking and player-specific campaign pressure.").define("nemesisEnabled", true);
        PN_ADAPTATION = builder.comment("Allows soldiers to adapt to repeated fire, projectile and melee damage.").define("adaptiveResistance", true);
        PN_AIR_SUPPORT = builder.comment("Lets raids use nearby unoccupied Immersive Aircraft / Man of Many Planes aircraft.").define("airSupport", true);
        PN_VILLAGE_CONQUEST = builder.comment("Allows Puente Norte to occupy villages after sustained, uncontested sieges; players can liberate them.").define("villageConquest", true);
        PN_GRACE_PERIOD_TICKS = builder.comment("World age in ticks before Puente Norte can appear; 36000 ticks is 1.5 in-game days.").defineInRange("gracePeriodTicks", 36000, 0, 2400000);
        PN_AIR_SUPPORT_CHANCE = builder.comment("Chance that a raid assigns an available compatible aircraft to an air-support pilot.").defineInRange("airSupportChance", 0.35D, 0.0D, 1.0D);
        PN_PLAYER_BASE_RAIDS = builder.comment("Allows raids to target online players at their saved respawn bases.").define("playerBaseRaids", true);
        PN_PLAYER_THEFT = builder.comment("Allows raiders to steal carried items during attacks on players.").define("playerTheft", true);
        PN_THEFT_CHANCE = builder.comment("Chance per successful faction hit to steal one valuable carried item.").defineInRange("theftChancePerHit", 0.12D, 0.0D, 1.0D);
        PN_BASE_LOOT_LIMIT = builder.comment("Maximum valuable items stolen from player-base containers during one raid.").defineInRange("baseLootLimit", 5, 0, 32);
        PN_AIRCRAFT_PER_BASE = builder.comment("Maximum faction-crafted aircraft near one faction base.").defineInRange("aircraftPerBase", 2, 0, 10);
        PN_AIRCRAFT_CRAFT_INTERVAL = builder.comment("Minimum in-game ticks between completed faction aircraft builds.").defineInRange("aircraftCraftIntervalTicks", 12000, 200, 240000);
        PN_RAID_INTERVAL_MINUTES = builder.comment("Minimum in-game minutes between faction raids at a village.").defineInRange("raidIntervalMinutes", 20, 1, 240);
        PN_RAID_SCAN_TICKS = builder.comment("How often the server scans active players for eligible villages.").defineInRange("raidScanTicks", 1200, 100, 12000);
        PN_ACTIVATION_RADIUS = builder.comment("Maximum player distance from a village for autonomous raids.").defineInRange("activationRadius", 160, 32, 1024);
        PN_ROSTER_RADIUS = builder.comment("Radius shown in the Nemesis roster.").defineInRange("rosterRadius", 512, 32, 4096);
        PN_CONQUEST_TICK_INTERVAL = builder.comment("Ticks between loaded-village siege updates; 100 ticks is 5 seconds.").defineInRange("conquestUpdateTicks", 100, 20, 1200);
        PN_CONQUEST_POINTS = builder.comment("Control points gained per update when attackers outnumber defenders.").defineInRange("conquestPointsPerUpdate", 1, 1, 5);
        PN_CONQUEST_REPEL_POINTS = builder.comment("Control points lost per update when defenders repel attackers or liberate an occupied village.").defineInRange("conquestRepelPointsPerUpdate", 2, 1, 10);
        PN_CONQUEST_MIN_RAIDERS = builder.comment("Minimum nearby faction raiders needed to advance a village siege.").defineInRange("conquestMinimumRaiders", 3, 1, 20);
        PN_MAX_ACTIVE_SIEGES = builder.comment("Maximum simultaneous village sieges across the server.").defineInRange("maxActiveVillageSieges", 1, 1, 5);
        PN_MAX_OCCUPIED_VILLAGES = builder.comment("Maximum villages Puente Norte can hold at once; players can liberate them.").defineInRange("maxOccupiedVillages", 3, 1, 20);
        PN_CONQUEST_COOLDOWN_TICKS = builder.comment("Protection after a siege is repelled or a village is liberated.").defineInRange("conquestCooldownTicks", 24000, 1200, 240000);
        PN_OUTPOST_BUILD_STEP_TICKS = builder.comment("Ticks between placing construction blocks at a live faction outpost.").defineInRange("outpostBuildStepTicks", 3, 1, 40);
        PN_OUTPOST_PRISONER_CHANCE = builder.comment("Chance that a new faction outpost contains a captive rebel villager.").defineInRange("outpostPrisonerChance", 0.55D, 0.0D, 1.0D);
        VILLAGE_DEFENDERS_ENABLED = builder.comment("Enables allied guards, village diplomacy, missions, recruitment and faction conflicts.").define("villageDefendersEnabled", true);
        VILLAGE_SKIRMISH_CHANCE = builder.comment("Chance per village scan for a small Puente Norte versus village guard skirmish.").defineInRange("villageSkirmishChance", 0.045D, 0.0D, 0.5D);
        PN_MAX_SQUAD_SIZE = builder.comment("Hard cap for soldiers in one raid squad.").defineInRange("maxSquadSize", 20, 1, 80);
        PN_SQUAD_SIZE_MULTIPLIER = builder.comment("Global multiplier for faction raid strength.").defineInRange("squadSizeMultiplier", 1.0D, 0.1D, 5.0D);
        PN_BOSS_CHANCE = builder.comment("Chance to include a subfaction boss once the campaign unlocks them.").defineInRange("bossChance", 0.5D, 0.0D, 1.0D);
        PN_MAX_NEMESIS_RANK = builder.comment("Maximum rank for a Nemesis soldier.").defineInRange("maxNemesisRank", 5, 1, 10);
        PN_STALKER_RANK = builder.comment("Rank at which a Nemesis soldier becomes a Stalker.").defineInRange("stalkerRank", 3, 2, 9);
        PN_MINIBOSS_RANK = builder.comment("Rank at which a Nemesis soldier becomes a mini-boss.").defineInRange("miniBossRank", 5, 3, 10);
        PN_RANK_UPS_PER_KILL = builder.comment("Nemesis ranks gained each time its soldier kills a player.").defineInRange("ranksPerPlayerKill", 1, 1, 3);
        PN_ADAPTATION_HITS = builder.comment("Repeated hits of the same damage class required to learn resistance.").defineInRange("adaptationHits", 3, 1, 20);
        PN_MAX_ADAPTATION_RESISTANCE = builder.comment("Maximum share of matching damage resisted by an adapted soldier.").defineInRange("maxAdaptationResistance", 0.45D, 0.0D, 0.8D);
        PN_HEALTH_PER_RANK = builder.comment("Additional Nemesis maximum health per rank.").defineInRange("healthPerRank", 22, 0, 100);
        PN_ARMOR_PER_RANK = builder.comment("Nemesis armor gained per rank.").defineInRange("armorPerRank", 2.5D, 0.0D, 10.0D);
        PN_STALKER_SPEED = builder.comment("Movement speed for Stalker and mini-boss Nemeses.").defineInRange("stalkerSpeed", 0.4D, 0.1D, 1.0D);
        PN_SUPER_LINE_RATIO = builder.defineInRange("superintendentLineRatio", 0.65D, 0.0D, 1.0D);
        PN_FILEMON_LINE_RATIO = builder.defineInRange("filemonLineRatio", 0.8D, 0.0D, 1.0D);
        PN_BACTERIO_LINE_RATIO = builder.defineInRange("bacterioLineRatio", 0.3D, 0.0D, 1.0D);
        PN_MORTADELO_LINE_RATIO = builder.defineInRange("mortadeloLineRatio", 0.5D, 0.0D, 1.0D);
        PN_SUPER_STALKER_RATIO = builder.defineInRange("superintendentStalkerRatio", 0.15D, 0.0D, 1.0D);
        PN_FILEMON_STALKER_RATIO = builder.defineInRange("filemonStalkerRatio", 0.35D, 0.0D, 1.0D);
        PN_BACTERIO_STALKER_RATIO = builder.defineInRange("bacterioStalkerRatio", 0.25D, 0.0D, 1.0D);
        PN_MORTADELO_STALKER_RATIO = builder.defineInRange("mortadeloStalkerRatio", 0.45D, 0.0D, 1.0D);
        builder.pop();
        SERVER_CONFIG = builder.build();
    }

    private static final String ANDROID_BASE_CLASS =
            "com.thunderbear06.entity.android.BaseAndroidEntity";
    private static final String COMMAND_ANDROID_CLASS =
            "com.thunderbear06.entity.android.CommandAndroidEntity";
    private static final String ANDROID_FRAME_CLASS =
            "com.thunderbear06.entity.android.frame.AndroidFrame";
    private static final String DIAMOND_COMMAND_FRAME_TAG =
            "androidscompat_diamond_command_frame";
    private static final String SKIN_COLOR_TAG = "androidscompat_skin_color";
    private static final int DEFAULT_SKIN_COLOR = 0xFFFFFF;
    private static final int DEFAULT_COMMAND_SKIN_COLOR = 0x00008B;
    private static final String NTGL_WEAPON_CLASS =
            "com.nukateam.ntgl.common.foundation.item.interfaces.IWeapon";
    private static final String NTGL_GOAL_CLASS =
            "com.nukateam.ntgl.common.foundation.goals.GunAttackGoal";
    // NTGL firing is opt-in per Android. Lua role programs explicitly open a
    // firing session; merely equipping a gun must never start combat.
    private static final Set<UUID> ntglScriptSessions = ConcurrentHashMap.newKeySet();
    private static final String NTGL_WEAPON_DATA_CLASS =
            "com.nukateam.ntgl.common.data.WeaponData";
    private static final String NTGL_STATE_HELPER_CLASS =
            "com.nukateam.ntgl.common.util.util.WeaponStateHelper";
    private static final String NTGL_AMMO_HOLDER_CLASS =
            "com.nukateam.ntgl.common.data.holders.AmmoHolder";
    private static final String NTGL_INVENTORY_UTIL_CLASS =
            "com.nukateam.ntgl.common.util.util.InventoryUtil";
    private static final String NTGL_AMMO_CONTEXT_CLASS =
            "com.nukateam.ntgl.common.util.helpers.context.IAmmoContext";
    private static final String RELOAD_CHAT = "!androidscompat reload";
    private static final String HELP_CHAT = "!help list";
    private static final String AUTOPILOT_CHAT_PREFIX = "!androidscompat autopilot";
    private static final String ORDER_CHAT_PREFIX = "!androidscompat order";
    private static final String CAPTURE_CHAT = "!androidscompat capture";
    private static final String IMPRISON_CHAT = "!androidscompat imprison";
    private static final String RELEASE_CHAT = "!androidscompat release";
    private static final String SPY_CHAT_PREFIX = "!androidscompat spy";
    private static final String INTEL_CHAT_PREFIX = "!androidscompat intel";
    private static final String AIRCRAFT_VEHICLE_CLASS =
            "immersive_aircraft.entity.VehicleEntity";
    private static final String AIRCRAFT_INVENTORY_CLASS =
            "immersive_aircraft.entity.InventoryVehicleEntity";
    private static final double TARGET_RANGE = 32.0D;
    private static final int CHECK_EVERY_TICKS = 5;
    private static final int DIRECT_GROUND_FIRE_COOLDOWN = 10;
    private static final int MAX_SAFE_DROP = 4;
    private static final int EDGE_HOLD_TICKS = 12;
    private static final int SUPPORT_SCAN_DEPTH = 16;
    private static final long XP_PER_LEVEL_SQUARE = 100L;
    private static final String XP_TAG = "androidscompat_xp";
    private static final String LEVEL_TAG = "androidscompat_level";
    private static final String BASE_SPEED_TAG = "androidscompat_base_speed";
    private static final String BASE_HEALTH_TAG = "androidscompat_base_health";
    private static final String BASE_FOLLOW_TAG = "androidscompat_base_follow";
    /** 25 hearts: the physical Command Android cannot exceed this value. */
    private static final double COMMAND_ANDROID_MAX_HEALTH = 50.0D;
    private static final String SQUAD_TAG = "androidscompat_squad";
    private static final String ORDER_TAG = "androidscompat_order";
    private static final String ORDER_TARGET_TAG = "androidscompat_order_target";
    /** Explicit server-side fire gate, independent of the Lua process. */
    private static final String COMBAT_ENABLED_TAG = "androidscompat_combat_enabled";
    private static final String GROUND_HOME_X_TAG = "androidscompat_ground_home_x";
    private static final String GROUND_HOME_Y_TAG = "androidscompat_ground_home_y";
    private static final String GROUND_HOME_Z_TAG = "androidscompat_ground_home_z";
    private static final String COMMANDER_TAG = "androidscompat_commander";
    private static final String OWNER_TAG = "androidscompat_owner";
    private static final String AIR_ROLE_TAG = "androidscompat_air_role";
    private static final String PN_AIR_PILOT_TAG = "androidscompat_pn_air_pilot";
    private static final String VILLAGE_AIR_PILOT_TAG = "androidscompat_village_air_pilot";
    private static final String VILLAGE_ROUTE_ORIGIN_X = "androidscompat_village_route_origin_x";
    private static final String VILLAGE_ROUTE_ORIGIN_Y = "androidscompat_village_route_origin_y";
    private static final String VILLAGE_ROUTE_ORIGIN_Z = "androidscompat_village_route_origin_z";
    private static final String VILLAGE_ROUTE_TARGET_X = "androidscompat_village_route_target_x";
    private static final String VILLAGE_ROUTE_TARGET_Y = "androidscompat_village_route_target_y";
    private static final String VILLAGE_ROUTE_TARGET_Z = "androidscompat_village_route_target_z";
    private static final String VILLAGE_ROUTE_RETURNING = "androidscompat_village_route_returning";
    private static final String SPY_TAG = "androidscompat_spy";
    private static final String SPY_COVER_TAG = "androidscompat_spy_cover";
    private static final String SPY_COVER_SET_TAG = "androidscompat_spy_cover_set";
    private static final String CAPTURED_BY_TAG = "androidscompat_captured_by";
    private static final String IMPRISONED_TAG = "androidscompat_imprisoned";
    private static final String PRISON_X_TAG = "androidscompat_prison_x";
    private static final String PRISON_Y_TAG = "androidscompat_prison_y";
    private static final String PRISON_Z_TAG = "androidscompat_prison_z";
    private static final String[] OPTIONAL_MODS = {
            "ntgl", "cgs", "immersive_aircraft", "man_of_many_planes",
            "voicechat", "voicechat_api",
            "curios", "sophisticatedbackpacks", "create", "createbigcannons",
            "create_radar", "createaddition", "mekanism", "mekanismgenerators",
            "advancedperipherals", "computercraft", "cccbridge", "carryon",
            "securitycraft", "easy_npc", "alexsmobs", "vampirism", "werewolves",
            "shieldexp", "puffish_skills", "appliedenergistics2", "railways",
            "create_sa", "create_confectionery", "create_central_kitchen",
            "createdeco", "createbb", "create_structures_arise", "createoplenty",
            "numismatics", "bellsandwhistles", "mek_x_star", "linguaperipherals",
            "peripherals", "ccwake", "ars_nouveau", "artifacts", "quark",
            "farmersdelight", "biomesoplenty", "easy_npc_bundle", "easy_npc_config_ui",
            "corpse", "lootr", "journeymap", "immersive_melodies",
            "immersive_paintings", "northstar", "casinocraft", "clumps",
            "chunkloaders", "embeddium", "oculus", "jei",
            "vampirism_integrations", "corpsecurioscompat", "easy_model_entities"
    };
    private static final double AIRCRAFT_TARGET_RANGE = 64.0D;
    private static final int AIRCRAFT_FIRE_COOLDOWN = 15;
    private static final int COMMANDER_INTERVAL_TICKS = 20;
    private static final double COMMANDER_SCAN_RANGE = 48.0D;
    private static final float COMMANDER_RETREAT_HEALTH = 6.0F;
    private static boolean commanderEnabled = true;

    private enum AircraftRole {
        COMBAT,
        BOMBER,
        TRANSPORT
    }

    private enum AircraftOrder {
        PATROL,
        FOLLOW,
        RETURN,
        HOLD
    }

    private static final class AircraftNavigation {
        private final Vec3 home;
        private final long started;
        private AircraftOrder order = AircraftOrder.PATROL;
        private UUID followTarget;

        private AircraftNavigation(Vec3 home, long started) {
            this.home = home;
            this.started = started;
        }
    }

    private static volatile Class<?> ntglWeaponType;
    private static volatile boolean checkedNtgl;
    private static volatile Class<?> aircraftVehicleType;
    private static volatile Class<?> aircraftInventoryType;
    private static volatile boolean checkedAircraft;
    private static final Map<UUID, Long> aircraftNextFire = new HashMap<>();
    private static final Map<UUID, Long> groundNextFire = new HashMap<>();
    private static final Map<UUID, AircraftRole> aircraftRoles = new HashMap<>();
    private static final Map<UUID, AircraftNavigation> aircraftNavigation = new HashMap<>();
    private static final Map<UUID, Boolean> aircraftControlLogged = new HashMap<>();
    private static final Map<UUID, Integer> lastLoggedLevel = new HashMap<>();
    private static final Map<UUID, Integer> edgeHoldTicks = new HashMap<>();
    private static final Map<UUID, Long> ttsNextRequestTick = new HashMap<>();

    public AndroidsCompatMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SERVER_CONFIG);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
        PuenteNorteEntities.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
                .get().getModEventBus());
        SkinColorNetwork.init();
        NemesisNetwork.init();
        LOGGER.info("CC:Androids compatibility loaded; NTGL weapons use server-side targeting");
    }

    @SubscribeEvent
    public void onAndroidDyed(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity().level().isClientSide
                || !(event.getTarget() instanceof Mob android)
                || !isAndroid(android)) {
            return;
        }
        int color = dyeColor(event.getItemStack());
        if (color < 0) {
            return;
        }
        android.getPersistentData().putInt(SKIN_COLOR_TAG, color);
        consumeOne(event.getEntity(), event.getHand());
        SkinColorNetwork.sendToTracking(android, color);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onAndroidStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof Mob android) || !isAndroid(android)) {
            return;
        }
        int defaultColor = isCommandAndroid(android)
                ? DEFAULT_COMMAND_SKIN_COLOR : DEFAULT_SKIN_COLOR;
        int color = android.getPersistentData().contains(SKIN_COLOR_TAG)
                ? android.getPersistentData().getInt(SKIN_COLOR_TAG) : defaultColor;
        SkinColorNetwork.sendToPlayer(player, android, color);
    }

    /**
     * CC:Androids normally accepts only iron/gold plates and reserves the
     * Command Android for a command-block CPU.  For this pack, diamonds are
     * the command plates: ten diamonds plus the normal frame construction and
     * an Android CPU produce the command computer.
     */
    @SubscribeEvent
    public void onFrameConstruction(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity().level().isClientSide
                || !(event.getTarget() instanceof Mob frame)
                || !isAndroidFrame(frame)) {
            return;
        }

        ItemStack held = event.getItemStack();
        if (held.is(Items.DIAMOND) && frameIngotsNeeded(frame) > 0
                && frameComponentsNeeded(frame) == 0) {
            if (invokeDiamondPlates(frame)) {
                frame.getPersistentData().putBoolean(DIAMOND_COMMAND_FRAME_TAG, true);
                consumeOne(event.getEntity(), event.getHand());
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
            return;
        }

        if (isAndroidCpu(held) && frameComponentsNeeded(frame) == 0
                && frameIngotsNeeded(frame) == 0 && frameHasCore(frame)
                && frame.getPersistentData().getBoolean(DIAMOND_COMMAND_FRAME_TAG)) {
            if (finishCommandFrame(frame, held)) {
                consumeOne(event.getEntity(), event.getHand());
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !commanderEnabled
                || event.getServer().getTickCount() % COMMANDER_INTERVAL_TICKS != 0) {
            return;
        }
        AABB worldBounds = new AABB(
                -30_000_000.0D, -30_000_000.0D, -30_000_000.0D,
                30_000_000.0D, 30_000_000.0D, 30_000_000.0D);
        for (ServerLevel level : event.getServer().getAllLevels()) {
            List<Mob> commanders = level.getEntitiesOfClass(
                    Mob.class, worldBounds, AndroidsCompatMod::isCommandAndroid);
            if (commanders.isEmpty()) {
                continue;
            }
            List<Mob> androids = level.getEntitiesOfClass(
                    Mob.class, worldBounds, AndroidsCompatMod::isAndroid);
            for (Mob android : androids) {
                Mob commander = commanders.stream()
                        .filter(candidate -> candidate != android)
                        .min(Comparator.comparingDouble(
                                candidate -> candidate.distanceToSqr(android)))
                        .orElse(null);
                if (commander != null) {
                    commandTroop(commander, android, level.getGameTime());
                }
            }
        }
    }

    private static void commandTroop(Mob commander, Mob android, long gameTime) {
        if (commander.distanceToSqr(android)
                > COMMANDER_SCAN_RANGE * COMMANDER_SCAN_RANGE) {
            return;
        }
        android.getPersistentData().putBoolean(COMMANDER_TAG, false);

        if (isSpy(android) || android.getPersistentData().hasUUID(CAPTURED_BY_TAG)
                || android.getPersistentData().getBoolean(IMPRISONED_TAG)) {
            return;
        }

        if (android.isPassenger() && isAircraft(android.getVehicle())) {
            restoreAircraftRole(android, gameTime);
            return;
        }

        rememberGroundHome(android);
        String order = android.getPersistentData().getString(ORDER_TAG)
                .toLowerCase(java.util.Locale.ROOT);

        if (android.getHealth() <= COMMANDER_RETREAT_HEALTH) {
            if (!"return".equals(order)) {
                android.getPersistentData().putString(ORDER_TAG, "return");
                setCombatEnabled(android, false);
            }
            return;
        }

        // Explicit player orders always win over the commander.
        if ("hold".equals(order) || "return".equals(order)) {
            return;
        }

        if (order.isEmpty()) {
            android.getPersistentData().putString(ORDER_TAG, "patrol");
            order = "patrol";
        }
        setCombatEnabled(android, true);

        LivingEntity enemy = findNearestEnemy(android);
        if (enemy != null) {
            android.setTarget(enemy);
            android.getNavigation().moveTo(enemy, 1.0D);
            return;
        }

        android.setTarget(null);
        if ("patrol".equals(order) || "guard".equals(order)) {
            commanderPatrol(android, gameTime);
        }
    }

    private static void rememberGroundHome(Mob android) {
        var data = android.getPersistentData();
        if (!data.contains(GROUND_HOME_X_TAG)) {
            data.putDouble(GROUND_HOME_X_TAG, android.getX());
            data.putDouble(GROUND_HOME_Y_TAG, android.getY());
            data.putDouble(GROUND_HOME_Z_TAG, android.getZ());
        }
    }

    private static void commanderPatrol(Mob android, long gameTime) {
        if (!android.getNavigation().isDone() && gameTime % 100L != 0L) {
            return;
        }
        var data = android.getPersistentData();
        double homeX = data.getDouble(GROUND_HOME_X_TAG);
        double homeY = data.getDouble(GROUND_HOME_Y_TAG);
        double homeZ = data.getDouble(GROUND_HOME_Z_TAG);
        int phase = Math.floorMod((int) (gameTime / 100L)
                + android.getUUID().hashCode(), 4);
        double radius = 8.0D;
        double x = homeX + (phase == 0 ? radius : phase == 2 ? -radius : 0.0D);
        double z = homeZ + (phase == 1 ? radius : phase == 3 ? -radius : 0.0D);
        android.getNavigation().moveTo(x, homeY, z, 1.0D);
    }

    private static void restoreAircraftRole(Mob android, long gameTime) {
        AircraftRole role = aircraftRoles.get(android.getUUID());
        if (role == null) {
            String stored = android.getPersistentData().getString(AIR_ROLE_TAG);
            try {
                role = AircraftRole.valueOf(stored.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return;
            }
            aircraftRoles.put(android.getUUID(), role);
        }
        Entity vehicle = android.getVehicle();
        aircraftNavigation.computeIfAbsent(android.getUUID(), key ->
                new AircraftNavigation(vehicle.position(), gameTime));
    }

    private static int setCommander(CommandSourceStack source, boolean enabled) {
        commanderEnabled = enabled;
        source.sendSuccess(() -> Component.literal(
                "Comandante IA: " + (enabled ? "activado" : "desactivado")), true);
        return 1;
    }

    private static int commanderStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "Comandante IA: " + (commanderEnabled ? "activo" : "inactivo")), false);
        return 1;
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("androidscompat")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("mount")
                        .executes(context -> mountNearest(context.getSource())))
                .then(Commands.literal("dismount")
                        .executes(context -> dismountNearest(context.getSource())))
                .then(Commands.literal("reload")
                        .executes(context -> reloadNearest(context.getSource())))
                .then(Commands.literal("stats")
                        .executes(context -> statsNearest(context.getSource())))
                .then(Commands.literal("compat")
                        .executes(context -> compatibilityStatus(context.getSource())))
                .then(Commands.literal("home")
                        .then(Commands.literal("set")
                                .executes(context -> setAndroidHome(context.getSource())))
                        .then(Commands.literal("clear")
                                .executes(context -> clearAndroidHome(context.getSource())))
                        .then(Commands.literal("show")
                                .executes(context -> showAndroidHome(context.getSource()))))
                .then(Commands.literal("commander")
                        .then(Commands.literal("on")
                                .executes(context -> setCommander(
                                        context.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(context -> setCommander(
                                        context.getSource(), false)))
                        .then(Commands.literal("status")
                                .executes(context -> commanderStatus(
                                        context.getSource()))))
                .then(Commands.literal("squad")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> assignSquad(
                                        context.getSource(), StringArgumentType.getString(
                                                context, "name")))
                                .then(Commands.literal("order")
                                        .then(Commands.argument("order", StringArgumentType.word())
                                                .executes(context -> orderSquad(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "name"),
                                                        StringArgumentType.getString(context, "order")))))))
                .then(Commands.literal("repair")
                        .executes(context -> repairNearest(context.getSource())))
                .then(Commands.literal("order")
                        .then(Commands.argument("order", StringArgumentType.word())
                                .executes(context -> orderNearest(
                                        context.getSource(), StringArgumentType.getString(
                                                context, "order"), ""))
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .executes(context -> orderNearest(
                                                context.getSource(), StringArgumentType.getString(
                                                        context, "order"),
                                                StringArgumentType.getString(
                                                        context, "target"))))))
                .then(Commands.literal("capture")
                        .executes(context -> captureNearest(context.getSource(), false)))
                .then(Commands.literal("imprison")
                        .executes(context -> captureNearest(context.getSource(), true)))
                .then(Commands.literal("release")
                        .executes(context -> releaseNearest(context.getSource())))
                .then(Commands.literal("spy")
                        .then(Commands.literal("on")
                                .executes(context -> setNearestSpy(
                                        context.getSource(), true, "")))
                        .then(Commands.literal("off")
                                .executes(context -> setNearestSpy(
                                        context.getSource(), false, "")))
                        .then(Commands.literal("cover")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(context -> setNearestSpy(
                                                context.getSource(), true,
                                                StringArgumentType.getString(
                                                        context, "name"))))))
                .then(Commands.literal("intel")
                        .executes(context -> intelNearest(context.getSource())))
                .then(Commands.literal("speak")
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(context -> speakNearest(context.getSource(),
                                        StringArgumentType.getString(context, "text")))))
                .then(Commands.literal("mode")
                        .then(Commands.literal("combat")
                                .executes(context -> setNearestAircraftRole(
                                        context.getSource(), AircraftRole.COMBAT)))
                        .then(Commands.literal("bomber")
                                .executes(context -> setNearestAircraftRole(
                                        context.getSource(), AircraftRole.BOMBER)))
                        .then(Commands.literal("transport")
                                .executes(context -> setNearestAircraftRole(
                                        context.getSource(), AircraftRole.TRANSPORT)))));
    }

    /** Receives the R-key marker sent by the Android computer. */
    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        String text = event.getRawText().trim();
        if (handleNtglScriptChat(event, text)) {
            return;
        }
        if (handleHelpChat(event, text)) {
            return;
        }
        if (handleCommanderChat(event, text)) {
            return;
        }
        // Keep legacy !jarvis commands, but make hold/return/patrol also
        // update the server-side fire gate.  Lua may be paused or delayed;
        // firing must never depend on its local state alone.
        if (handleJarvisCombatOrder(event, text)) {
            return;
        }
        if (handleAutopilotChat(event, text)) {
            return;
        }
        if (handleOrderChat(event, text)) {
            return;
        }
        if (handleCaptureChat(event, text)) {
            return;
        }
        if (handleSpyChat(event, text)) {
            return;
        }
        if (!RELOAD_CHAT.equalsIgnoreCase(text)) {
            return;
        }

        event.setCanceled(true);
        ServerPlayer player = event.getPlayer();
        ServerLevel level = (ServerLevel) player.level();
        Mob android = level.getEntitiesOfClass(
                        Mob.class, player.getBoundingBox().inflate(8.0D),
                        candidate -> isAndroid(candidate))
                .stream()
                .min(Comparator.comparingDouble(
                        (Mob candidate) -> player.distanceToSqr(candidate)))
                .orElse(null);
        if (android != null) {
            reloadAndroid(android);
        }
    }

    private boolean handleNtglScriptChat(ServerChatEvent event, String text) {
        String[] parts = text.split("\\s+");
        if (parts.length != 4 || !parts[0].equalsIgnoreCase("!androidscompat")
                || !parts[1].equalsIgnoreCase("ntgl")
                || !(parts[2].equalsIgnoreCase("start") || parts[2].equalsIgnoreCase("stop"))) {
            return false;
        }
        event.setCanceled(true);
        ServerLevel level = (ServerLevel) event.getPlayer().level();
        Mob android = androidByUuid(level, parts[3]);
        if (android == null) {
            return true;
        }
        if (parts[2].equalsIgnoreCase("start")) {
            ntglScriptSessions.add(android.getUUID());
        } else {
            ntglScriptSessions.remove(android.getUUID());
            android.setTarget(null);
            removeGunAttackGoal(android);
        }
        return true;
    }

    private boolean handleHelpChat(ServerChatEvent event, String text) {
        if (!HELP_CHAT.equalsIgnoreCase(text) && !"!help".equalsIgnoreCase(text)) {
            return false;
        }
        event.setCanceled(true);
        ServerPlayer player = event.getPlayer();
        String[] lines = {
                "=== ANDROIDS COMPAT: AYUDA ===",
                "!help list | muestra esta ayuda",
                "",
                "TIERRA / JARVIS",
                "!jarvis <all|Android> hold | detener y mantener posicion",
                "!jarvis <all|Android> resume | reanudar patrulla",
                "!jarvis <all|Android> patrol | patrullar y combatir",
                "!jarvis <all|Android> return | volver a la base",
                "!jarvis <all|Android> reload | recargar arma NTGL",
                "!jarvis <all|Android> test | probar deteccion/disparo",
                "!jarvis <all|Android> status | estado, vida, combustible y arma",
                "COMMAND ANDROID",
                "Construccion: Frame + 8 Components + 10 diamantes + Reactor + Android CPU",
                "El Command Android fisico dirige Androids cercanos y tiene max. 25 corazones",
                "",
                "AVIACION",
                "!avion <all|Android> mount | montar y activar rol aereo",
                "!avion <all|Android> patrol | patrulla aerea",
                "!avion <all|Android> follow <jugador> | seguir jugador",
                "!avion <all|Android> return | regresar al punto de salida",
                "!avion <all|Android> hold | mantener posicion",
                "!avion <all|Android> guard | orden de guardia",
                "!avion <all|Android> rescue | acudir a retenidos",
                "!avion <all|Android> logistics | coordinar logistica",
                "!avion <all|Android> capture | capturar objetivo cercano",
                "!avion <all|Android> imprison | encarcelar objetivo cercano",
                "!avion <all|Android> release | liberar retenido",
                "!avion <all|Android> status | estado del avion",
                "",
                "ESPIONAJE",
                "!espia <all|Android> hide | activar modo encubierto",
                "!espia <all|Android> show | desactivar modo encubierto",
                "!espia <all|Android> report | pedir informe de inteligencia",
                "!espia <all|Android> follow <jugador> | infiltrar siguiendo",
                "!espia <all|Android> guard|patrol|return|hold|rescue|logistics",
                "",
                "COMANDOS DEL MOD (requieren permisos de operador)",
                "/androidscompat compat | mods compatibles detectados",
                "/androidscompat stats | nivel, XP, precision y velocidad",
                "/androidscompat reload | recarga del Android cercano",
                "/androidscompat repair | consume hierro: cura y limpia efectos negativos",
                "/androidscompat home set|show|clear | gestionar base del Android cercano",
                "/androidscompat squad <nombre> | asignar escuadra",
                "/androidscompat squad <nombre> order <orden> | mandar escuadra cercana",
                "/androidscompat order <orden> [objetivo] | ordenar Android cercano",
                "/androidscompat order all <orden> [objetivo] | ordenar todos",
                "ordenes: hold, patrol, guard, follow, rescue, logistics, return",
                "/androidscompat mode combat|bomber|transport | asignar avion",
                "/androidscompat mount | montar avion asignado",
                "/androidscompat dismount | desmontar avion",
                "/androidscompat spy on|off | activar/desactivar espia",
                "/androidscompat spy cover <nombre> | nombre de cobertura",
                "/androidscompat intel | informe de inteligencia",
                "/androidscompat speak <texto> | Android cercano lo pronuncia por voice chat",
                "/androidscompat capture | capturar objetivo cercano",
                "/androidscompat imprison | encarcelar objetivo cercano",
                "/androidscompat release | liberar retenido cercano",
                "/androidscompat commander on|off|status | comandante IA de tropas",
                "!androidscompat commander on|off|status | alternativa por chat",
                "R = el script puede solicitar recarga; Q = detener el script Lua"
        };
        for (String line : lines) {
            player.sendSystemMessage(Component.literal(line));
        }
        return true;
    }

    private boolean handleJarvisCombatOrder(ServerChatEvent event, String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^!jarvis\\s+(\\S+)\\s+(hold|return|resume|patrol)(?:\\s+.*)?$",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(text);
        if (!matcher.matches()) {
            return false;
        }

        // Do not cancel the chat: the role Lua still needs to receive it and
        // update its own patrol/return state.  The server gate is applied now.
        ServerPlayer player = event.getPlayer();
        ServerLevel level = (ServerLevel) player.level();
        String requested = matcher.group(1);
        String command = matcher.group(2).toLowerCase(java.util.Locale.ROOT);
        String normalized = "resume".equals(command) ? "patrol" : command;
        List<Mob> targets;
        if ("all".equalsIgnoreCase(requested)) {
            targets = level.getEntitiesOfClass(
                    Mob.class, player.getBoundingBox().inflate(256.0D),
                    AndroidsCompatMod::isAndroid);
        } else {
            Mob android = androidByUuid(level, requested);
            if (android == null) {
                android = nearestAndroid(player, 32.0D);
            }
            targets = android == null ? List.of() : List.of(android);
        }
        for (Mob android : targets) {
            applyGroundOrAircraftOrder(android, normalized, "", player);
        }
        return true;
    }

    private boolean handleCommanderChat(ServerChatEvent event, String text) {
        if (!text.toLowerCase(java.util.Locale.ROOT)
                .matches("!androidscompat\\s+commander\\s+(on|off|status)")) {
            return false;
        }
        event.setCanceled(true);
        String command = text.substring(text.lastIndexOf(' ') + 1)
                .toLowerCase(java.util.Locale.ROOT);
        if ("on".equals(command)) {
            commanderEnabled = true;
        } else if ("off".equals(command)) {
            commanderEnabled = false;
        }
        event.getPlayer().sendSystemMessage(Component.literal(
                "Comandante IA: " + (commanderEnabled ? "ACTIVO" : "INACTIVO")));
        return true;
    }

    /** Called by the independent aircraft Lua roles. */
    private boolean handleAutopilotChat(ServerChatEvent event, String text) {
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        if (!lower.startsWith(AUTOPILOT_CHAT_PREFIX)) {
            return false;
        }

        event.setCanceled(true);
        String[] autopilotParts = text.substring(AUTOPILOT_CHAT_PREFIX.length()).trim()
                .split("\\s+");
        String roleName = autopilotParts.length == 0 ? "" : autopilotParts[0]
                .toLowerCase(java.util.Locale.ROOT);
        AircraftRole role;
        try {
            role = AircraftRole.valueOf(roleName.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException error) {
            event.getPlayer().sendSystemMessage(Component.literal(
                    "Rol aereo desconocido: " + roleName));
            return true;
        }

        ServerPlayer player = event.getPlayer();
        ServerLevel level = (ServerLevel) player.level();
        Mob android = autopilotParts.length > 1
                ? androidByUuid(level, autopilotParts[1]) : null;
        if (android == null) {
            android = nearestAndroid(player, 16.0D);
        }
        if (android == null) {
            player.sendSystemMessage(Component.literal(
                    "Autopiloto: no encuentro un Android libre cerca."));
            return true;
        }
        final Mob controlledAndroid = android;

        if (android.isPassenger() && isAircraft(android.getVehicle())) {
            rememberOwner(android, player);
            aircraftRoles.put(android.getUUID(), role);
            android.getPersistentData().putString(AIR_ROLE_TAG,
                    role.name().toLowerCase(java.util.Locale.ROOT));
            aircraftNavigation.computeIfAbsent(android.getUUID(), key ->
                    new AircraftNavigation(controlledAndroid.getVehicle().position(),
                            level.getGameTime()));
            return true;
        }
        if (android.isPassenger()) {
            player.sendSystemMessage(Component.literal(
                    "Autopiloto: el Android cercano ya esta montado en otro vehiculo."));
            return true;
        }

        Entity aircraft = level.getEntitiesOfClass(
                        Entity.class, android.getBoundingBox().inflate(12.0D),
                        candidate -> isAircraft(candidate)
                                && candidate.getPassengers().isEmpty())
                .stream()
                .min(Comparator.comparingDouble(
                        (Entity candidate) -> controlledAndroid.distanceToSqr(candidate)))
                .orElse(null);
        if (aircraft == null || !android.startRiding(aircraft, true)) {
            player.sendSystemMessage(Component.literal(
                    "Autopiloto: acerca un avion vacio al Android."));
            return true;
        }

        aircraftRoles.put(android.getUUID(), role);
        android.getPersistentData().putString(AIR_ROLE_TAG,
                role.name().toLowerCase(java.util.Locale.ROOT));
        rememberOwner(android, player);
        aircraftNavigation.put(android.getUUID(),
                new AircraftNavigation(aircraft.position(), level.getGameTime()));
        player.sendSystemMessage(Component.literal(
                "Autopiloto " + roleName + ": Android montado y rol activado."));
        LOGGER.info("Aircraft autopilot requested by Lua: Android {} -> {} ({})",
                android.getUUID(), roleName, aircraft.getClass().getName());
        return true;
    }

    /** Receives patrol/follow/return/hold orders from an aircraft Lua role. */
    private boolean handleOrderChat(ServerChatEvent event, String text) {
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        if (!lower.startsWith(ORDER_CHAT_PREFIX)) {
            return false;
        }

        event.setCanceled(true);
        String[] parts = text.substring(ORDER_CHAT_PREFIX.length()).trim()
                .split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return true;
        }

        ServerPlayer player = event.getPlayer();
        ServerLevel level = (ServerLevel) player.level();
        if ("all".equalsIgnoreCase(parts[0])) {
            if (parts.length < 2 || !isKnownOrder(parts[1].toLowerCase(
                    java.util.Locale.ROOT))) {
                player.sendSystemMessage(Component.literal(
                        "Orden desconocida. Usa patrol, hold, return, guard o logistics."));
                return true;
            }
            String command = parts[1].toLowerCase(java.util.Locale.ROOT);
            String targetName = parts.length > 2 ? parts[2] : "";
            int count = 0;
            for (Mob candidate : level.getEntitiesOfClass(
                    Mob.class, player.getBoundingBox().inflate(256.0D),
                    AndroidsCompatMod::isAndroid)) {
                applyGroundOrAircraftOrder(candidate, command, targetName, player);
                count++;
            }
            player.sendSystemMessage(Component.literal(
                    "Orden " + command + " aplicada a " + count + " Androids."));
            return true;
        }
        int commandIndex = 0;
        Mob android = null;
        if (parts.length > 1) {
            android = androidByUuid(level, parts[0]);
            if (android != null) {
                commandIndex = 1;
            }
        }
        if (android == null) {
            android = nearestAndroid(player, 32.0D);
        }
        if (android == null) {
            player.sendSystemMessage(Component.literal(
                    "No hay un Android cerca para recibir la orden."));
            return true;
        }
        final Mob controlledAndroid = android;

        if (commandIndex >= parts.length) {
            return true;
        }
        String command = parts[commandIndex].toLowerCase(java.util.Locale.ROOT);
        String targetName = commandIndex + 1 < parts.length
                ? parts[commandIndex + 1] : "";
        if ("capture".equals(command)) {
            captureNearby(android, false);
            return true;
        }
        if ("imprison".equals(command)) {
            captureNearby(android, true);
            return true;
        }
        if (!isKnownOrder(command)) {
            player.sendSystemMessage(Component.literal(
                    "Orden desconocida. Usa hold, guard, patrol, follow, rescue o logistics."));
            return true;
        }

        if (!android.isPassenger() || !isAircraft(android.getVehicle())) {
            setCombatEnabled(android,
                    !"hold".equals(command) && !"return".equals(command));
            android.getPersistentData().putString(ORDER_TAG, command);
            if (!targetName.isEmpty()) {
                android.getPersistentData().putString(ORDER_TARGET_TAG, targetName);
            } else {
                android.getPersistentData().remove(ORDER_TARGET_TAG);
            }
            rememberOwner(android, player);
            player.sendSystemMessage(Component.literal(
                    "Orden terrestre aplicada: " + command));
            return true;
        }

        AircraftOrder order;
        try {
            order = AircraftOrder.valueOf(command.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException error) {
            // Ground-only orders are valid for Androids but not aircraft.
            player.sendSystemMessage(Component.literal(
                    "Orden no compatible con avion: " + command));
            return true;
        }

        setCombatEnabled(android,
                order != AircraftOrder.HOLD && order != AircraftOrder.RETURN);
        AircraftNavigation navigation = aircraftNavigation.computeIfAbsent(
                android.getUUID(), key -> new AircraftNavigation(
                        controlledAndroid.getVehicle().position(),
                        controlledAndroid.level().getGameTime()));
        navigation.order = order;
        if (order == AircraftOrder.FOLLOW && !targetName.isEmpty()) {
            Player followTarget = android.level().getServer().getPlayerList()
                    .getPlayerByName(targetName);
            navigation.followTarget = followTarget == null
                    ? player.getUUID() : followTarget.getUUID();
        } else if (order != AircraftOrder.FOLLOW) {
            navigation.followTarget = null;
        }
        player.sendSystemMessage(Component.literal(
                "Orden aerea aplicada: " + order.name().toLowerCase(java.util.Locale.ROOT)));
        return true;
    }

    /** Handles capture/release requests sent by an independent Lua role. */
    private boolean handleCaptureChat(ServerChatEvent event, String text) {
        boolean captureRequest = chatCommandMatches(text, CAPTURE_CHAT);
        boolean imprisonRequest = chatCommandMatches(text, IMPRISON_CHAT);
        boolean releaseRequest = chatCommandMatches(text, RELEASE_CHAT);
        if (!captureRequest && !imprisonRequest && !releaseRequest) {
            return false;
        }
        event.setCanceled(true);
        ServerPlayer player = event.getPlayer();
        ServerLevel level = (ServerLevel) player.level();
        String capturePrefix = CAPTURE_CHAT;
        String imprisonPrefix = IMPRISON_CHAT;
        String releasePrefix = RELEASE_CHAT;
        String requestedId = text.length() > capturePrefix.length()
                && text.regionMatches(true, 0, capturePrefix, 0, capturePrefix.length())
                ? text.substring(capturePrefix.length()).trim()
                : text.length() > imprisonPrefix.length()
                && text.regionMatches(true, 0, imprisonPrefix, 0, imprisonPrefix.length())
                ? text.substring(imprisonPrefix.length()).trim()
                : text.length() > releasePrefix.length()
                && text.regionMatches(true, 0, releasePrefix, 0, releasePrefix.length())
                ? text.substring(releasePrefix.length()).trim() : "";
        Mob android = requestedId.isEmpty() ? null : androidByUuid(level, requestedId);
        if (android == null) {
            android = nearestAndroid(player, 20.0D);
        }
        if (captureRequest || imprisonRequest) {
            boolean imprison = imprisonRequest;
            if (android != null && isOwnedBy(android, player)) {
                captureNearby(android, imprison);
            } else {
                LivingEntity target = findCapturable(player, 12.0D);
                if (target == null) {
                    player.sendSystemMessage(Component.literal(
                            "No hay un enemigo o Android enemigo capturable cerca."));
                } else {
                    captureEntity(player, target, imprison);
                    player.sendSystemMessage(Component.literal(
                            (imprison ? "Encarcelado: " : "Capturado: ")
                                    + target.getName().getString()));
                }
            }
        } else {
            if (android != null && isOwnedBy(android, player)) {
                releaseNearby(android);
            } else {
                if (!releaseCapturedByPlayer(player)) {
                    player.sendSystemMessage(Component.literal("No hay retenidos tuyos cerca."));
                }
            }
        }
        return true;
    }

    private static boolean chatCommandMatches(String text, String command) {
        return text.equalsIgnoreCase(command)
                || (text.length() > command.length()
                && text.regionMatches(true, 0, command, 0, command.length())
                && Character.isWhitespace(text.charAt(command.length())));
    }

    private boolean handleSpyChat(ServerChatEvent event, String text) {
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        boolean spyRequest = lower.startsWith(SPY_CHAT_PREFIX)
                && (lower.length() == SPY_CHAT_PREFIX.length()
                || Character.isWhitespace(lower.charAt(SPY_CHAT_PREFIX.length())));
        boolean intelRequest = lower.startsWith(INTEL_CHAT_PREFIX)
                && (lower.length() == INTEL_CHAT_PREFIX.length()
                || Character.isWhitespace(lower.charAt(INTEL_CHAT_PREFIX.length())));
        if (!spyRequest && !intelRequest) {
            return false;
        }

        event.setCanceled(true);
        ServerPlayer player = event.getPlayer();
        ServerLevel level = (ServerLevel) player.level();
        String prefix = spyRequest ? SPY_CHAT_PREFIX : INTEL_CHAT_PREFIX;
        String[] parts = text.substring(prefix.length()).trim().split("\\s+");
        String action = intelRequest ? "intel"
                : (parts.length == 0 ? "" : parts[0].toLowerCase(java.util.Locale.ROOT));
        String requestedId = intelRequest
                ? (parts.length > 0 && isUuidText(parts[0]) ? parts[0] : "")
                : (parts.length > 1 ? parts[1] : "");
        Mob android = requestedId.isEmpty() ? null : androidByUuid(level, requestedId);
        if (android == null) {
            android = nearestAndroid(player, 20.0D);
        }
        if (android == null) {
            player.sendSystemMessage(Component.literal("No hay un Android de espionaje cerca."));
            return true;
        }

        if (intelRequest || "intel".equals(action) || "report".equals(action)) {
            if (!isOwnedBy(android, player)
                    && android.getPersistentData().hasUUID(OWNER_TAG)) {
                player.sendSystemMessage(Component.literal("Ese informe no es accesible."));
            } else {
                collectIntel(player, android);
            }
            return true;
        }
        if (!isOwnedBy(android, player)
                && android.getPersistentData().hasUUID(OWNER_TAG)) {
            player.sendSystemMessage(Component.literal("Ese Android no acepta tu orden."));
            return true;
        }
        if ("off".equals(action) || "stop".equals(action)) {
            disableSpy(android);
            player.sendSystemMessage(Component.literal("Modo espia desactivado."));
        } else {
            String cover = parts.length > 1 && !isUuidText(parts[1]) ? parts[1] : "";
            enableSpy(android, player, cover);
            player.sendSystemMessage(Component.literal(
                    "Modo espia activo" + (cover.isEmpty() ? "" : " | cobertura=" + cover)));
        }
        return true;
    }

    private static boolean isUuidText(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static int setNearestSpy(CommandSourceStack source, boolean enabled,
                                     String cover) {
        Entity caller = source.getEntity();
        if (!(caller instanceof Player player)) {
            source.sendFailure(Component.literal("Solo un jugador puede configurar un espia."));
            return 0;
        }
        Mob android = nearestAndroid(player, 24.0D);
        if (android == null) {
            source.sendFailure(Component.literal("No hay un Android cerca."));
            return 0;
        }
        if (android.getPersistentData().hasUUID(OWNER_TAG)
                && !isOwnedBy(android, player)) {
            source.sendFailure(Component.literal("Ese Android pertenece a otro jugador."));
            return 0;
        }
        if (enabled) {
            enableSpy(android, player, cover);
        } else {
            disableSpy(android);
        }
        source.sendSuccess(() -> Component.literal(
                enabled ? "Modo espia activo." : "Modo espia desactivado."), true);
        return 1;
    }

    private static int intelNearest(CommandSourceStack source) {
        Entity caller = source.getEntity();
        if (!(caller instanceof Player player)) {
            source.sendFailure(Component.literal("Solo un jugador puede pedir inteligencia."));
            return 0;
        }
        Mob android = nearestAndroid(player, 32.0D);
        if (android == null) {
            source.sendFailure(Component.literal("No hay un Android cerca."));
            return 0;
        }
        collectIntel(player, android);
        return 1;
    }

    private static void enableSpy(Mob android, Player owner, String cover) {
        rememberOwner(android, owner);
        android.getPersistentData().putBoolean(SPY_TAG, true);
        if (!cover.isEmpty()) {
            android.getPersistentData().putString(SPY_COVER_TAG, cover);
            if (android.getCustomName() == null) {
                android.setCustomName(Component.literal(cover));
                android.getPersistentData().putBoolean(SPY_COVER_SET_TAG, true);
            }
        }
        android.setTarget(null);
        android.getNavigation().stop();
    }

    private static void disableSpy(Mob android) {
        android.getPersistentData().remove(SPY_TAG);
        android.getPersistentData().remove(SPY_COVER_TAG);
        if (android.getPersistentData().getBoolean(SPY_COVER_SET_TAG)) {
            android.setCustomName(null);
        }
        android.getPersistentData().remove(SPY_COVER_SET_TAG);
        android.setTarget(null);
        android.getNavigation().stop();
    }

    private static boolean isSpy(Mob android) {
        return android.getPersistentData().getBoolean(SPY_TAG);
    }

    private static boolean combatEnabled(Mob android) {
        // Old Androids have no tag and remain active by default.
        if (!android.getPersistentData().contains(COMBAT_ENABLED_TAG)) {
            return true;
        }
        return android.getPersistentData().getBoolean(COMBAT_ENABLED_TAG);
    }

    private static void setCombatEnabled(Mob android, boolean enabled) {
        android.getPersistentData().putBoolean(COMBAT_ENABLED_TAG, enabled);
        if (!enabled) {
            android.setTarget(null);
            android.getNavigation().stop();
        }
    }

    private static boolean combatBlocked(Mob android) {
        return !combatEnabled(android)
                || isSpy(android)
                || android.getPersistentData().hasUUID(CAPTURED_BY_TAG)
                || android.getPersistentData().getBoolean(IMPRISONED_TAG)
                || android.getHealth() <= 6.0F;
    }

    private static void concealSpy(Mob spy) {
        if (!(spy.level() instanceof ServerLevel level)) {
            return;
        }
        level.getEntitiesOfClass(Mob.class, spy.getBoundingBox().inflate(24.0D),
                candidate -> candidate instanceof Enemy && candidate.getTarget() == spy)
                .forEach(candidate -> candidate.setTarget(null));
    }

    private static void collectIntel(Player player, Mob spy) {
        ServerLevel level = (ServerLevel) player.level();
        double range = 64.0D;
        List<LivingEntity> living = level.getEntitiesOfClass(
                LivingEntity.class, spy.getBoundingBox().inflate(range),
                target -> target != spy && target.isAlive());
        long players = living.stream().filter(entity -> entity instanceof Player).count();
        long enemies = living.stream().filter(entity -> entity instanceof Enemy).count();
        long androids = living.stream().filter(AndroidsCompatMod::isAndroid).count();
        long aircraft = level.getEntitiesOfClass(
                Entity.class, spy.getBoundingBox().inflate(range),
                entity -> entity != spy && isAircraft(entity)).size();
        String cover = spy.getPersistentData().getString(SPY_COVER_TAG);
        player.sendSystemMessage(Component.literal(
                "INTEL | espia=" + spy.getName().getString()
                        + " | cobertura=" + (cover.isEmpty() ? "ninguna" : cover)
                        + " | jugadores=" + players
                        + " | enemigos=" + enemies
                        + " | androids=" + androids
                        + " | aviones=" + aircraft));
        living.stream()
                .filter(entity -> entity instanceof Player || entity instanceof Enemy)
                .sorted(Comparator.comparingDouble(spy::distanceToSqr))
                .limit(8)
                .forEach(entity -> player.sendSystemMessage(Component.literal(
                        "  contacto " + entity.getName().getString()
                                + " | distancia=" + String.format(java.util.Locale.ROOT,
                                "%.1f", Math.sqrt(spy.distanceToSqr(entity))))));
    }

    private static boolean isKnownOrder(String command) {
        return "hold".equals(command) || "patrol".equals(command)
                || "guard".equals(command) || "follow".equals(command)
                || "rescue".equals(command) || "logistics".equals(command)
                || "return".equals(command);
    }

    private static void rememberOwner(Mob android, Player owner) {
        if (!android.getPersistentData().hasUUID(OWNER_TAG)) {
            android.getPersistentData().putUUID(OWNER_TAG, owner.getUUID());
        }
    }

    private static boolean isOwnedBy(Mob android, Player player) {
        return android.getPersistentData().hasUUID(OWNER_TAG)
                && player.getUUID().equals(android.getPersistentData().getUUID(OWNER_TAG));
    }

    private static int assignSquad(CommandSourceStack source, String name) {
        Entity caller = source.getEntity();
        if (!(caller instanceof Player player)) {
            source.sendFailure(Component.literal("Solo un jugador puede asignar escuadras."));
            return 0;
        }
        Mob android = nearestAndroid(player, 24.0D);
        if (android == null) {
            source.sendFailure(Component.literal("No hay un Android cercano."));
            return 0;
        }
        String squad = name.trim().toLowerCase(java.util.Locale.ROOT);
        android.getPersistentData().putString(SQUAD_TAG, squad);
        rememberOwner(android, player);
        source.sendSuccess(() -> Component.literal(
                "Escuadra " + squad + " asignada a " + android.getName().getString()), true);
        return 1;
    }

    private static int orderSquad(CommandSourceStack source, String name, String requested) {
        Entity caller = source.getEntity();
        if (!(caller instanceof Player player)) {
            source.sendFailure(Component.literal("Solo un jugador puede dar ordenes."));
            return 0;
        }
        String squad = name.trim().toLowerCase(java.util.Locale.ROOT);
        String order = requested.toLowerCase(java.util.Locale.ROOT);
        if (!isKnownOrder(order)) {
            source.sendFailure(Component.literal(
                    "Orden no valida. Usa hold, patrol, guard, follow, rescue, logistics o return."));
            return 0;
        }
        List<Mob> members = nearbyAndroids(player, 256.0D).stream()
                .filter(android -> squad.equals(android.getPersistentData()
                        .getString(SQUAD_TAG).toLowerCase(java.util.Locale.ROOT)))
                .toList();
        for (Mob android : members) {
            applyGroundOrAircraftOrder(android, order,
                    "follow".equals(order) ? player.getName().getString() : "", player);
        }
        source.sendSuccess(() -> Component.literal(members.isEmpty()
                ? "No hay miembros cercanos en la escuadra " + squad + "."
                : "Orden " + order + " aplicada a " + members.size()
                        + " miembros de " + squad + "."), true);
        return members.size();
    }

    private static int setAndroidHome(CommandSourceStack source) {
        Entity caller = source.getEntity();
        if (!(caller instanceof Player player)) {
            source.sendFailure(Component.literal("Solo un jugador puede fijar una base."));
            return 0;
        }
        Mob android = nearestAndroid(player, 32.0D);
        if (android == null) {
            source.sendFailure(Component.literal("No hay un Android cercano."));
            return 0;
        }
        var data = android.getPersistentData();
        data.putDouble(GROUND_HOME_X_TAG, android.getX());
        data.putDouble(GROUND_HOME_Y_TAG, android.getY());
        data.putDouble(GROUND_HOME_Z_TAG, android.getZ());
        rememberOwner(android, player);
        source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                "Base fijada para %s en %.1f, %.1f, %.1f.",
                android.getName().getString(), android.getX(), android.getY(), android.getZ())), true);
        return 1;
    }

    private static int clearAndroidHome(CommandSourceStack source) {
        Entity caller = source.getEntity();
        if (!(caller instanceof Player player)) {
            source.sendFailure(Component.literal("Solo un jugador puede borrar una base."));
            return 0;
        }
        Mob android = nearestAndroid(player, 32.0D);
        if (android == null) {
            source.sendFailure(Component.literal("No hay un Android cercano."));
            return 0;
        }
        var data = android.getPersistentData();
        data.remove(GROUND_HOME_X_TAG);
        data.remove(GROUND_HOME_Y_TAG);
        data.remove(GROUND_HOME_Z_TAG);
        rememberOwner(android, player);
        source.sendSuccess(() -> Component.literal(
                "Base borrada; se registrara la posicion actual cuando vuelva a patrullar."), true);
        return 1;
    }

    private static int showAndroidHome(CommandSourceStack source) {
        Entity caller = source.getEntity();
        if (!(caller instanceof Player player)) {
            source.sendFailure(Component.literal("Solo un jugador puede consultar una base."));
            return 0;
        }
        Mob android = nearestAndroid(player, 32.0D);
        if (android == null) {
            source.sendFailure(Component.literal("No hay un Android cercano."));
            return 0;
        }
        var data = android.getPersistentData();
        if (!data.contains(GROUND_HOME_X_TAG)) {
            source.sendSuccess(() -> Component.literal(
                    android.getName().getString() + " no tiene una base guardada."), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                "Base de %s: %.1f, %.1f, %.1f.", android.getName().getString(),
                data.getDouble(GROUND_HOME_X_TAG), data.getDouble(GROUND_HOME_Y_TAG),
                data.getDouble(GROUND_HOME_Z_TAG))), false);
        return 1;
    }

    private static int repairNearest(CommandSourceStack source) {
        Entity caller = source.getEntity();
        if (!(caller instanceof Player player)) {
            source.sendFailure(Component.literal("Solo un jugador puede reparar Androids."));
            return 0;
        }
        Mob android = nearestAndroid(player, 8.0D);
        if (android == null) {
            source.sendFailure(Component.literal("No hay un Android a 8 bloques."));
            return 0;
        }
        if (android.getHealth() >= android.getMaxHealth()) {
            source.sendFailure(Component.literal("Ese Android ya tiene toda la vida."));
            return 0;
        }
        int ironSlot = -1;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(Items.IRON_INGOT)) {
                ironSlot = slot;
                break;
            }
        }
        if (ironSlot < 0) {
            source.sendFailure(Component.literal("Necesitas un lingote de hierro para repararlo."));
            return 0;
        }
        player.getInventory().getItem(ironSlot).shrink(1);
        android.heal(Math.min(10.0F, android.getMaxHealth() - android.getHealth()));
        for (MobEffectInstance effect : new java.util.ArrayList<>(android.getActiveEffects())) {
            if (!effect.getEffect().isBeneficial()) {
                android.removeEffect(effect.getEffect());
            }
        }
        source.sendSuccess(() -> Component.literal(
                "Reparaste a " + android.getName().getString()
                        + " con un lingote: hasta +10 de vida y limpieza de efectos negativos."), true);
        return 1;
    }

    private static int orderNearest(CommandSourceStack source,
                                    String requested, String target) {
        Entity caller = source.getEntity();
        if (!(caller instanceof Player player)) {
            source.sendFailure(Component.literal("Solo un jugador puede dar ordenes."));
            return 0;
        }
        String command = requested.toLowerCase(java.util.Locale.ROOT);
        if ("all".equals(command)) {
            command = target.toLowerCase(java.util.Locale.ROOT);
            target = "";
            if (!isKnownOrder(command)) {
                source.sendFailure(Component.literal("Orden desconocida."));
                return 0;
            }
            int count = 0;
            for (Mob android : nearbyAndroids(player, 48.0D)) {
                applyGroundOrAircraftOrder(android, command, target, player);
                count++;
            }
            final String appliedOrder = command;
            final int appliedCount = count;
            source.sendSuccess(() -> Component.literal(
                    "Orden " + appliedOrder + " aplicada a " + appliedCount + " Androids."), true);
            return count;
        }
        if (!isKnownOrder(command)) {
            source.sendFailure(Component.literal(
                    "Orden desconocida. Usa hold, guard, patrol, follow, rescue o logistics."));
            return 0;
        }
        Mob android = nearestAndroid(player, 32.0D);
        if (android == null) {
            source.sendFailure(Component.literal("No hay un Android cercano."));
            return 0;
        }
        applyGroundOrAircraftOrder(android, command, target, player);
        final String appliedOrder = command;
        source.sendSuccess(() -> Component.literal(
                "Orden " + appliedOrder + " aplicada a " + android.getName().getString()), true);
        return 1;
    }

    private static List<Mob> nearbyAndroids(Player player, double range) {
        return ((ServerLevel) player.level()).getEntitiesOfClass(
                Mob.class, player.getBoundingBox().inflate(range),
                AndroidsCompatMod::isAndroid);
    }

    private static void applyGroundOrAircraftOrder(Mob android, String command,
                                                   String target, Player issuer) {
        if ("follow".equalsIgnoreCase(command) && target.isEmpty()) {
            target = issuer.getName().getString();
        }
        setCombatEnabled(android,
                !"hold".equalsIgnoreCase(command)
                        && !"return".equalsIgnoreCase(command));
        if (android.isPassenger() && isAircraft(android.getVehicle())) {
            try {
                AircraftOrder aircraftOrder = AircraftOrder.valueOf(
                        command.toUpperCase(java.util.Locale.ROOT));
                AircraftNavigation navigation = aircraftNavigation.computeIfAbsent(
                        android.getUUID(), key -> new AircraftNavigation(
                                android.getVehicle().position(), android.level().getGameTime()));
                navigation.order = aircraftOrder;
                if (aircraftOrder == AircraftOrder.FOLLOW && !target.isEmpty()) {
                    Player follow = android.level().getServer().getPlayerList()
                            .getPlayerByName(target);
                    navigation.followTarget = follow == null ? issuer.getUUID() : follow.getUUID();
                } else if (aircraftOrder != AircraftOrder.FOLLOW) {
                    navigation.followTarget = null;
                }
                rememberOwner(android, issuer);
                return;
            } catch (IllegalArgumentException ignored) {
                // guard/rescue/logistics are ground coordination orders.
            }
        }

        android.getPersistentData().putString(ORDER_TAG, command);
        if (!target.isEmpty()) {
            android.getPersistentData().putString(ORDER_TARGET_TAG, target);
        } else {
            android.getPersistentData().remove(ORDER_TARGET_TAG);
        }
        rememberOwner(android, issuer);
    }

    private static void applyGroundOrder(Mob android) {
        String order = android.getPersistentData().getString(ORDER_TAG)
                .toLowerCase(java.util.Locale.ROOT);
        if (order.isEmpty() || (android.isPassenger() && isAircraft(android.getVehicle()))) {
            return;
        }
        if ("hold".equals(order)) {
            android.setTarget(null);
            android.getNavigation().stop();
            return;
        }
        if ("return".equals(order)) {
            rememberGroundHome(android);
            var data = android.getPersistentData();
            android.setTarget(null);
            android.getNavigation().moveTo(
                    data.getDouble(GROUND_HOME_X_TAG),
                    data.getDouble(GROUND_HOME_Y_TAG),
                    data.getDouble(GROUND_HOME_Z_TAG), 1.1D);
            return;
        }
        ServerLevel level = (ServerLevel) android.level();
        if ("follow".equals(order)) {
            String targetName = android.getPersistentData().getString(ORDER_TARGET_TAG);
            Player target = targetName.isEmpty() ? null
                    : level.getServer().getPlayerList().getPlayerByName(targetName);
            if (target != null) {
                android.getNavigation().moveTo(target, 1.05D);
                android.setTarget(null);
            }
        } else if ("rescue".equals(order)) {
            LivingEntity prisoner = level.getEntitiesOfClass(
                            LivingEntity.class, android.getBoundingBox().inflate(48.0D),
                            candidate -> candidate != android
                                    && candidate.getPersistentData().hasUUID(CAPTURED_BY_TAG))
                    .stream().min(Comparator.comparingDouble(android::distanceToSqr)).orElse(null);
            if (prisoner != null) {
                android.getNavigation().moveTo(prisoner, 1.1D);
                android.setTarget(null);
            }
        } else if ("logistics".equals(order)) {
            Mob transport = level.getEntitiesOfClass(
                            Mob.class, android.getBoundingBox().inflate(96.0D),
                            candidate -> isAndroid(candidate)
                                    && "transport".equals(candidate.getPersistentData()
                                            .getString("androidscompat_air_role")))
                    .stream().min(Comparator.comparingDouble(android::distanceToSqr)).orElse(null);
            if (transport != null) {
                android.getNavigation().moveTo(transport, 1.0D);
                android.setTarget(null);
            }
        }
    }

    private static int captureNearest(CommandSourceStack source, boolean imprison) {
        Entity caller = source.getEntity();
        if (!(caller instanceof Player player)) {
            source.sendFailure(Component.literal("Solo un jugador puede capturar."));
            return 0;
        }
        LivingEntity target = findCapturable(player, 12.0D);
        if (target == null) {
            source.sendFailure(Component.literal(
                    "No hay un enemigo, Android o jugador capturable cerca."));
            return 0;
        }
        captureEntity(player, target, imprison);
        source.sendSuccess(() -> Component.literal(
                (imprison ? "Encarcelado: " : "Capturado: ")
                        + target.getName().getString()), true);
        return 1;
    }

    private static int releaseNearest(CommandSourceStack source) {
        Entity caller = source.getEntity();
        if (!(caller instanceof Player player)) {
            source.sendFailure(Component.literal("Solo un jugador puede liberar."));
            return 0;
        }
        LivingEntity target = findCapturedByPlayer(player);
        if (target == null) {
            source.sendFailure(Component.literal("No hay retenidos tuyos cerca."));
            return 0;
        }
        releaseEntity(target);
        source.sendSuccess(() -> Component.literal(
                "Liberado: " + target.getName().getString()), true);
        return 1;
    }

    private static boolean releaseCapturedByPlayer(Player player) {
        LivingEntity target = findCapturedByPlayer(player);
        if (target == null) {
            return false;
        }
        releaseEntity(target);
        player.sendSystemMessage(Component.literal(
                "Liberado: " + target.getName().getString()));
        return true;
    }

    private static LivingEntity findCapturedByPlayer(Player player) {
        ServerLevel level = (ServerLevel) player.level();
        return level.getEntitiesOfClass(
                        LivingEntity.class, player.getBoundingBox().inflate(32.0D),
                        candidate -> isCapturedByPlayerOrOwnedAndroid(candidate, player))
                .stream()
                .min(Comparator.comparingDouble(player::distanceToSqr))
                .orElse(null);
    }

    private static void captureNearby(LivingEntity captor, boolean imprison) {
        LivingEntity target = findCapturable(captor, 10.0D);
        if (target == null) {
            LOGGER.debug("No capturable target near {}", captor.getUUID());
            return;
        }
        captureEntity(captor, target, imprison);
        LOGGER.info("{} captured {}{}", captor.getUUID(), target.getUUID(),
                imprison ? " and imprisoned" : "");
    }

    private static void releaseNearby(LivingEntity captor) {
        if (!(captor.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity target = level.getEntitiesOfClass(
                        LivingEntity.class, captor.getBoundingBox().inflate(24.0D),
                        candidate -> candidate.getPersistentData().hasUUID(CAPTURED_BY_TAG)
                                && captor.getUUID().equals(candidate.getPersistentData()
                                .getUUID(CAPTURED_BY_TAG)))
                .stream()
                .min(Comparator.comparingDouble(captor::distanceToSqr))
                .orElse(null);
        if (target != null) {
            releaseEntity(target);
            LOGGER.info("{} released {}", captor.getUUID(), target.getUUID());
        }
    }

    private static LivingEntity findCapturable(LivingEntity captor, double range) {
        if (!(captor.level() instanceof ServerLevel level)) {
            return null;
        }
        return level.getEntitiesOfClass(
                        LivingEntity.class, captor.getBoundingBox().inflate(range),
                        candidate -> candidate != captor
                                && candidate.isAlive()
                                && isCapturableType(candidate)
                                && !candidate.getPersistentData().hasUUID(CAPTURED_BY_TAG)
                                && !isFriendlyTo(captor, candidate))
                .stream()
                .min(Comparator.comparingDouble(captor::distanceToSqr))
                .orElse(null);
    }

    private static boolean isCapturableType(LivingEntity target) {
        return target instanceof Player || isAndroid(target) || target instanceof Enemy;
    }

    private static boolean isFriendlyTo(LivingEntity captor, LivingEntity target) {
        if (target instanceof Player player
                && captor.getPersistentData().hasUUID(OWNER_TAG)
                && player.getUUID().equals(captor.getPersistentData().getUUID(OWNER_TAG))) {
            return true;
        }
        if (isAndroid(target) && captor.getPersistentData().hasUUID(OWNER_TAG)
                && target.getPersistentData().hasUUID(OWNER_TAG)
                && captor.getPersistentData().getUUID(OWNER_TAG)
                .equals(target.getPersistentData().getUUID(OWNER_TAG))) {
            return true;
        }
        String captorSquad = captor.getPersistentData().getString(SQUAD_TAG);
        String targetSquad = target.getPersistentData().getString(SQUAD_TAG);
        return !captorSquad.isEmpty() && captorSquad.equals(targetSquad);
    }

    private static boolean isCapturedByPlayerOrOwnedAndroid(LivingEntity target,
                                                              Player player) {
        if (!target.getPersistentData().hasUUID(CAPTURED_BY_TAG)) {
            return false;
        }
        UUID captorId = target.getPersistentData().getUUID(CAPTURED_BY_TAG);
        if (player.getUUID().equals(captorId)) {
            return true;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        Entity captor = level.getEntity(captorId);
        return captor instanceof Mob mob
                && mob.getPersistentData().hasUUID(OWNER_TAG)
                && player.getUUID().equals(mob.getPersistentData().getUUID(OWNER_TAG));
    }

    private static void captureEntity(LivingEntity captor, LivingEntity target,
                                      boolean imprison) {
        if (target == captor) {
            return;
        }
        if (target.isPassenger()) {
            target.stopRiding();
        }
        target.getPersistentData().putUUID(CAPTURED_BY_TAG, captor.getUUID());
        target.getPersistentData().putBoolean(IMPRISONED_TAG, imprison);
        if (imprison) {
            target.getPersistentData().putDouble(PRISON_X_TAG, captor.getX());
            target.getPersistentData().putDouble(PRISON_Y_TAG, captor.getY() + 1.0D);
            target.getPersistentData().putDouble(PRISON_Z_TAG, captor.getZ());
        } else {
            target.getPersistentData().remove(PRISON_X_TAG);
            target.getPersistentData().remove(PRISON_Y_TAG);
            target.getPersistentData().remove(PRISON_Z_TAG);
        }
        target.setNoGravity(true);
        target.setDeltaMovement(Vec3.ZERO);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                40, 255, false, false));
        target.addEffect(new MobEffectInstance(MobEffects.JUMP,
                40, 250, false, false));
        if (target instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
    }

    private static void enforceDetention(LivingEntity target) {
        if (!target.getPersistentData().hasUUID(CAPTURED_BY_TAG)) {
            return;
        }
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        Entity captor = level.getEntity(target.getPersistentData().getUUID(CAPTURED_BY_TAG));
        if (!(captor instanceof LivingEntity livingCaptor) || !livingCaptor.isAlive()) {
            releaseEntity(target);
            return;
        }
        if (target.isPassenger()) {
            target.stopRiding();
        }
        target.setNoGravity(true);
        if (target.getPersistentData().getBoolean(IMPRISONED_TAG)
                && target.getPersistentData().contains(PRISON_X_TAG)) {
            target.setPos(target.getPersistentData().getDouble(PRISON_X_TAG),
                    target.getPersistentData().getDouble(PRISON_Y_TAG),
                    target.getPersistentData().getDouble(PRISON_Z_TAG));
        } else {
            target.setPos(livingCaptor.getX(), livingCaptor.getY() + 1.0D,
                    livingCaptor.getZ());
        }
        target.setDeltaMovement(Vec3.ZERO);
        if (target.tickCount % 10 == 0) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    40, 255, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.JUMP,
                    40, 250, false, false));
        }
        if (target instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
    }

    private static void releaseEntity(LivingEntity target) {
        target.getPersistentData().remove(CAPTURED_BY_TAG);
        target.getPersistentData().remove(IMPRISONED_TAG);
        target.getPersistentData().remove(PRISON_X_TAG);
        target.getPersistentData().remove(PRISON_Y_TAG);
        target.getPersistentData().remove(PRISON_Z_TAG);
        target.setNoGravity(false);
        target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        target.removeEffect(MobEffects.JUMP);
    }

    /**
     * Looks at the next one or two blocks before allowing a ground Android to
     * continue. A small step is allowed; a void or a drop greater than the
     * configured limit stops navigation and briefly holds the Android at the
     * edge. This is server-side, so it protects every Lua role equally.
     */
    private static boolean safetyEdgeGuard(Mob android) {
        if (android.isNoGravity() || !android.onGround()) {
            return false;
        }

        UUID id = android.getUUID();
        int hold = edgeHoldTicks.getOrDefault(id, 0);
        if (hold > 0) {
            edgeHoldTicks.put(id, hold - 1);
            stopAtEdge(android);
            return true;
        }

        if (!(android.level() instanceof ServerLevel level)) {
            return false;
        }
        int currentDepth = supportDepth(level, android.getX(), android.getY(), android.getZ());
        Vec3 motion = android.getDeltaMovement();
        double horizontalSpeed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        Vec3 direction = horizontalSpeed > 0.02D
                ? new Vec3(motion.x, 0.0D, motion.z).normalize()
                : new Vec3(android.getLookAngle().x, 0.0D, android.getLookAngle().z);
        if (direction.lengthSqr() < 0.0001D) {
            return false;
        }
        direction = direction.normalize();

        double width = Math.max(0.65D, android.getBbWidth());
        double[] lookAhead = {width * 0.9D, width * 1.6D};
        for (double distance : lookAhead) {
            double x = android.getX() + direction.x * distance;
            double z = android.getZ() + direction.z * distance;
            int nextDepth = supportDepth(level, x, android.getY(), z);
            boolean voidOrDrop = nextDepth > MAX_SAFE_DROP
                    && (currentDepth <= 1 || nextDepth > currentDepth + MAX_SAFE_DROP);
            if (voidOrDrop) {
                edgeHoldTicks.put(id, EDGE_HOLD_TICKS);
                stopAtEdge(android);
                LOGGER.debug("Safety stop for Android {}: ground drop {} blocks",
                        id, nextDepth > SUPPORT_SCAN_DEPTH ? "void" : nextDepth);
                return true;
            }
        }
        return false;
    }

    private static int supportDepth(ServerLevel level, double x, double y, double z) {
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        int startY = Mth.floor(y) - 1;
        for (int depth = 0; depth <= SUPPORT_SCAN_DEPTH; depth++) {
            int blockY = startY - depth;
            if (blockY < level.getMinBuildHeight()) {
                return SUPPORT_SCAN_DEPTH + 1;
            }
            BlockPos position = new BlockPos(blockX, blockY, blockZ);
            if (!level.getBlockState(position).getCollisionShape(level, position).isEmpty()) {
                return depth;
            }
        }
        return SUPPORT_SCAN_DEPTH + 1;
    }

    private static void stopAtEdge(Mob android) {
        android.getNavigation().stop();
        android.setTarget(null);
        Vec3 velocity = android.getDeltaMovement();
        android.setDeltaMovement(0.0D, Math.min(0.0D, velocity.y), 0.0D);
    }

    @SubscribeEvent
    public void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity living = event.getEntity();
        if (!(living.level() instanceof ServerLevel)) {
            return;
        }
        enforceDetention(living);
        if (!(living instanceof Mob mob)) {
            return;
        }
        boolean factionAircraftPilot = mob.getPersistentData().getBoolean(PuenteNorteRaider.FACTION_TAG)
                && mob.getPersistentData().getBoolean(PN_AIR_PILOT_TAG);
        boolean villageAircraftPilot = mob instanceof VillageDefender
                && mob.getPersistentData().getBoolean(VILLAGE_AIR_PILOT_TAG);
        if (!isAndroid(mob) && !factionAircraftPilot && !villageAircraftPilot) {
            if (mob instanceof VillageDefender && mob.tickCount % 20 == 0) {
                ensureFactionGunGoal(mob);
            }
            return;
        }

        if (factionAircraftPilot || villageAircraftPilot) {
            controlAircraft(mob);
            if (mob.isPassenger() && isAircraft(mob.getVehicle())) {
                mob.setTarget(null);
                return;
            }
            if (factionAircraftPilot) mob.getPersistentData().putBoolean(PN_AIR_PILOT_TAG, false);
            if (villageAircraftPilot) mob.getPersistentData().putBoolean(VILLAGE_AIR_PILOT_TAG, false);
            return;
        }

        // NTGL can install its own goal as soon as an IWeapon is equipped.
        // Keep it detached until a Lua role explicitly enables this Android.
        if (!ntglScriptSessions.contains(mob.getUUID())) {
            mob.setTarget(null);
            removeGunAttackGoal(mob);
        }

        // The Command Android is a real, mortal entity from CC:Androids.
        // It is the physical source of the squad commander AI.
        if (isCommandAndroid(mob)) {
            mob.setInvulnerable(false);
            mob.getPersistentData().putBoolean(COMMANDER_TAG, commanderEnabled);
        }

        // Aircraft control must run every passenger tick.  Immersive Aircraft
        // clears inputs when the passenger is not a Player, so doing this only
        // every few ticks makes the plane lose control and dive.
        controlAircraft(mob);

        if (!mob.isPassenger() && safetyEdgeGuard(mob)) {
            return;
        }

        // A ground role must never fire while it is sitting in an aircraft.
        // Aircraft roles are handled exclusively by controlAircraft().
        if (mob.isPassenger() && isAircraft(mob.getVehicle())) {
            mob.setTarget(null);
            return;
        }

        if (isSpy(mob)) {
            concealSpy(mob);
            mob.setTarget(null);
            mob.getNavigation().stop();
            return;
        }

        // This gate must run every tick, not only on the slower equipment/
        // targeting interval. Otherwise NTGL can reacquire and fire during
        // the few ticks between two order checks.
        applyGroundOrder(mob);
        if (combatBlocked(mob)) {
            mob.setTarget(null);
            mob.getNavigation().stop();
            return;
        }

        if (mob.tickCount % CHECK_EVERY_TICKS != 0) {
            return;
        }

        updateProgression(mob);
        equipArmorAndBackpack(mob);
        syncBackpackSupplies(mob);
        equipArmorAndBackpack(mob);

        moveNtglWeaponToMainHand(mob);
        ItemStack weapon = mob.getMainHandItem();
        if (isNtglWeapon(weapon)) {
            if (!ntglScriptSessions.contains(mob.getUUID())) {
                mob.setTarget(null);
                removeGunAttackGoal(mob);
                return;
            }
            LivingEntity current = mob.getTarget();
            if (!isValidTarget(mob, current)) {
                mob.setTarget(findNearestEnemy(mob));
            }
            ensureGunAttackGoal(mob);
            ensureAutomaticReload(mob, weapon);
            directGroundShot(mob, mob.getTarget());
        }

    }

    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (event.getEntity() instanceof Mob target && isSpy(target)
                && attacker instanceof Enemy) {
            event.setCanceled(true);
            return;
        }
        if (attacker instanceof Mob android && isAndroid(android)
                && event.getEntity() != android) {
            grantExperience(android, Math.max(1L,
                    Math.round(event.getAmount() * 2.0F)));
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof Mob android && isAndroid(android)
                && event.getEntity() != android) {
            grantExperience(android, 50L
                    + Math.max(1L, Math.round(event.getEntity().getMaxHealth())) * 5L);
        }
    }

    /** The compatibility AI commander does not leave its command block behind. */
    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        Entity entity = event.getEntity();
        if (!commanderEnabled
                || !(entity instanceof Mob mob)
                || !isAndroid(mob)
                || !mob.getPersistentData().getBoolean(COMMANDER_TAG)) {
            return;
        }
        event.getDrops().removeIf(drop -> drop.getItem().is(Items.COMMAND_BLOCK));
    }

    /**
     * Drives and arms an Immersive Aircraft when an Android is its first
     * passenger. This is deliberately reflective: the compatibility jar can
     * still load when Immersive Aircraft is not installed.
     */
    private static void controlAircraft(Mob android) {
        Entity vehicle = android.getVehicle();
        if (vehicle == null || !isAircraft(vehicle)
                || !vehicle.getPassengers().contains(android)) {
            // Do not leave an aircraft role attached after dismounting. If
            // this Android is later used as a fusilero or guard, mounting it
            // manually must not resurrect the old autopilot state.
            aircraftRoles.remove(android.getUUID());
            aircraftNavigation.remove(android.getUUID());
            aircraftControlLogged.remove(android.getUUID());
            android.getPersistentData().remove(AIR_ROLE_TAG);
            if (android.getPersistentData().getBoolean(PuenteNorteRaider.FACTION_TAG)) {
                android.getPersistentData().putBoolean(PN_AIR_PILOT_TAG, false);
            }
            android.getPersistentData().putBoolean(VILLAGE_AIR_PILOT_TAG, false);
            android.getPersistentData().remove(VILLAGE_ROUTE_ORIGIN_X);
            android.getPersistentData().remove(VILLAGE_ROUTE_ORIGIN_Y);
            android.getPersistentData().remove(VILLAGE_ROUTE_ORIGIN_Z);
            android.getPersistentData().remove(VILLAGE_ROUTE_TARGET_X);
            android.getPersistentData().remove(VILLAGE_ROUTE_TARGET_Y);
            android.getPersistentData().remove(VILLAGE_ROUTE_TARGET_Z);
            android.getPersistentData().remove(VILLAGE_ROUTE_RETURNING);
            return;
        }

        // Never infer an aircraft role from the fact that an Android is a
        // passenger.  Fusileros, guards and other ground roles can be mounted
        // accidentally; they must not receive flight controls or aircraft
        // weapons.  A role is assigned only by rol_avion/piloto/bombardero/
        // transportador or by the explicit server command.
        AircraftRole role = aircraftRoles.get(android.getUUID());
        if (role == null) {
            String stored = android.getPersistentData().getString(AIR_ROLE_TAG);
            try {
                role = AircraftRole.valueOf(stored.toUpperCase(java.util.Locale.ROOT));
                aircraftRoles.put(android.getUUID(), role);
            } catch (IllegalArgumentException ignored) {
                // No aircraft role: leave this passenger completely passive.
                return;
            }
        }

        // Immersive Aircraft is also the superclass of Man of Many Planes
        // aircraft.  Do not require a specific concrete plane class here.
        if (aircraftControlLogged.putIfAbsent(android.getUUID(), Boolean.TRUE) == null) {
            LOGGER.info("Aircraft autopilot active for Android {} in {} (passengers={})",
                    android.getUUID(), vehicle.getClass().getName(),
                    vehicle.getPassengers().size());
        }

        try {
            long tick = vehicle.level().getGameTime();
            AircraftNavigation navigation = aircraftNavigation.computeIfAbsent(
                    android.getUUID(), key -> new AircraftNavigation(
                            vehicle.position(), tick));
            boolean factionPilot = android.getPersistentData().getBoolean(PN_AIR_PILOT_TAG);
                boolean villagePilot = android.getPersistentData().getBoolean(VILLAGE_AIR_PILOT_TAG);
            Entity targetAircraft = role == AircraftRole.COMBAT
                    ? (factionPilot ? findPlayerAircraft(vehicle)
                    : villagePilot ? findPuenteNorteAircraft(vehicle) : findEnemyAircraft(vehicle)) : null;
            LivingEntity groundTarget = role == AircraftRole.TRANSPORT
                    ? null
                    : (targetAircraft == null ? (factionPilot
                    ? findFactionAirTarget(vehicle, android)
                    : villagePilot ? findVillageAirTarget(vehicle, android) : findGroundTarget(vehicle)) : null);
            Entity followTarget = navigation.order == AircraftOrder.FOLLOW
                    ? findFollowTarget(android, navigation) : null;
            Entity target = followTarget != null ? followTarget
                    : (targetAircraft != null ? targetAircraft : groundTarget);

            boolean takingOff = tick - navigation.started < 100L
                    || (vehicle.onGround() && vehicle.getY() < navigation.home.y + 8.0D);
            Vector3f direction;
            Vector3f weaponDirection = null;
            if (takingOff) {
                direction = takeoffDirection(vehicle);
            } else if (navigation.order == AircraftOrder.RETURN) {
                direction = directionTo(vehicle,
                        new Vec3(navigation.home.x, navigation.home.y + 20.0D,
                                navigation.home.z));
            } else if (navigation.order == AircraftOrder.HOLD) {
                direction = getForwardDirection(vehicle);
            } else if (targetAircraft != null || followTarget != null) {
                // Follow/air-combat may use the real 3D position.  A ground
                // target is handled below at a safe altitude instead of
                // turning the aircraft into a guided projectile.
                direction = directionTo(vehicle, target);
            } else if (villagePilot && role == AircraftRole.TRANSPORT
                    && android.getPersistentData().contains(VILLAGE_ROUTE_TARGET_X)) {
                direction = villageLogisticsDirection(vehicle, android, navigation);
            } else if (groundTarget != null) {
                double safeY = Math.max(groundTarget.getY() + 18.0D,
                        navigation.home.y + 16.0D);
                direction = directionTo(vehicle,
                        new Vec3(groundTarget.getX(), safeY, groundTarget.getZ()));
                weaponDirection = directionTo(vehicle, groundTarget);
            } else {
                direction = patrolDirection(vehicle, navigation, tick);
            }
            if (direction == null) {
                LOGGER.debug("Aircraft autopilot has no direction for {}", android.getUUID());
                return;
            }

            // Inputs are set for versions that honor them. Direct motion below
            // is also applied because IA resets inputs when the passenger is not
            // a Player, which is exactly the CC:Androids case.
            applyFlightInputs(vehicle, direction,
                    navigation.order != AircraftOrder.HOLD);
            applyDirectFlightMotion(vehicle, direction, takingOff,
                    navigation.order == AircraftOrder.HOLD);

            long next = aircraftNextFire.getOrDefault(vehicle.getUUID(), 0L);
            boolean aircraftCombatAllowed = combatEnabled(android)
                    && !isSpy(android)
                    && !android.getPersistentData().hasUUID(CAPTURED_BY_TAG)
                    && !android.getPersistentData().getBoolean(IMPRISONED_TAG)
                    && android.getHealth() > 6.0F
                    && navigation.order != AircraftOrder.HOLD
                    && navigation.order != AircraftOrder.RETURN;
            if (target != null && aircraftCombatAllowed
                    && role != AircraftRole.TRANSPORT && tick >= next) {
                fireAircraftWeapons(vehicle,
                        weaponDirection == null ? direction : weaponDirection, role);
                aircraftNextFire.put(vehicle.getUUID(), tick + AIRCRAFT_FIRE_COOLDOWN);
            }
        } catch (RuntimeException error) {
            LOGGER.warn("Aircraft autopilot failed for Android {} in {}",
                    android.getUUID(), vehicle.getClass().getName(), error);
        }
    }

    /** Assigns a Puente Norte raider to the existing compatible aircraft controller. */
    static boolean assignPuenteNorteAircraftPilot(Mob pilot, Entity aircraft) {
        return assignPuenteNorteAircraftPilot(pilot, aircraft, true);
    }

    static boolean assignPuenteNorteAircraftPilot(Mob pilot, Entity aircraft, boolean requireWeapon) {
        if (!pilot.getPersistentData().getBoolean(PuenteNorteRaider.FACTION_TAG)
                || (requireWeapon && !hasCompatibleAircraftWeapons(aircraft))
                || !aircraft.getPassengers().isEmpty() || !pilot.startRiding(aircraft, true)) {
            return false;
        }
        pilot.getPersistentData().putBoolean(PN_AIR_PILOT_TAG, true);
        pilot.getPersistentData().putString(AIR_ROLE_TAG, "COMBAT");
        pilot.getPersistentData().putBoolean(COMBAT_ENABLED_TAG, true);
        aircraftRoles.put(pilot.getUUID(), AircraftRole.COMBAT);
        aircraftNavigation.put(pilot.getUUID(), new AircraftNavigation(aircraft.position(), aircraft.level().getGameTime()));
        return true;
    }

    static boolean assignVillageAircraftPilot(VillageDefender pilot, Entity aircraft,
                                              BlockPos origin, BlockPos destination,
                                              boolean combat) {
        if (!ModList.get().isLoaded("immersive_aircraft") || !isAircraft(aircraft)
                || !aircraft.getPassengers().isEmpty()
                || (combat && !hasCompatibleAircraftWeapons(aircraft))
                || !pilot.startRiding(aircraft, true)) return false;
        pilot.getPersistentData().putBoolean(VILLAGE_AIR_PILOT_TAG, true);
        pilot.getPersistentData().putString(AIR_ROLE_TAG, combat ? "COMBAT" : "TRANSPORT");
        pilot.getPersistentData().putBoolean(COMBAT_ENABLED_TAG, combat);
        pilot.getPersistentData().putInt(VILLAGE_ROUTE_ORIGIN_X, origin.getX());
        pilot.getPersistentData().putInt(VILLAGE_ROUTE_ORIGIN_Y, origin.getY());
        pilot.getPersistentData().putInt(VILLAGE_ROUTE_ORIGIN_Z, origin.getZ());
        pilot.getPersistentData().putInt(VILLAGE_ROUTE_TARGET_X, destination.getX());
        pilot.getPersistentData().putInt(VILLAGE_ROUTE_TARGET_Y, destination.getY());
        pilot.getPersistentData().putInt(VILLAGE_ROUTE_TARGET_Z, destination.getZ());
        pilot.getPersistentData().putBoolean(VILLAGE_ROUTE_RETURNING, false);
        aircraftRoles.put(pilot.getUUID(), combat ? AircraftRole.COMBAT : AircraftRole.TRANSPORT);
        aircraftNavigation.put(pilot.getUUID(), new AircraftNavigation(
                aircraft.position(), aircraft.level().getGameTime()));
        pilot.setPersistenceRequired();
        return true;
    }

    static boolean hasCompatibleAircraftWeapons(Entity aircraft) {
        if (!isAircraft(aircraft) || !isAircraftInventory(aircraft)) return false;
        Object weaponMap = invoke(aircraft, "getWeapons");
        if (!(weaponMap instanceof Map<?, ?> weapons)) return false;
        return weapons.values().stream().anyMatch(value -> value instanceof List<?> list && !list.isEmpty());
    }

    static boolean isCompatibleAircraftInstance(Entity entity) { return isAircraft(entity); }

    private static Entity findEnemyAircraft(Entity own) {
        List<Entity> candidates = own.level().getEntitiesOfClass(
                Entity.class,
                own.getBoundingBox().inflate(AIRCRAFT_TARGET_RANGE),
                entity -> entity != own
                        && isAircraft(entity)
                        && !entity.getPassengers().contains(own));

        return candidates.stream()
                .min(Comparator.comparingDouble(own::distanceToSqr))
                .orElse(null);
    }

    private static Entity findPlayerAircraft(Entity own) {
        return own.level().getEntitiesOfClass(Entity.class,
                        own.getBoundingBox().inflate(AIRCRAFT_TARGET_RANGE), entity -> entity != own
                                && isAircraft(entity)
                                && entity.getPassengers().stream().anyMatch(passenger -> passenger instanceof Player
                                        || (passenger instanceof Mob mob && isAndroid(mob)
                                            && !mob.getPersistentData().getBoolean(PuenteNorteRaider.FACTION_TAG))))
                .stream().min(Comparator.comparingDouble(own::distanceToSqr)).orElse(null);
    }

            private static Entity findPuenteNorteAircraft(Entity own) {
            return own.level().getEntitiesOfClass(Entity.class,
                    own.getBoundingBox().inflate(AIRCRAFT_TARGET_RANGE), entity -> entity != own
                        && isAircraft(entity)
                        && entity.getPassengers().stream().anyMatch(passenger ->
                            passenger instanceof Mob mob && mob.getPersistentData()
                                .getBoolean(PuenteNorteRaider.FACTION_TAG)))
                .stream().min(Comparator.comparingDouble(own::distanceToSqr)).orElse(null);
            }

            private static LivingEntity findVillageAirTarget(Entity aircraft, Mob pilot) {
            return aircraft.level().getEntitiesOfClass(LivingEntity.class,
                    aircraft.getBoundingBox().inflate(AIRCRAFT_TARGET_RANGE), target -> target.isAlive()
                        && target != pilot
                        && target instanceof Mob mob && mob.getPersistentData()
                            .getBoolean(PuenteNorteRaider.FACTION_TAG)
                        && target.getVehicle() != aircraft)
                .stream().min(Comparator.comparingDouble(aircraft::distanceToSqr)).orElse(null);
            }

            private static Vector3f villageLogisticsDirection(Entity aircraft, Mob pilot,
                                       AircraftNavigation navigation) {
            var data = pilot.getPersistentData();
            Vec3 origin = new Vec3(data.getInt(VILLAGE_ROUTE_ORIGIN_X) + 0.5D,
                data.getInt(VILLAGE_ROUTE_ORIGIN_Y) + 26.0D,
                data.getInt(VILLAGE_ROUTE_ORIGIN_Z) + 0.5D);
            Vec3 destination = new Vec3(data.getInt(VILLAGE_ROUTE_TARGET_X) + 0.5D,
                data.getInt(VILLAGE_ROUTE_TARGET_Y) + 26.0D,
                data.getInt(VILLAGE_ROUTE_TARGET_Z) + 0.5D);
            Vec3 waypoint = data.getBoolean(VILLAGE_ROUTE_RETURNING) ? origin : destination;
            if (aircraft.position().distanceToSqr(waypoint) < 28.0D * 28.0D) {
                data.putBoolean(VILLAGE_ROUTE_RETURNING, !data.getBoolean(VILLAGE_ROUTE_RETURNING));
                waypoint = data.getBoolean(VILLAGE_ROUTE_RETURNING) ? origin : destination;
            }
            return directionTo(aircraft, waypoint);
            }

    private static LivingEntity findFactionAirTarget(Entity aircraft, Mob pilot) {
        return aircraft.level().getEntitiesOfClass(LivingEntity.class,
                        aircraft.getBoundingBox().inflate(AIRCRAFT_TARGET_RANGE), target -> target.isAlive()
                                && target != pilot
                                && (target instanceof Player player && !player.isSpectator()
                                    || target instanceof Mob mob && isAndroid(mob)
                                        && !mob.getPersistentData().getBoolean(PuenteNorteRaider.FACTION_TAG))
                                && target.getVehicle() != aircraft)
                .stream().min(Comparator.comparingDouble(aircraft::distanceToSqr)).orElse(null);
    }

    private static LivingEntity findGroundTarget(Entity aircraft) {
        List<LivingEntity> candidates = aircraft.level().getEntitiesOfClass(
                LivingEntity.class,
                aircraft.getBoundingBox().inflate(AIRCRAFT_TARGET_RANGE),
                target -> target.isAlive()
                        && target instanceof Enemy
                        && !(target instanceof Player)
                        && target.getVehicle() != aircraft);

        return candidates.stream()
                .min(Comparator.comparingDouble(aircraft::distanceToSqr))
                .orElse(null);
    }

    private static Vector3f directionTo(Entity from, Entity to) {
        Vector3f direction = new Vector3f(
                (float) (to.getX() - from.getX()),
                (float) (to.getY() - from.getY()),
                (float) (to.getZ() - from.getZ()));
        return direction.lengthSquared() < 0.0001F ? null : direction.normalize();
    }

    private static Vector3f directionTo(Entity from, Vec3 to) {
        Vector3f direction = new Vector3f(
                (float) (to.x - from.getX()),
                (float) (to.y - from.getY()),
                (float) (to.z - from.getZ()));
        return direction.lengthSquared() < 0.0001F ? null : direction.normalize();
    }

    private static Vector3f takeoffDirection(Entity aircraft) {
        Vector3f forward = getForwardDirection(aircraft);
        if (forward == null || forward.lengthSquared() < 0.0001F) {
            forward = new Vector3f(0.0F, 0.0F, 1.0F);
        }
        forward.normalize().mul(0.85F);
        forward.y = 0.35F;
        return forward.normalize();
    }

    private static Vector3f patrolDirection(Entity aircraft,
                                             AircraftNavigation navigation,
                                             long tick) {
        double phase = ((tick - navigation.started) % 600L) / 600.0D
                * Math.PI * 2.0D;
        Vec3 point = new Vec3(
                navigation.home.x + Math.cos(phase) * 32.0D,
                navigation.home.y + 20.0D + Math.sin(phase * 2.0D) * 6.0D,
                navigation.home.z + Math.sin(phase) * 32.0D);
        return directionTo(aircraft, point);
    }

    private static Entity findFollowTarget(Mob android, AircraftNavigation navigation) {
        if (navigation.followTarget == null
                || !(android.level() instanceof ServerLevel level)) {
            return null;
        }
        return level.getEntity(navigation.followTarget);
    }

    private static void applyDirectFlightMotion(Entity aircraft, Vector3f direction,
                                                boolean takingOff, boolean holding) {
        Vector3f normalized = new Vector3f(direction);
        if (normalized.lengthSquared() < 0.0001F) {
            return;
        }
        normalized.normalize();
        double speed = holding ? 0.0D : (takingOff ? 0.32D : 0.46D);
        Vec3 desired = new Vec3(
                normalized.x * speed,
                normalized.y * speed,
                normalized.z * speed);
        Vec3 current = aircraft.getDeltaMovement();
        double smoothing = holding ? 0.20D : 0.35D;
        Vec3 velocity = current.scale(1.0D - smoothing).add(desired.scale(smoothing));
        if (!holding && velocity.y < -0.12D) {
            velocity = new Vec3(velocity.x, -0.12D, velocity.z);
        }
        if (!holding && aircraft.getY() < 4.0D && velocity.y < 0.10D) {
            velocity = new Vec3(velocity.x, 0.10D, velocity.z);
        }
        aircraft.setDeltaMovement(velocity);
        aircraft.hasImpulse = true;

        float yaw = (float) Math.toDegrees(Math.atan2(-normalized.x, normalized.z));
        float pitch = (float) Math.toDegrees(Math.asin(-normalized.y));
        aircraft.setYRot(yaw);
        aircraft.setXRot(Math.max(-25.0F, Math.min(25.0F, pitch)));
    }

    private static Vector3f getForwardDirection(Entity aircraft) {
        Object value = invoke(aircraft, "getForwardDirection");
        return value instanceof Vector3f direction ? new Vector3f(direction) : null;
    }

    private static void applyFlightInputs(Entity aircraft, Vector3f targetDirection,
                                          boolean hasTarget) {
        Object rightValue = invoke(aircraft, "getRightDirection");
        Object topValue = invoke(aircraft, "getTopDirection");
        if (!(rightValue instanceof Vector3f right)
                || !(topValue instanceof Vector3f top)) {
            return;
        }

        float turn = clamp(targetDirection.dot(right) * 2.0F);
        float pitch = clamp(targetDirection.dot(top) * 2.0F);
        // IA maps movementY to engine power.  A negative value is the brake /
        // reverse input, so patrol must keep it positive even without a target.
        float throttle = hasTarget ? 1.0F : 0.0F;

        // setEngineTarget exists only in some aircraft versions.  Calling it
        // unconditionally made IA 1.5.0 throw before setInputs was reached.
        invokeIfPresent(aircraft, "setEngineTarget",
                new Class<?>[]{float.class}, throttle);
        invoke(aircraft, "setInputs",
                new Class<?>[]{float.class, float.class, float.class},
                turn, throttle, -pitch);
    }

    private static void invokeIfPresent(Object receiver, String method,
                                        Class<?>[] types, Object... args) {
        try {
            Method target = receiver.getClass().getMethod(method, types);
            target.invoke(receiver, args);
        } catch (NoSuchMethodException ignored) {
            // Optional API: Immersive Aircraft 1.5.0 has no setter, while
            // other aircraft bridges may expose it.
        } catch (IllegalAccessException | InvocationTargetException error) {
            LOGGER.debug("Optional aircraft method {} failed", method, error);
        }
    }

    private static void fireAircraftWeapons(Entity aircraft, Vector3f direction,
                                            AircraftRole role) {
        if (!isAircraftInventory(aircraft)) {
            return;
        }

        Object weaponMap = invoke(aircraft, "getWeapons");
        if (!(weaponMap instanceof Map<?, ?> weapons)) {
            return;
        }

        for (Map.Entry<?, ?> entry : weapons.entrySet()) {
            if (!(entry.getKey() instanceof Number slot)
                    || !(entry.getValue() instanceof List<?> weaponList)) {
                continue;
            }
            for (int index = 0; index < weaponList.size(); index++) {
                if (role == AircraftRole.BOMBER
                        && !isBombBay(weaponList.get(index))) {
                    continue;
                }
                try {
                    invoke(aircraft, "fireWeapon",
                            new Class<?>[]{int.class, int.class, Vector3f.class},
                            slot.intValue(), index, new Vector3f(direction));
                } catch (RuntimeException error) {
                    // Empty magazines and invalid mounts are normal during
                    // gameplay; leave the other mounted weapons operational.
                    LOGGER.debug("Aircraft weapon could not fire", error);
                }
            }
        }
    }

    private static boolean isBombBay(Object weapon) {
        return weapon != null
                && weapon.getClass().getSimpleName().toLowerCase().contains("bombbay");
    }

    private static float clamp(float value) {
        return Math.max(-1.0F, Math.min(1.0F, value));
    }

    private static boolean isAircraft(Entity entity) {
        loadAircraftTypes();
        return aircraftVehicleType != null && aircraftVehicleType.isInstance(entity);
    }

    private static boolean isAircraftInventory(Entity entity) {
        loadAircraftTypes();
        return aircraftInventoryType != null && aircraftInventoryType.isInstance(entity);
    }

    private static void loadAircraftTypes() {
        if (checkedAircraft) {
            return;
        }
        synchronized (AndroidsCompatMod.class) {
            if (checkedAircraft) {
                return;
            }
            try {
                aircraftVehicleType = Class.forName(AIRCRAFT_VEHICLE_CLASS);
                aircraftInventoryType = Class.forName(AIRCRAFT_INVENTORY_CLASS);
                LOGGER.info("Immersive Aircraft detected; Android aircraft combat enabled");
            } catch (ClassNotFoundException ignored) {
                LOGGER.info("Immersive Aircraft not installed; aircraft combat disabled");
            }
            checkedAircraft = true;
        }
    }

    private static Object invoke(Object receiver, String method, Object... args) {
        return invoke(receiver, method, parameterTypes(args), args);
    }

    private static Object invoke(Object receiver, String method, Class<?>[] types,
                                 Object... args) {
        try {
            Method target = receiver.getClass().getMethod(method, types);
            return target.invoke(receiver, args);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException error) {
            throw new RuntimeException(error);
        }
    }

    private static Class<?>[] parameterTypes(Object[] args) {
        Class<?>[] types = new Class<?>[args.length];
        for (int index = 0; index < args.length; index++) {
            types[index] = args[index].getClass();
        }
        return types;
    }

    private static Mob androidByUuid(ServerLevel level, String value) {
        try {
            Entity entity = level.getEntity(UUID.fromString(value));
            return entity instanceof Mob mob && isAndroid(mob) ? mob : null;
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static Mob nearestAndroid(Player player, double range) {
        ServerLevel level = (ServerLevel) player.level();
        return level.getEntitiesOfClass(
                        Mob.class, player.getBoundingBox().inflate(range),
                        AndroidsCompatMod::isAndroid)
                .stream()
                .min(Comparator.comparingDouble(
                        (Mob candidate) -> player.distanceToSqr(candidate)))
                .orElse(null);
    }

    private static int statsNearest(CommandSourceStack source) {
        Entity caller = source.getEntity();
        if (!(caller instanceof Player player)) {
            source.sendFailure(Component.literal("Solo un jugador puede usar este comando."));
            return 0;
        }
        Mob android = nearestAndroid(player, 32.0D);
        if (android == null) {
            source.sendFailure(Component.literal("No hay un Android cerca."));
            return 0;
        }
        updateProgression(android);
        source.sendSuccess(() -> Component.literal(formatStats(android)), false);
        return 1;
    }

    private static int speakNearest(CommandSourceStack source, String text) {
        Entity caller = source.getEntity();
        if (!(caller instanceof Player player)) {
            source.sendFailure(Component.literal("Solo un jugador puede usar este comando."));
            return 0;
        }
        String speech = text == null ? "" : text.trim();
        if (speech.isEmpty() || speech.length() > 500) {
            source.sendFailure(Component.literal("El texto debe tener entre 1 y 500 caracteres."));
            return 0;
        }
        long gameTime = player.level().getGameTime();
        long nextAllowed = ttsNextRequestTick.getOrDefault(player.getUUID(), 0L);
        if (gameTime < nextAllowed) {
            source.sendFailure(Component.literal("Espera dos segundos antes de pedir otra frase TTS."));
            return 0;
        }
        if (!ModList.get().isLoaded("voicechat_api")) {
            source.sendFailure(Component.literal(
                    "Esta función requiere Simple Voice Chat 2.6.0 o posterior en servidor y clientes."));
            return 0;
        }
        if (!AndroidsCompatVoiceChat.isReady()) {
            source.sendFailure(Component.literal(
                    "Simple Voice Chat aún no inicializó su API en el servidor."));
            return 0;
        }
        String endpoint = TTS_ENDPOINT.get().trim();
        if (endpoint.isEmpty()) {
            source.sendFailure(Component.literal(
                    "Configura el endpoint TTS en serverconfig/androidscompat-server.toml."));
            return 0;
        }
        Mob android = nearestAndroid(player, 32.0D);
        if (android == null) {
            source.sendFailure(Component.literal("No hay un Android a menos de 32 bloques."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Generando voz para "
                + android.getName().getString() + "..."), false);
        int timeoutMillis = TTS_TIMEOUT_MS.get();
        double distance = TTS_VOICE_DISTANCE.get();
        var server = android.getServer();
        ttsNextRequestTick.put(player.getUUID(), gameTime + 40L);
        CompletableFuture.supplyAsync(() -> {
            try {
                return TtsSynthesizer.synthesize(endpoint, speech, timeoutMillis);
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }).whenComplete((pcm, failure) -> {
            if (server == null) {
                return;
            }
            server.execute(() -> {
                if (failure != null) {
                    Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                            ? failure.getCause() : failure;
                    LOGGER.warn("TTS request failed ({})", cause.getClass().getSimpleName());
                    source.sendFailure(Component.literal(
                            "Falló TTS. Revisa el endpoint y el log del servidor."));
                } else if (!android.isAlive()) {
                    source.sendFailure(Component.literal("El Android desapareció antes de hablar."));
                } else if (!AndroidsCompatVoiceChat.speakFrom(android, pcm, distance)) {
                    source.sendFailure(Component.literal(
                            "Simple Voice Chat no pudo iniciar el audio del Android."));
                }
            });
        });
        return 1;
    }

    private static int compatibilityStatus(CommandSourceStack source) {
        List<String> loaded = new java.util.ArrayList<>();
        for (String modId : OPTIONAL_MODS) {
            if (ModList.get().isLoaded(modId)) {
                loaded.add(modId);
            }
        }
        source.sendSuccess(() -> Component.literal(
                "Mods detectados: " + String.join(", ", loaded)), false);
        return 1;
    }

    private static String formatStats(Mob android) {
        long xp = android.getPersistentData().getLong(XP_TAG);
        int level = getLevel(android);
        return "Android nivel=" + level
                + " XP=" + xp
                + " accuracy=" + String.format(java.util.Locale.ROOT, "%.3f", accuracyFor(android))
                + " speed=" + String.format(java.util.Locale.ROOT, "%.3f",
                attributeValue(android, Attributes.MOVEMENT_SPEED));
    }

    static boolean isAndroidEntity(Entity entity) {
        return isAndroid(entity);
    }

    static void appendJadeStats(net.minecraft.nbt.CompoundTag tag, Entity entity) {
        if (!(entity instanceof Mob android) || !isAndroid(android)) {
            return;
        }
        net.minecraft.nbt.CompoundTag data = android.getPersistentData();
        tag.putBoolean("android", true);
        tag.putInt("level", getLevel(android));
        tag.putLong("xp", experience(android));
        tag.putDouble("accuracy", accuracyFor(android));
        tag.putDouble("speed", attributeValue(android, Attributes.MOVEMENT_SPEED));
        tag.putFloat("health", android.getHealth());
        tag.putFloat("max_health", android.getMaxHealth());
        tag.putString("squad", data.getString(SQUAD_TAG));
        tag.putString("order", data.getString(ORDER_TAG));
        tag.putString("air_role", data.getString(AIR_ROLE_TAG));
        tag.putBoolean("commander", data.getBoolean(COMMANDER_TAG));
        tag.putBoolean("command_android", isCommandAndroid(android));
        tag.putBoolean("combat_enabled", combatEnabled(android));
        tag.putBoolean("spy", isSpy(android));
        tag.putString("cover", data.getString(SPY_COVER_TAG));
        tag.putBoolean("captured", data.hasUUID(CAPTURED_BY_TAG));
        tag.putBoolean("imprisoned", data.getBoolean(IMPRISONED_TAG));
        tag.putInt("armor", countArmor(android));
        ItemStack weapon = android.getMainHandItem();
        if (!weapon.isEmpty()) {
            var key = BuiltInRegistries.ITEM.getKey(weapon.getItem());
            tag.putString("weapon", key == null ? weapon.getHoverName().getString()
                    : key.toString());
        }
    }

    private static int countArmor(Mob android) {
        int count = 0;
        for (ItemStack stack : android.getArmorSlots()) {
            if (!stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static long experience(Mob android) {
        return Math.max(0L, android.getPersistentData().getLong(XP_TAG));
    }

    private static int getLevel(Mob android) {
        long xp = experience(android);
        double raw = Math.sqrt(xp / (double) XP_PER_LEVEL_SQUARE) + 1.0D;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1.0D, raw));
    }

    private static void grantExperience(Mob android, long amount) {
        if (amount <= 0L) {
            return;
        }
        long beforeXp = experience(android);
        int beforeLevel = getLevel(android);
        long afterXp = beforeXp > Long.MAX_VALUE - amount
                ? Long.MAX_VALUE : beforeXp + amount;
        android.getPersistentData().putLong(XP_TAG, afterXp);
        updateProgression(android);
        int afterLevel = getLevel(android);
        if (afterLevel > beforeLevel) {
            LOGGER.info("Android {} reached level {} (XP={})",
                    android.getUUID(), afterLevel, afterXp);
            android.level().broadcastEntityEvent(android, (byte) 28);
        }
    }

    private static double attributeValue(Mob android,
                                         net.minecraft.world.entity.ai.attributes.Attribute attribute) {
        AttributeInstance instance = android.getAttribute(attribute);
        return instance == null ? 0.0D : instance.getBaseValue();
    }

    private static void updateProgression(Mob android) {
        var data = android.getPersistentData();
        boolean commandAndroid = isCommandAndroid(android);
        AttributeInstance speed = android.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance health = android.getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance follow = android.getAttribute(Attributes.FOLLOW_RANGE);

        if (speed != null && !data.contains(BASE_SPEED_TAG)) {
            data.putDouble(BASE_SPEED_TAG, speed.getBaseValue());
        }
        if (health != null && (!data.contains(BASE_HEALTH_TAG)
                || (commandAndroid
                && data.getDouble(BASE_HEALTH_TAG) > COMMAND_ANDROID_MAX_HEALTH))) {
            data.putDouble(BASE_HEALTH_TAG, Math.min(health.getBaseValue(),
                    commandAndroid ? COMMAND_ANDROID_MAX_HEALTH : health.getBaseValue()));
        }
        if (follow != null && !data.contains(BASE_FOLLOW_TAG)) {
            data.putDouble(BASE_FOLLOW_TAG, follow.getBaseValue());
        }

        int level = getLevel(android);
        double growth = Math.log1p(level) / Math.log(2.0D);
        if (speed != null && data.contains(BASE_SPEED_TAG)) {
            speed.setBaseValue(data.getDouble(BASE_SPEED_TAG) * (1.0D + growth * 0.03D));
        }
        if (health != null && data.contains(BASE_HEALTH_TAG)) {
            double oldMax = health.getValue();
            double nextMax = data.getDouble(BASE_HEALTH_TAG) + growth * 2.0D;
            if (commandAndroid) {
                nextMax = Math.min(COMMAND_ANDROID_MAX_HEALTH, nextMax);
                android.setInvulnerable(false);
            }
            health.setBaseValue(nextMax);
            if (android.getHealth() > health.getValue()) {
                android.setHealth((float) health.getValue());
            } else if (health.getValue() > oldMax && android.getHealth() > 0.0F) {
                android.heal((float) Math.min(2.0D, health.getValue() - oldMax));
            }
        }
        if (follow != null && data.contains(BASE_FOLLOW_TAG)) {
            follow.setBaseValue(data.getDouble(BASE_FOLLOW_TAG) + growth * 4.0D);
        }

        data.putInt(LEVEL_TAG, level);
        Integer previous = lastLoggedLevel.put(android.getUUID(), level);
        if (previous != null && previous != level) {
            LOGGER.info("Android {} stats improved to level {}", android.getUUID(), level);
        }
    }

    /** Accuracy is skill-based but still has random shot spread. */
    private static float accuracyFor(Mob android) {
        int level = getLevel(android);
        return (float) Math.min(0.98D,
                0.45D + 0.08D * Math.log1p(level));
    }

    private static int mountNearest(CommandSourceStack source) {
        Entity caller = source.getEntity();
        if (!(caller instanceof Player player)) {
            source.sendFailure(Component.literal("Solo un jugador puede usar este comando."));
            return 0;
        }

        ServerLevel level = (ServerLevel) player.level();
        Mob android = level.getEntitiesOfClass(
                        Mob.class, player.getBoundingBox().inflate(8.0D),
                        candidate -> isAndroid(candidate)
                                && !candidate.isPassenger()
                                && aircraftRoles.containsKey(candidate.getUUID()))
                .stream()
                .min(Comparator.comparingDouble((Mob candidate) -> player.distanceToSqr(candidate)))
                .orElse(null);
        Entity aircraft = level.getEntitiesOfClass(
                        Entity.class, player.getBoundingBox().inflate(8.0D),
                        candidate -> isAircraft(candidate) && candidate.getPassengers().isEmpty())
                .stream()
                .min(Comparator.comparingDouble((Entity candidate) -> player.distanceToSqr(candidate)))
                .orElse(null);

        if (android == null || aircraft == null || !android.startRiding(aircraft, true)) {
            source.sendFailure(Component.literal(
                    "Acerca un Android y un avión vacío y vuelve a ejecutar el comando."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Android montado: piloto automático activado."), true);
        return 1;
    }

    private static int dismountNearest(CommandSourceStack source) {
        Entity caller = source.getEntity();
        if (!(caller instanceof Player player)) {
            source.sendFailure(Component.literal("Solo un jugador puede usar este comando."));
            return 0;
        }

        ServerLevel level = (ServerLevel) player.level();
        Mob android = level.getEntitiesOfClass(
                        Mob.class, player.getBoundingBox().inflate(8.0D),
                        candidate -> isAndroid(candidate) && candidate.isPassenger())
                .stream()
                .min(Comparator.comparingDouble((Mob candidate) -> player.distanceToSqr(candidate)))
                .orElse(null);
        if (android == null) {
            source.sendFailure(Component.literal("No hay un Android montado cerca."));
            return 0;
        }
        android.stopRiding();
        source.sendSuccess(() -> Component.literal("Android desmontado."), true);
        return 1;
    }

    private static int reloadNearest(CommandSourceStack source) {
        Entity caller = source.getEntity();
        if (!(caller instanceof Player player)) {
            source.sendFailure(Component.literal("Solo un jugador puede usar este comando."));
            return 0;
        }

        ServerLevel level = (ServerLevel) player.level();
        Mob android = level.getEntitiesOfClass(
                        Mob.class, player.getBoundingBox().inflate(8.0D),
                        candidate -> isAndroid(candidate))
                .stream()
                .min(Comparator.comparingDouble(
                        (Mob candidate) -> player.distanceToSqr(candidate)))
                .orElse(null);
        if (android == null || !reloadAndroid(android)) {
            source.sendFailure(Component.literal("No hay un Android con arma NTGL cerca."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Recarga solicitada al Android."), true);
        return 1;
    }

    /** Starts NTGL's real server-side reload tracker for an Android. */
    private static boolean reloadAndroid(Mob android) {
        InteractionHand hand;
        ItemStack stack;
        if (isNtglWeapon(android.getMainHandItem())) {
            hand = InteractionHand.MAIN_HAND;
            stack = android.getMainHandItem();
        } else if (isNtglWeapon(android.getOffhandItem())) {
            hand = InteractionHand.OFF_HAND;
            stack = android.getOffhandItem();
        } else {
            return false;
        }

        try {
            Class<?> dataType = Class.forName(
                    "com.nukateam.ntgl.common.data.WeaponData");
            Object data = dataType.getConstructor(ItemStack.class, LivingEntity.class)
                    .newInstance(stack, android);
            Class<?> trackerType = Class.forName(
                    "com.nukateam.ntgl.common.util.trackers.ReloadTracker");
            trackerType.getMethod("startReloading", dataType, InteractionHand.class)
                    .invoke(null, data, hand);
            LOGGER.info("NTGL reload requested for Android {}", android.getUUID());
            return true;
        } catch (ReflectiveOperationException error) {
            LOGGER.warn("Could not start NTGL reload for Android", error);
            return false;
        }
    }

    private static int setNearestAircraftRole(CommandSourceStack source,
                                              AircraftRole role) {
        Entity caller = source.getEntity();
        if (!(caller instanceof Player player)) {
            source.sendFailure(Component.literal("Solo un jugador puede usar este comando."));
            return 0;
        }

        ServerLevel level = (ServerLevel) player.level();
        Mob android = level.getEntitiesOfClass(
                        Mob.class, player.getBoundingBox().inflate(8.0D),
                        candidate -> isAndroid(candidate)
                                && candidate.isPassenger()
                                && isAircraft(candidate.getVehicle()))
                .stream()
                .min(Comparator.comparingDouble((Mob candidate) -> player.distanceToSqr(candidate)))
                .orElse(null);
        if (android == null) {
            source.sendFailure(Component.literal(
                    "No hay un Android montado en un avion cerca."));
            return 0;
        }

        aircraftRoles.put(android.getUUID(), role);
        android.getPersistentData().putString(AIR_ROLE_TAG,
                role.name().toLowerCase(java.util.Locale.ROOT));
        rememberOwner(android, player);
        source.sendSuccess(() -> Component.literal(
                "Rol aereo: " + role.name().toLowerCase()), true);
        return 1;
    }

    private static boolean isAndroid(Entity entity) {
        return hasClassInHierarchy(entity, ANDROID_BASE_CLASS);
    }

    private static boolean isCommandAndroid(Entity entity) {
        return hasClassInHierarchy(entity, COMMAND_ANDROID_CLASS);
    }

    private static boolean isAndroidFrame(Entity entity) {
        return hasClassInHierarchy(entity, ANDROID_FRAME_CLASS);
    }

    private static int frameComponentsNeeded(Mob frame) {
        Object value = invokeFrameMethod(frame, "getComponentsNeeded");
        return value instanceof Number number ? number.intValue() : -1;
    }

    private static int frameIngotsNeeded(Mob frame) {
        Object value = invokeFrameMethod(frame, "getIngotsNeeded");
        return value instanceof Number number ? number.intValue() : -1;
    }

    private static boolean frameHasCore(Mob frame) {
        return Boolean.TRUE.equals(invokeFrameMethod(frame, "hasCore"));
    }

    private static Object invokeFrameMethod(Mob frame, String name) {
        try {
            Method method = frame.getClass().getMethod(name);
            return method.invoke(frame);
        } catch (ReflectiveOperationException exception) {
            LOGGER.warn("No se pudo consultar {} en AndroidFrame", name, exception);
            return null;
        }
    }

    private static boolean invokeDiamondPlates(Mob frame) {
        try {
            for (Method method : frame.getClass().getDeclaredMethods()) {
                if (!"addPlates".equals(method.getName())
                        || method.getParameterCount() != 2) {
                    continue;
                }
                method.setAccessible(true);
                Object result = method.invoke(frame, frame.level(), true);
                return Boolean.TRUE.equals(result);
            }
        } catch (ReflectiveOperationException exception) {
            LOGGER.warn("No se pudo aplicar un diamante al Android Frame", exception);
        }
        return false;
    }

    private static boolean isAndroidCpu(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null && "cc_androids:android_cpu".equals(key.toString());
    }

    private static int dyeColor(ItemStack stack) {
        if (stack.isEmpty()) {
            return -1;
        }
        if (stack.is(Items.WHITE_DYE)) return 0xF9FFFE;
        if (stack.is(Items.ORANGE_DYE)) return 0xF9801D;
        if (stack.is(Items.MAGENTA_DYE)) return 0xC74EBD;
        if (stack.is(Items.LIGHT_BLUE_DYE)) return 0x3AB3DA;
        if (stack.is(Items.YELLOW_DYE)) return 0xFED83D;
        if (stack.is(Items.LIME_DYE)) return 0x80C71F;
        if (stack.is(Items.PINK_DYE)) return 0xF38BAA;
        if (stack.is(Items.GRAY_DYE)) return 0x474F52;
        if (stack.is(Items.LIGHT_GRAY_DYE)) return 0x9D9D97;
        if (stack.is(Items.CYAN_DYE)) return 0x169C9C;
        if (stack.is(Items.PURPLE_DYE)) return 0x8932B8;
        if (stack.is(Items.BLUE_DYE)) return 0x3C44AA;
        if (stack.is(Items.BROWN_DYE)) return 0x835432;
        if (stack.is(Items.GREEN_DYE)) return 0x5E7C16;
        if (stack.is(Items.RED_DYE)) return 0xB02E26;
        if (stack.is(Items.BLACK_DYE)) return 0x1D1D21;
        return -1;
    }

    private static boolean finishCommandFrame(Mob frame, ItemStack cpu) {
        try {
            for (Method method : frame.getClass().getDeclaredMethods()) {
                if (!"finish".equals(method.getName())
                        || method.getParameterCount() != 2
                        || !method.getParameterTypes()[0].isEnum()
                        || method.getParameterTypes()[1] != int.class) {
                    continue;
                }
                @SuppressWarnings({"rawtypes", "unchecked"})
                Object commandFamily = Enum.valueOf(
                        (Class<? extends Enum>) method.getParameterTypes()[0], "COMMAND");
                int computerId = -1;
                if (cpu.hasTag() && cpu.getTag().contains("ComputerID")) {
                    computerId = cpu.getTag().getInt("ComputerID");
                }
                method.setAccessible(true);
                method.invoke(frame, commandFamily, computerId);
                return true;
            }
        } catch (ReflectiveOperationException exception) {
            LOGGER.warn("No se pudo finalizar el Android Frame como Command Android",
                    exception);
        }
        return false;
    }

    private static void consumeOne(Player player, InteractionHand hand) {
        if (!player.getAbilities().instabuild) {
            player.getItemInHand(hand).shrink(1);
        }
    }

    private static boolean hasClassInHierarchy(Entity entity, String className) {
        if (entity == null) {
            return false;
        }
        Class<?> type = entity.getClass();
        while (type != null) {
            if (className.equals(type.getName())) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static boolean isNtglWeapon(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Class<?> type = loadNtglWeaponType();
        return type != null && type.isInstance(stack.getItem());
    }

    /**
     * NTGL normally installs this goal itself every 20 ticks. Androids are
     * special entities, so install the same goal immediately as a fallback.
     * This also removes the dependency on Forge event listener order.
     */
    private static void ensureGunAttackGoal(Mob mob) {
        if (!(mob instanceof PathfinderMob pathfinder)) {
            return;
        }

        for (WrappedGoal wrapped : pathfinder.goalSelector.getAvailableGoals()) {
            Goal goal = wrapped.getGoal();
            if (NTGL_GOAL_CLASS.equals(goal.getClass().getName())) {
                return;
            }
        }

        try {
            Class<?> goalType = Class.forName(NTGL_GOAL_CLASS);
            Object goal = goalType.getConstructor(
                            PathfinderMob.class, double.class, float.class, float.class)
                    .newInstance(pathfinder, 1.0D, 1.0F, 30.0F);
            pathfinder.goalSelector.addGoal(2, (Goal) goal);
            LOGGER.info("NTGL GunAttackGoal attached to Android {}", mob.getUUID());
        } catch (ReflectiveOperationException | ClassCastException error) {
            LOGGER.warn("Could not attach NTGL GunAttackGoal to Android", error);
        }
    }

    static void ensureFactionGunGoal(Mob mob) {
        if (isNtglWeapon(mob.getMainHandItem())) ensureGunAttackGoal(mob);
    }

    private static void removeGunAttackGoal(Mob mob) {
        if (!(mob instanceof PathfinderMob pathfinder)) return;
        for (WrappedGoal wrapped : new ArrayList<>(pathfinder.goalSelector.getAvailableGoals())) {
            Goal goal = wrapped.getGoal();
            if (NTGL_GOAL_CLASS.equals(goal.getClass().getName())) {
                pathfinder.goalSelector.removeGoal(goal);
            }
        }
    }

    /** NTGL's goal can be blocked by CC:Androids' own movement goals. */
    private static void directGroundShot(Mob android, LivingEntity target) {
        if (!(android instanceof PathfinderMob)
                || !ntglScriptSessions.contains(android.getUUID())
                || android.isPassenger()
                || combatBlocked(android)
                || !isValidTarget(android, target)
                || !isNtglWeapon(android.getMainHandItem())) {
            return;
        }

        long now = android.level().getGameTime();
        Long next = groundNextFire.get(android.getUUID());
        if (next != null && now < next) {
            return;
        }

        try {
            aimWithVariableAccuracy(android, target);
            Class<?> goalType = Class.forName(NTGL_GOAL_CLASS);
            Method shoot = goalType.getMethod("shoot", LivingEntity.class, InteractionHand.class);
            shoot.invoke(null, android, InteractionHand.MAIN_HAND);
            groundNextFire.put(android.getUUID(), now + DIRECT_GROUND_FIRE_COOLDOWN);
            LOGGER.debug("NTGL direct ground shot for Android {} at {}",
                    android.getUUID(), target.getType().toString());
        } catch (ReflectiveOperationException | RuntimeException error) {
            LOGGER.warn("Could not fire NTGL weapon from Android", error);
        }
    }

    private static void aimWithVariableAccuracy(Mob android, LivingEntity target) {
        Vec3 origin = android.getEyePosition(1.0F);
        Vec3 wanted = target.getEyePosition(1.0F).subtract(origin).normalize();
        double spread = (1.0D - accuracyFor(android)) * 0.35D;
        var random = android.getRandom();
        Vec3 noisy = wanted.add(
                (random.nextDouble() - 0.5D) * spread,
                (random.nextDouble() - 0.5D) * spread,
                (random.nextDouble() - 0.5D) * spread).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-noisy.x, noisy.z));
        float pitch = (float) Math.toDegrees(Math.asin(-noisy.y));
        android.setYRot(yaw);
        android.setXRot(pitch);
        android.setYHeadRot(yaw);
        android.setYBodyRot(yaw);
    }

    /** GunAttackGoal only reads the main hand, so normalize CC:Androids equipment. */
    private static void moveNtglWeaponToMainHand(Mob android) {
        if (isNtglWeapon(android.getMainHandItem())
                || !isNtglWeapon(android.getOffhandItem())) {
            return;
        }

        ItemStack main = android.getMainHandItem();
        ItemStack offhand = android.getOffhandItem();
        android.setItemInHand(InteractionHand.MAIN_HAND, offhand);
        android.setItemInHand(InteractionHand.OFF_HAND, main);
        LOGGER.info("Moved NTGL weapon to Android main hand {}", android.getUUID());
    }

    /**
     * Reloads an empty NTGL weapon from the Android inventory.
     *
     * The old implementation called setMaxAmmo() unconditionally.  That made
     * every Android effectively creative: it received a new full magazine
     * without consuming any cartridge.  NTGL already knows which ammo item is
     * valid for the weapon, so use its InventoryUtil/AmmoContext to consume
     * real items from the Android's SimpleContainer and only load the amount
     * that was actually found.
     */
    private static void ensureAutomaticReload(Mob mob, ItemStack weapon) {
        try {
            Class<?> dataType = Class.forName(NTGL_WEAPON_DATA_CLASS);
            Object data = dataType.getConstructor(ItemStack.class, LivingEntity.class)
                    .newInstance(weapon, mob);

            // GunAttackGoal and EntityReloadTracker always use PRIMARY.  Set it
            // explicitly as well: some NTGL weapons keep AmmoCount in a nested
            // tag whose key is the selected weapon mode.
            Class<?> modeType = Class.forName(
                    "com.nukateam.ntgl.common.data.holders.WeaponMode");
            Object primary = modeType.getField("PRIMARY").get(null);
            dataType.getMethod("setWeaponMode", modeType).invoke(data, primary);

            Class<?> stateHelper = Class.forName(NTGL_STATE_HELPER_CLASS);
            int before = (Integer) stateHelper
                    .getMethod("getAmmoCount", dataType)
                    .invoke(null, data);
            int max = (Integer) Class.forName(
                    "com.nukateam.ntgl.common.util.util.WeaponModifierHelper")
                    .getMethod("getMaxAmmo", dataType)
                    .invoke(null, data);
            boolean hasAmmo = (Boolean) stateHelper
                    .getMethod("hasAmmo", dataType)
                    .invoke(null, data);
            if (hasAmmo) {
                return;
            }

            int requested = Math.max(0, max - before);
            int loaded = consumeAndroidAmmo(mob, data, requested);
            if (loaded <= 0) {
                LOGGER.debug("Android {} has no compatible NTGL ammunition for {}",
                        mob.getUUID(), BuiltInRegistries.ITEM.getKey(weapon.getItem()));
                return;
            }

            // Write only the number of rounds paid for with physical ammo.
            stateHelper.getMethod("setAmmoCount", dataType, int.class)
                    .invoke(null, data, Math.min(max, before + loaded));

            // Re-attach the same stack so NTGL sees the updated NBT even on
            // Android implementations that expose a defensive ItemStack copy.
            mob.setItemInHand(InteractionHand.MAIN_HAND, weapon);

            int after = (Integer) stateHelper
                    .getMethod("getAmmoCount", dataType)
                    .invoke(null, data);
            if (after <= 0) {
                LOGGER.warn("Automatic NTGL reload wrote no ammo for Android {} (before={}, max={}, loaded={}, after={})",
                        mob.getUUID(), before, max, loaded, after);
            } else {
                LOGGER.info("Automatic NTGL reload completed for Android {} ({} -> {}/{}, consumed={})",
                        mob.getUUID(), before, after, max, loaded);
            }
        } catch (ReflectiveOperationException | ClassCastException error) {
            LOGGER.warn("Automatic NTGL reload failed for Android", error);
        }
    }

    /**
     * Consumes compatible ammo using NTGL's own AmmoHolder rules.
     *
     * This keeps compatibility with custom ammo from NTGL and other weapon
     * mods: AmmoHolder.isAcceptable() decides what can be loaded and its
     * AmmoContext.shrink() applies the correct stack/value behaviour.
     */
    private static int consumeAndroidAmmo(Mob mob, Object data, int requested)
            throws ReflectiveOperationException {
        if (requested <= 0) {
            return 0;
        }

        SimpleContainer inventory = getAndroidInventory(mob);
        if (inventory == null) {
            return 0;
        }

        Class<?> dataType = Class.forName(NTGL_WEAPON_DATA_CLASS);
        Class<?> holderType = Class.forName(NTGL_AMMO_HOLDER_CLASS);
        Class<?> contextType = Class.forName(NTGL_AMMO_CONTEXT_CLASS);
        Class<?> inventoryUtil = Class.forName(NTGL_INVENTORY_UTIL_CLASS);

        Class<?> stateHelperType = Class.forName(NTGL_STATE_HELPER_CLASS);
        Object ammoHolder = stateHelperType
                .getMethod("getCurrentAmmoWithoutCheck", dataType)
                .invoke(null, data);
        if (ammoHolder == null) {
            return 0;
        }

        Method findAmmo = inventoryUtil.getMethod(
                "findAmmo", net.minecraft.world.Container.class, holderType);
        Method stackMethod = contextType.getMethod("stack");
        Method shrinkMethod = contextType.getMethod(
                "shrink", int.class, holderType, LivingEntity.class);

        int remaining = requested;
        int loaded = 0;
        int safety = inventory.getContainerSize() * 2 + 4;
        while (remaining > 0 && safety-- > 0) {
            Object context = findAmmo.invoke(null, inventory, ammoHolder);
            if (context == null) {
                break;
            }

            ItemStack stack = (ItemStack) stackMethod.invoke(context);
            if (stack == null || stack.isEmpty()) {
                break;
            }

            int value = (Integer) holderType
                    .getMethod("getValue", ItemStack.class)
                    .invoke(ammoHolder, stack);
            value = Math.max(1, value);
            int available = Math.max(1, stack.getCount() * value);
            int take = Math.min(remaining, available);

            shrinkMethod.invoke(context, take, ammoHolder, mob);
            loaded += take;
            remaining -= take;
        }
        inventory.setChanged();
        return loaded;
    }

    /** Equips modded armor and Curios-compatible backpacks from Android storage. */
    private static void equipArmorAndBackpack(Mob android) {
        SimpleContainer inventory = getAndroidInventory(android);
        if (inventory == null) {
            return;
        }

        for (int index = 0; index < inventory.getContainerSize(); index++) {
            ItemStack stack = inventory.getItem(index);
            if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem armor)) {
                continue;
            }

            EquipmentSlot slot = armor.getEquipmentSlot();
            if (slot.getType() == EquipmentSlot.Type.ARMOR
                    && android.getItemBySlot(slot).isEmpty()) {
                android.setItemSlot(slot, stack.copy());
                inventory.setItem(index, ItemStack.EMPTY);
                inventory.setChanged();
                LOGGER.info("Armor equipped on Android {}: {}", android.getUUID(), slot);
            }
        }

        for (int index = 0; index < inventory.getContainerSize(); index++) {
            ItemStack stack = inventory.getItem(index);
            if (!isBackpack(stack) || !equipBackpackCurio(android, stack)) {
                continue;
            }
            inventory.setItem(index, ItemStack.EMPTY);
            inventory.setChanged();
            LOGGER.info("Backpack equipped on Android {}", android.getUUID());
            break;
        }
    }

    private static SimpleContainer getAndroidInventory(Mob android) {
        try {
            Object value = android.getClass().getField("inventory").get(android);
            return value instanceof SimpleContainer container ? container : null;
        } catch (ReflectiveOperationException error) {
            LOGGER.debug("Android inventory is not accessible", error);
            return null;
        }
    }

    private static boolean isBackpack(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        String name = stack.getItem().getClass().getSimpleName().toLowerCase();
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase();
        return name.contains("backpack") || id.contains("backpack")
                || name.contains("rucksack") || id.contains("rucksack")
                || name.contains("satchel") || id.contains("satchel");
    }

    /**
     * Treat the worn Sophisticated Backpack as a real reserve inventory.
     * Supplies are pulled into the Android inventory before NTGL reloads or
     * equipment selection, so the backpack remains worn and visible while it
     * is actually useful instead of being decorative storage.
     */
    private static void syncBackpackSupplies(Mob android) {
        if (!ModList.get().isLoaded("sophisticatedbackpacks")) {
            return;
        }
        SimpleContainer inventory = getAndroidInventory(android);
        ItemStack backpack = getEquippedBackpackCurio(android);
        if (inventory == null || backpack.isEmpty()) {
            return;
        }

        try {
            IItemHandler handler = backpack.getCapability(ForgeCapabilities.ITEM_HANDLER)
                    .resolve().orElse(null);
            if (handler == null) {
                return;
            }

            for (int sourceSlot = 0; sourceSlot < handler.getSlots(); sourceSlot++) {
                ItemStack source = handler.getStackInSlot(sourceSlot);
                if (source.isEmpty() || !isUsefulBackpackSupply(source)) {
                    continue;
                }
                moveBackpackStack(inventory, handler, sourceSlot, source);
            }
        } catch (RuntimeException error) {
            LOGGER.debug("Sophisticated Backpack inventory is unavailable", error);
        }
    }

    private static boolean isUsefulBackpackSupply(ItemStack stack) {
        if (isBackpack(stack) || isNtglWeapon(stack)
                || stack.getItem() instanceof ArmorItem || isFuelItem(stack)) {
            return true;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase();
        String name = stack.getHoverName().getString().toLowerCase();
        String combined = id + " " + name;
        return combined.contains("ammo") || combined.contains("round")
                || combined.contains("bullet") || combined.contains("cartridge")
                || combined.contains("shell") || combined.contains("nail")
                || combined.contains("rocket") || combined.contains("rocket")
                || combined.contains("spear") || combined.contains("lead_ball")
                || combined.contains("paper_shot") || combined.contains("redstone");
    }

    private static boolean isFuelItem(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase();
        String name = stack.getHoverName().getString().toLowerCase();
        return (id.contains("redstone") || name.contains("redstone"))
                && !id.contains("redstone_torch") && !name.contains("redstone torch");
    }

    private static void moveBackpackStack(SimpleContainer inventory, IItemHandler handler,
                                          int sourceSlot, ItemStack source) {
        for (int targetSlot = 0; targetSlot < inventory.getContainerSize(); targetSlot++) {
            ItemStack target = inventory.getItem(targetSlot);
            if (!target.isEmpty()
                    && !ItemStack.isSameItemSameTags(target, source)) {
                continue;
            }
            int capacity = target.isEmpty()
                    ? Math.min(source.getMaxStackSize(), inventory.getMaxStackSize())
                    : Math.min(target.getMaxStackSize(), inventory.getMaxStackSize())
                    - target.getCount();
            if (capacity <= 0) {
                continue;
            }
            int amount = Math.min(capacity, source.getCount());
            ItemStack moved = handler.extractItem(sourceSlot, amount, false);
            if (moved.isEmpty()) {
                return;
            }
            if (target.isEmpty()) {
                inventory.setItem(targetSlot, moved);
            } else {
                target.grow(moved.getCount());
                inventory.setItem(targetSlot, target);
            }
            inventory.setChanged();
            return;
        }
    }

    /** Uses Curios' back slot without a hard dependency on Curios. */
    private static boolean equipBackpackCurio(Mob android, ItemStack backpack) {
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Object lazy = api.getMethod("getCuriosInventory", LivingEntity.class)
                    .invoke(null, android);
            Object resolved = lazy.getClass().getMethod("resolve").invoke(lazy);
            if (!(resolved instanceof Optional<?> optional) || optional.isEmpty()) {
                return false;
            }

            Object handler = optional.get();
            Object slotResult = handler.getClass()
                    .getMethod("getStacksHandler", String.class)
                    .invoke(handler, "back");
            if (!(slotResult instanceof Optional<?> slotOptional)
                    || slotOptional.isEmpty()) {
                return false;
            }

            Object stacksHandler = slotOptional.get();
            Object stacks = stacksHandler.getClass().getMethod("getStacks")
                    .invoke(stacksHandler);
            int slots = (Integer) stacks.getClass().getMethod("getSlots").invoke(stacks);
            for (int index = 0; index < slots; index++) {
                ItemStack equipped = (ItemStack) stacks.getClass()
                        .getMethod("getStackInSlot", int.class)
                        .invoke(stacks, index);
                if (equipped.isEmpty()) {
                    stacks.getClass().getMethod("setStackInSlot", int.class, ItemStack.class)
                            .invoke(stacks, index, backpack.copy());
                    stacksHandler.getClass().getMethod("update").invoke(stacksHandler);
                    return true;
                }
            }
        } catch (ReflectiveOperationException | ClassCastException error) {
            LOGGER.debug("Curios backpack slot is unavailable", error);
        }
        return false;
    }

    private static ItemStack getEquippedBackpackCurio(Mob android) {
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Object lazy = api.getMethod("getCuriosInventory", LivingEntity.class)
                    .invoke(null, android);
            Object resolved = lazy.getClass().getMethod("resolve").invoke(lazy);
            if (!(resolved instanceof Optional<?> optional) || optional.isEmpty()) {
                return ItemStack.EMPTY;
            }
            Object handler = optional.get();
            Object slotResult = handler.getClass()
                    .getMethod("getStacksHandler", String.class)
                    .invoke(handler, "back");
            if (!(slotResult instanceof Optional<?> slotOptional)
                    || slotOptional.isEmpty()) {
                return ItemStack.EMPTY;
            }
            Object stacks = slotOptional.get().getClass().getMethod("getStacks")
                    .invoke(slotOptional.get());
            int slots = (Integer) stacks.getClass().getMethod("getSlots").invoke(stacks);
            for (int index = 0; index < slots; index++) {
                ItemStack stack = (ItemStack) stacks.getClass()
                        .getMethod("getStackInSlot", int.class).invoke(stacks, index);
                if (!stack.isEmpty() && isBackpack(stack)) {
                    return stack;
                }
            }
        } catch (ReflectiveOperationException | ClassCastException error) {
            LOGGER.debug("Could not read Curios backpack slot", error);
        }
        return ItemStack.EMPTY;
    }

    private static Class<?> loadNtglWeaponType() {
        if (checkedNtgl) {
            return ntglWeaponType;
        }
        synchronized (AndroidsCompatMod.class) {
            if (!checkedNtgl) {
                try {
                    ntglWeaponType = Class.forName(NTGL_WEAPON_CLASS);
                    LOGGER.info("NTGL weapon interface detected");
                } catch (ClassNotFoundException ignored) {
                    LOGGER.warn("NTGL is not installed; ranged compatibility is disabled");
                }
                checkedNtgl = true;
            }
        }
        return ntglWeaponType;
    }

    private static boolean isValidTarget(Mob mob, LivingEntity target) {
        return !combatBlocked(mob)
                && target != null
                && target.isAlive()
                && target instanceof Enemy
                && !(target instanceof VillageDefender
                    && !VillageDefenderFaction.isAtWar(target.level())
                    && !target.getPersistentData().getBoolean("androidscompat_village_skirmish"))
                && !mob.isAlliedTo(target)
                && mob.distanceToSqr(target) <= TARGET_RANGE * TARGET_RANGE;
    }

    private static LivingEntity findNearestEnemy(Mob mob) {
        List<LivingEntity> candidates = mob.level().getEntitiesOfClass(
                LivingEntity.class,
                mob.getBoundingBox().inflate(TARGET_RANGE),
                target -> target != mob
                        && target.isAlive()
                        && !combatBlocked(mob)
                        && target instanceof Enemy
                        && !(target instanceof Player)
                        && !(target instanceof VillageDefender
                            && !VillageDefenderFaction.isAtWar(target.level())
                            && !target.getPersistentData().getBoolean("androidscompat_village_skirmish"))
                        && !mob.isAlliedTo(target));

        return candidates.stream()
                .min(Comparator.comparingDouble(mob::distanceToSqr))
                .orElse(null);
    }
}
