package com.puentenorte.androidscompat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Autonomous Puente Norte raids, field bases and player-specific Nemesis memory. */
@Mod.EventBusSubscriber(modid = AndroidsCompatMod.MOD_ID)
public final class PuenteNorteFaction {
    private static final String NEMESIS_DEATHS = "androidscompat_pn_deaths";
    private static final String NEMESIS_RANK = "androidscompat_pn_nemesis_rank";
    private static final String NEMESIS_KILLS = "androidscompat_pn_nemesis_kills";
    private static final Map<String, Long> VILLAGE_RAID_NEXT = new HashMap<>();
    private static final Map<String, BaseBuildJob> BASE_BUILDS = new HashMap<>();
    private static volatile List<Item> CACHED_CGS_WEAPONS;
    private static volatile List<Item> CACHED_CGS_ATTACHMENTS;
    private static final String WEAPON_INTERFACE =
            "com.nukateam.ntgl.common.foundation.item.interfaces.IWeapon";

    private PuenteNorteFaction() {}

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("androidscompat")
                .requires(source -> source.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("puentenorte")
                        .then(net.minecraft.commands.Commands.literal("raid")
                                .executes(ctx -> {
                                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
                                        ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Este comando requiere un jugador."));
                                        return 0;
                                    }
                                    var village = nearestVillage((ServerLevel) player.level(), player.blockPosition());
                                    if (village == null) {
                                        ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal("No se encontró una aldea cercana."));
                                        return 0;
                                    }
                                    if (!AndroidsCompatMod.PN_ENABLED.get()) {
                                        ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal("La facción Puente Norte está desactivada en la configuración del servidor."));
                                        return 0;
                                    }
                                    if (!gracePeriodElapsed(player.serverLevel())) {
                                        ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal(
                                                "Puente Norte permanece inactiva durante el período de gracia del mundo."));
                                        return 0;
                                    }
                                    VillageOccupationData occupation = VillageOccupationData.get(player.getServer());
                                    VillageOccupationData.VillageRecord record = occupation.getVillage(
                                            player.serverLevel().dimension().location(), village);
                                    if (AndroidsCompatMod.PN_VILLAGE_CONQUEST.get()
                                            && (!canStartVillageSiege(occupation, record)
                                                || player.serverLevel().getGameTime() < record.nextRaidAt())) {
                                        ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal(
                                            "Esta aldea está protegida, ya está bajo asedio o Puente Norte alcanzó su límite de expansión."));
                                        return 0;
                                    }
                                    launchRaid((ServerLevel) player.level(), player, village, false);
                                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Puente Norte inicia una incursión contra la aldea."), true);
                                    return 1;
                                }))
                        .then(net.minecraft.commands.Commands.literal("base")
                                .executes(ctx -> {
                                    if (!(ctx.getSource().getEntity() instanceof Player player)) return 0;
                                    BlockPos village = nearestVillage((ServerLevel) player.level(), player.blockPosition());
                                    if (village == null) return 0;
                                    if (!AndroidsCompatMod.PN_ENABLED.get() || !AndroidsCompatMod.PN_BUILD_BASES.get()) {
                                        ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal("La construcción de bases está desactivada en la configuración del servidor."));
                                        return 0;
                                    }
                                    if (!gracePeriodElapsed((ServerLevel) player.level())) {
                                        ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal(
                                                "No se pueden construir bases de Puente Norte durante el período de gracia."));
                                        return 0;
                                    }
                                    buildFort((ServerLevel) player.level(), village);
                                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Constructores de Puente Norte enviados al emplazamiento del puesto."), true);
                                    return 1;
                                }))
                        .then(net.minecraft.commands.Commands.literal("status")
                                .executes(ctx -> villageStatus(ctx.getSource())))));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !AndroidsCompatMod.PN_ENABLED.get()) return;
        if (AndroidsCompatMod.PN_BUILD_BASES.get()
            && event.getServer().getTickCount() % AndroidsCompatMod.PN_OUTPOST_BUILD_STEP_TICKS.get() == 0) {
            advanceBaseConstruction(event.getServer());
        }
        if (AndroidsCompatMod.PN_VILLAGE_CONQUEST.get()
                && event.getServer().getTickCount() % AndroidsCompatMod.PN_CONQUEST_TICK_INTERVAL.get() == 0) {
            advanceVillageConquests(event.getServer());
        }
        if (!AndroidsCompatMod.PN_AUTONOMOUS_RAIDS.get()
                || event.getServer().overworld().getGameTime() < AndroidsCompatMod.PN_GRACE_PERIOD_TICKS.get()
                || event.getServer().getTickCount() % AndroidsCompatMod.PN_RAID_SCAN_TICKS.get() != 0) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.dimension() == Level.NETHER || level.dimension() == Level.END) continue;
            Map<String, VillageTarget> candidates = new HashMap<>();
            VillageOccupationData occupation = VillageOccupationData.get(event.getServer());
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator()) continue;
                BlockPos village = nearestVillage(level, player.blockPosition());
                int radius = AndroidsCompatMod.PN_ACTIVATION_RADIUS.get();
                if (village != null && player.distanceToSqr(village.getX(), village.getY(), village.getZ())
                        <= (double) radius * radius) {
                    rememberExistingFactionBase(level, village);
                    String key = level.dimension().location() + ":" + village.getX() + ":" + village.getZ();
                    VillageOccupationData.VillageRecord record = occupation.getVillage(
                            level.dimension().location(), village);
                    if (canStartVillageSiege(occupation, record)
                            && level.getGameTime() >= record.nextRaidAt()) {
                        VillageTarget previous = candidates.get(key);
                        if (previous == null || player.distanceToSqr(village.getX(), village.getY(), village.getZ())
                                < previous.player.distanceToSqr(village.getX(), village.getY(), village.getZ())) {
                            candidates.put(key, new VillageTarget(village, player, false));
                        }
                    }
                }
                BlockPos home = player.getRespawnPosition();
                if (AndroidsCompatMod.PN_PLAYER_BASE_RAIDS.get() && home != null
                        && player.getRespawnDimension().equals(level.dimension()) && level.hasChunkAt(home)
                        && player.distanceToSqr(home.getX(), home.getY(), home.getZ()) <= (double) radius * radius) {
                    String homeKey = level.dimension().location() + ":player-home:" + player.getUUID();
                    if (level.getGameTime() >= VILLAGE_RAID_NEXT.getOrDefault(homeKey, 0L)) {
                        candidates.put(homeKey, new VillageTarget(home, player, true));
                    }
                }
            }
            for (Map.Entry<String, VillageTarget> entry : candidates.entrySet()) {
                VillageTarget target = entry.getValue();
                long interval = (long) AndroidsCompatMod.PN_RAID_INTERVAL_MINUTES.get() * 60L * 20L;
                if (target.playerBase) {
                    VILLAGE_RAID_NEXT.put(entry.getKey(), level.getGameTime() + interval);
                } else {
                    VillageOccupationData.VillageRecord record = occupation.getVillage(
                            level.dimension().location(), target.village);
                    if (!canStartVillageSiege(occupation, record)
                            || level.getGameTime() < record.nextRaidAt()) continue;
                    record.nextRaidAt = level.getGameTime() + interval;
                    occupation.setDirty();
                }
                if (AndroidsCompatMod.PN_BUILD_BASES.get() && !target.playerBase) buildFort(level, target.village);
                launchRaid(level, target.player, target.village, target.playerBase);
            }
        }
    }

    private static boolean canStartVillageSiege(VillageOccupationData data,
                                                VillageOccupationData.VillageRecord village) {
        return !AndroidsCompatMod.PN_VILLAGE_CONQUEST.get()
                || (!village.isOccupied() && !village.isSiegeActive()
                    && data.activeSiegeCount() < AndroidsCompatMod.PN_MAX_ACTIVE_SIEGES.get()
                    && data.occupiedCount() < AndroidsCompatMod.PN_MAX_OCCUPIED_VILLAGES.get());
    }

    private static int villageStatus(net.minecraft.commands.CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("Este comando requiere un jugador."));
            return 0;
        }
        BlockPos village = nearestVillage(player.serverLevel(), player.blockPosition());
        if (village == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("No se encontró una aldea cercana."));
            return 0;
        }
        VillageOccupationData data = VillageOccupationData.get(player.getServer());
        VillageOccupationData.VillageRecord record = data.getVillage(
                player.serverLevel().dimension().location(), village);
        String status = record.isOccupied() ? "ocupada por Puente Norte"
                : record.isSiegeActive() ? "bajo asedio" : "sin ocupar";
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                "Aldea " + village.getX() + ", " + village.getZ() + " | " + status
                    + " | control enemigo=" + record.control() + "%"), false);
        return 1;
    }

    private static void advanceVillageConquests(net.minecraft.server.MinecraftServer server) {
        VillageOccupationData data = VillageOccupationData.get(server);
        int interval = AndroidsCompatMod.PN_CONQUEST_TICK_INTERVAL.get();
        for (VillageOccupationData.VillageRecord village : data.villages()) {
            if (!village.isSiegeActive() && village.control() == 0) continue;
            ServerLevel level;
            try {
                level = server.getLevel(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        new ResourceLocation(village.dimension)));
            } catch (IllegalArgumentException invalidDimension) {
                continue;
            }
            if (level == null || !level.hasChunkAt(village.center)
                    || level.getGameTime() - village.lastProgressAt < interval) continue;
            village.lastProgressAt = level.getGameTime();

            var area = new net.minecraft.world.phys.AABB(village.center).inflate(32.0D, 16.0D, 32.0D);
            int attackers = level.getEntitiesOfClass(Mob.class, area, raider ->
                    raider.isAlive() && raider.getPersistentData()
                        .getBoolean(PuenteNorteRaider.FACTION_TAG)
                            && !raider.getPersistentData().getBoolean("androidscompat_pn_builder")
                            && !raider.isNoAi()).size();
            int players = level.getEntitiesOfClass(ServerPlayer.class, area,
                    candidate -> candidate.isAlive() && !candidate.isSpectator()).size();
            int golems = level.getEntitiesOfClass(IronGolem.class, area, Entity::isAlive).size();
            int villagers = level.getEntitiesOfClass(Villager.class, area, Entity::isAlive).size();
                int villageGuards = level.getEntitiesOfClass(VillageDefender.class, area,
                    guard -> guard.isAlive() && !guard.getPersistentData()
                        .getBoolean(VillageDefender.RECRUITED_TAG)).size();
                int defenders = players * 4 + golems * 2 + villageGuards * 3 + villagers / 4;
            boolean changed = false;

            if (village.isSiegeActive()) {
                if (attackers == 0) {
                    village.siegeActive = false;
                    village.nextRaidAt = level.getGameTime()
                        + AndroidsCompatMod.PN_CONQUEST_COOLDOWN_TICKS.get();
                    notifyVillage(level, village.center, "Aldea defendida: Puente Norte se retira.");
                    changed = true;
                } else if (attackers >= AndroidsCompatMod.PN_CONQUEST_MIN_RAIDERS.get()
                    && attackers > defenders) {
                    village.control = Math.min(100, village.control
                        + AndroidsCompatMod.PN_CONQUEST_POINTS.get());
                    changed = true;
                    if (village.control >= 100) {
                        village.siegeActive = false;
                        village.nextRaidAt = level.getGameTime()
                                + AndroidsCompatMod.PN_CONQUEST_COOLDOWN_TICKS.get();
                        notifyVillage(level, village.center,
                                "¡Puente Norte ha ocupado la aldea! Los jugadores aún pueden liberarla.");
                    }
                } else if (defenders > attackers && village.control > 0) {
                    village.control = Math.max(0, village.control
                        - AndroidsCompatMod.PN_CONQUEST_REPEL_POINTS.get());
                    changed = true;
                }
            } else if (village.isOccupied() && attackers == 0 && players > 0) {
                village.control = Math.max(0, village.control
                    - AndroidsCompatMod.PN_CONQUEST_REPEL_POINTS.get());
                changed = true;
                if (village.control == 0) {
                    village.nextRaidAt = level.getGameTime()
                        + AndroidsCompatMod.PN_CONQUEST_COOLDOWN_TICKS.get();
                    notifyVillage(level, village.center,
                        "¡Aldea liberada! Puente Norte no podrá asediarla durante un tiempo.");
                }
            }

            if (changed) data.setDirty();
            if (village.isSiegeActive() || village.control() > 0) {
                String message = village.isOccupied()
                    ? "Aldea ocupada | Control Puente Norte " + village.control() + "% | elimina la guarnición para liberar"
                    : "Asedio Puente Norte | control " + village.control() + "% | atacantes "
                        + attackers + " / defensores " + defenders;
                level.getEntitiesOfClass(ServerPlayer.class,
                        new net.minecraft.world.phys.AABB(village.center).inflate(48.0D),
                        player -> player.isAlive() && !player.isSpectator())
                    .forEach(player -> player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(message), true));
            }
        }
    }

    private static void notifyVillage(ServerLevel level, BlockPos center, String message) {
        level.getEntitiesOfClass(ServerPlayer.class,
                    new net.minecraft.world.phys.AABB(center).inflate(64.0D),
                    player -> player.isAlive() && !player.isSpectator())
                .forEach(player -> player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(message), false));
    }

    private record VillageTarget(BlockPos village, ServerPlayer player, boolean playerBase) {}

    private static boolean gracePeriodElapsed(ServerLevel level) {
        return level.getServer().overworld().getGameTime() >= AndroidsCompatMod.PN_GRACE_PERIOD_TICKS.get();
    }

    @SubscribeEvent
    public static void rememberDeath(LivingDeathEvent event) {
        if (!AndroidsCompatMod.PN_ENABLED.get() || !AndroidsCompatMod.PN_NEMESIS.get()) return;
        Entity attacker = event.getSource().getEntity();
        if (event.getEntity().getPersistentData().getBoolean(PuenteNorteRaider.FACTION_TAG)
                && attacker instanceof ServerPlayer player
                && (event.getEntity().getPersistentData().getBoolean(PuenteNorteRaider.NEMESIS_TAG)
                    || event.getEntity() instanceof PuenteNorteBoss)) {
            rememberNemesisDefeat(player, event.getEntity());
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(attacker instanceof Mob raider)
                || !raider.getPersistentData().getBoolean(PuenteNorteRaider.FACTION_TAG)) return;
        int deaths = player.getPersistentData().getInt(NEMESIS_DEATHS) + 1;
        player.getPersistentData().putInt(NEMESIS_DEATHS, deaths);
        raider.getPersistentData().putBoolean(PuenteNorteRaider.NEMESIS_TAG, true);
        String title = raider instanceof PuenteNorteBoss boss ? boss.getCustomName().getString() : "Némesis";
        raider.getPersistentData().putString("androidscompat_pn_target", player.getStringUUID());
        if (raider instanceof PuenteNorteSoldier soldier) {
            promoteSoldier(soldier, player);
        } else {
            promoteFactionAndroid(raider, player, title);
        }
    }

    private static void promoteFactionAndroid(Mob android, ServerPlayer player, String title) {
        var data = android.getPersistentData();
        int rank = Math.min(AndroidsCompatMod.PN_MAX_NEMESIS_RANK.get(), Math.max(1,
                data.getInt(PuenteNorteRaider.RANK_TAG)) + AndroidsCompatMod.PN_RANK_UPS_PER_KILL.get());
        data.putInt(PuenteNorteRaider.RANK_TAG, rank);
        data.putBoolean(PuenteNorteRaider.NEMESIS_TAG, true);
        data.putString("androidscompat_pn_target", player.getStringUUID());
        android.setPersistenceRequired();
        var health = android.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(36.0D + (rank - 1) * AndroidsCompatMod.PN_HEALTH_PER_RANK.get());
            android.setHealth((float) health.getValue());
        }
        var armor = android.getAttribute(Attributes.ARMOR);
        if (armor != null) armor.setBaseValue(rank * AndroidsCompatMod.PN_ARMOR_PER_RANK.get());
        android.setCustomName(net.minecraft.network.chat.Component.literal(title + " · rango " + rank
                + " · Némesis de " + player.getGameProfile().getName()));
        android.setCustomNameVisible(true);
    }

    private static void rememberNemesisDefeat(ServerPlayer player, LivingEntity fallen) {
        var data = player.getPersistentData();
        data.putInt(NEMESIS_KILLS, data.getInt(NEMESIS_KILLS) + 1);
        net.minecraft.nbt.ListTag history = data.getList("androidscompat_pn_nemesis_history",
                net.minecraft.nbt.Tag.TAG_COMPOUND);
        net.minecraft.nbt.CompoundTag record = new net.minecraft.nbt.CompoundTag();
        record.putString("name", fallen.getCustomName() == null ? "Némesis" : fallen.getCustomName().getString());
        record.putInt("rank", fallen.getPersistentData().getInt(PuenteNorteRaider.RANK_TAG));
        record.putString("faction", fallen instanceof PuenteNorteBoss boss ? boss.getBossKind()
                : fallen.getPersistentData().getString(PuenteNorteSoldier.SUBFACTION_TAG));
        if (fallen instanceof PuenteNorteSoldier soldier) {
            record.putString("type", soldier.getArchetype());
            record.putString("adaptation", soldier.getPersistentData().getString(PuenteNorteSoldier.TRAIT_TAG));
            record.putInt("adaptationRank", soldier.getPersistentData().getInt(PuenteNorteSoldier.TRAIT_LEVEL_TAG));
            record.putString("adaptationSummary", adaptationSummary(soldier));
        }
        record.putLong("time", player.serverLevel().getGameTime());
        history.add(record);
        while (history.size() > 20) history.remove(0);
        data.put("androidscompat_pn_nemesis_history", history);
        player.giveExperiencePoints(Math.max(10, fallen.getPersistentData()
                .getInt(PuenteNorteRaider.RANK_TAG) * 20));
    }

    @SubscribeEvent
    public static void applySubfactionCombat(LivingDamageEvent event) {
        if (!AndroidsCompatMod.PN_ENABLED.get()) return;
        if (AndroidsCompatMod.PN_PLAYER_THEFT.get()
                && event.getEntity() instanceof ServerPlayer player
                && event.getSource().getEntity() instanceof LivingEntity attacker
                && attacker.getPersistentData().getBoolean(PuenteNorteRaider.FACTION_TAG)) {
            attemptTheft(player, attacker);
        }
        if (event.getEntity() instanceof PuenteNorteSoldier victim
                && event.getSource().getEntity() instanceof Player) {
            if (AndroidsCompatMod.PN_NEMESIS.get() && AndroidsCompatMod.PN_ADAPTATION.get()) {
                adaptToPlayerAttack(victim, event);
            }
        }
        if (event.getSource().getEntity() instanceof PuenteNorteSoldier soldier) {
            String faction = soldier.getPersistentData().getString(PuenteNorteSoldier.SUBFACTION_TAG);
            if ("bacterio".equals(faction) && "apoyo".equals(soldier.getVariant())) {
                event.getEntity().addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.POISON, 60, 0));
            } else if ("mortadelo".equals(faction)
                    && soldier.getPersistentData().getInt(PuenteNorteRaider.RANK_TAG) >= 3) {
                event.getEntity().addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.WEAKNESS, 80, 0));
            }
        }
    }

    private static void attemptTheft(ServerPlayer player, LivingEntity thief) {
        if (player.getRandom().nextDouble() >= AndroidsCompatMod.PN_THEFT_CHANCE.get()) return;
        var data = player.getPersistentData();
        long now = player.level().getGameTime();
        if (now - data.getLong("androidscompat_pn_last_theft") < 1200L) return;
        List<Integer> candidates = new java.util.ArrayList<>();
        for (int slot = 9; slot < player.getInventory().items.size(); slot++) {
            ItemStack stack = player.getInventory().items.get(slot);
            if (!stack.isEmpty() && isValuableLoot(stack)) candidates.add(slot);
        }
        if (candidates.isEmpty()) return;
        int slot = candidates.get(player.getRandom().nextInt(candidates.size()));
        ItemStack inventoryStack = player.getInventory().items.get(slot);
        ItemStack stolen = inventoryStack.copyWithCount(1);
        inventoryStack.shrink(1);
        player.getInventory().setChanged();
        data.putLong("androidscompat_pn_last_theft", now);
        net.minecraft.nbt.ListTag stolenItems = thief.getPersistentData().getList(
                "androidscompat_pn_stolen_items", net.minecraft.nbt.Tag.TAG_COMPOUND);
        stolenItems.add(stolen.save(new net.minecraft.nbt.CompoundTag()));
        thief.getPersistentData().put("androidscompat_pn_stolen_items", stolenItems);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "§cUn saqueador de Puente Norte te robó " + stolen.getHoverName().getString() + "."), true);
    }

    private static boolean isValuableLoot(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return id.startsWith("immersive_aircraft:") || id.startsWith("man_of_many_planes:")
                || id.equals("minecraft:stick") || id.equals("minecraft:string")
                || id.endsWith("_planks") || id.equals("minecraft:leather")
                || id.endsWith("_log") || id.endsWith("_wood") || id.endsWith("_stem")
                || id.endsWith("_carpet") || id.equals("minecraft:blast_furnace")
                || id.equals("minecraft:cobblestone") || id.equals("minecraft:piston")
                || id.equals("minecraft:lever") || id.equals("minecraft:blaze_rod")
                || id.equals("minecraft:slime_ball") || id.equals("minecraft:brick")
                || id.equals("minecraft:magma_cream") || id.equals("minecraft:nether_brick")
                || id.equals("minecraft:iron_nugget") || id.equals("minecraft:copper_ingot")
                || id.equals("minecraft:diamond") || id.equals("minecraft:emerald")
                || id.equals("minecraft:gold_ingot") || id.equals("minecraft:iron_ingot")
                || id.equals("minecraft:netherite_ingot") || id.equals("minecraft:ancient_debris")
                || id.equals("minecraft:diamond_block") || id.equals("minecraft:emerald_block")
                || id.equals("minecraft:gold_block") || id.equals("minecraft:iron_block")
                || id.equals("minecraft:netherite_scrap") || id.equals("minecraft:amethyst_shard");
    }

    @SubscribeEvent
    public static void dropStolenLoot(LivingDropsEvent event) {
        LivingEntity thief = event.getEntity();
        net.minecraft.nbt.ListTag stolen = thief.getPersistentData().getList(
                "androidscompat_pn_stolen_items", net.minecraft.nbt.Tag.TAG_COMPOUND);
        if (stolen.isEmpty() || !(thief.level() instanceof ServerLevel level)) return;
        for (int i = 0; i < stolen.size(); i++) {
            ItemStack stack = ItemStack.of(stolen.getCompound(i));
            if (!stack.isEmpty()) thief.spawnAtLocation(stack);
        }
        thief.getPersistentData().remove("androidscompat_pn_stolen_items");
    }

    @SubscribeEvent
    public static void trackNemesisTarget(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof PuenteNorteSoldier soldier)
                || !(soldier.level() instanceof ServerLevel level)
                || soldier.tickCount % 10 != 0) return;
        if ("mini_boss".equals(soldier.getArchetype())) {
            for (PuenteNorteRaider ally : level.getEntitiesOfClass(PuenteNorteRaider.class,
                    soldier.getBoundingBox().inflate(10.0D), entity -> entity != soldier && entity.isAlive())) {
                ally.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 50, 0));
            }
        }
        LivingEntity current = soldier.getTarget();
        if (current != null && current.isAlive() && !current.isSpectator()
                && soldier.distanceToSqr(current) <= 64.0D * 64.0D) {
            if (current instanceof ServerPlayer player
                    && soldier.getPersistentData().getBoolean(PuenteNorteRaider.NEMESIS_TAG)
                    && soldier.distanceToSqr(player) <= 24.0D * 24.0D) {
                alertNemesisTarget(soldier, player, level);
            }
            if (soldier.distanceToSqr(current) > 32.0D * 32.0D) {
                soldier.getNavigation().moveTo(current,
                        "stalker".equals(soldier.getArchetype()) ? 1.35D : 1.0D);
            }
            return;
        }

        LivingEntity target = null;
        if (soldier.getPersistentData().getBoolean(PuenteNorteRaider.NEMESIS_TAG)) {
            String targetId = soldier.getPersistentData().getString("androidscompat_pn_target");
            try {
                Entity preferred = targetId.isEmpty() ? null
                        : level.getEntity(java.util.UUID.fromString(targetId));
                if (preferred instanceof ServerPlayer player && player.isAlive()
                        && !player.isSpectator()) target = player;
            } catch (IllegalArgumentException ignored) { }
        }
        if (target == null) {
            target = level.getEntitiesOfClass(ServerPlayer.class,
                            soldier.getBoundingBox().inflate(64.0D), player -> player.isAlive()
                                    && !player.isSpectator())
                    .stream().min(Comparator.comparingDouble(soldier::distanceToSqr)).orElse(null);
        }
        if (target == null) {
            target = level.getEntitiesOfClass(Mob.class, soldier.getBoundingBox().inflate(48.0D), mob ->
                            AndroidsCompatMod.isAndroidEntity(mob)
                                    && !mob.getPersistentData().getBoolean(PuenteNorteRaider.FACTION_TAG)
                                    && mob.isAlive())
                    .stream().min(Comparator.comparingDouble(soldier::distanceToSqr)).orElse(null);
        }
        soldier.setTarget(target);
        if (target instanceof ServerPlayer player
                && soldier.getPersistentData().getBoolean(PuenteNorteRaider.NEMESIS_TAG)
                && soldier.distanceToSqr(player) <= 24.0D * 24.0D) {
            alertNemesisTarget(soldier, player, level);
        }
        if (target != null && soldier.distanceToSqr(target) > 6.0D * 6.0D) {
            double speed = "stalker".equals(soldier.getArchetype()) ? 1.35D : 1.0D;
            soldier.getNavigation().moveTo(target, speed);
        }
    }

    private static void alertNemesisTarget(PuenteNorteSoldier soldier, ServerPlayer player,
                                           ServerLevel level) {
        long now = level.getGameTime();
        long lastAlert = player.getPersistentData().getLong("androidscompat_pn_last_hunt_alert");
        if (now - lastAlert < 200L) return;
        player.getPersistentData().putLong("androidscompat_pn_last_hunt_alert", now);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "¡Tu Némesis " + soldier.getName().getString() + " te ha localizado!"), true);
    }

    private static void promoteSoldier(PuenteNorteSoldier soldier, ServerPlayer player) {
        var data = soldier.getPersistentData();
        int rank = Math.min(AndroidsCompatMod.PN_MAX_NEMESIS_RANK.get(), Math.max(1,
                data.getInt(PuenteNorteRaider.RANK_TAG)) + AndroidsCompatMod.PN_RANK_UPS_PER_KILL.get());
        data.putInt(PuenteNorteRaider.RANK_TAG, rank);
        data.putBoolean(PuenteNorteRaider.NEMESIS_TAG, true);
        data.putString("androidscompat_pn_target", player.getStringUUID());
        soldier.setPersistenceRequired();
        String archetype = rank >= AndroidsCompatMod.PN_MINIBOSS_RANK.get() ? "mini_boss"
                : rank >= AndroidsCompatMod.PN_STALKER_RANK.get() ? "stalker" : "normal";
        data.putString(PuenteNorteSoldier.ARCHETYPE_TAG, archetype);
        applyNemesisGrowth(soldier, rank, archetype);
        String subfaction = data.getString(PuenteNorteSoldier.SUBFACTION_TAG);
        String subfactionName = switch (subfaction) {
            case "superintendente" -> "Superintendente";
            case "filemon" -> "Filemón";
            case "bacterio" -> "Bacterio";
            case "mortadelo" -> "Mortadelo";
            default -> "Puente Norte";
        };
        String title = rank >= AndroidsCompatMod.PN_MINIBOSS_RANK.get() ? "Mini jefe"
                : rank >= AndroidsCompatMod.PN_STALKER_RANK.get() ? "Stalker" : rank > 1 ? "Veterano" : "Soldado";
        soldier.setCustomName(net.minecraft.network.chat.Component.literal(title + " " + subfactionName
                + " · rango " + rank + " · Némesis de " + player.getGameProfile().getName()));
        soldier.setCustomNameVisible(true);
    }

    private static void applyNemesisGrowth(PuenteNorteSoldier soldier, int rank, String archetype) {
        float oldHealth = soldier.getHealth();
        double oldMaxHealth = soldier.getMaxHealth();
        double maxHealth = 28.0D + (rank - 1) * AndroidsCompatMod.PN_HEALTH_PER_RANK.get();
        double armor = rank * AndroidsCompatMod.PN_ARMOR_PER_RANK.get() + ("apoyo".equals(soldier.getVariant()) ? 3.0D : 0.0D);
        double speed = "stalker".equals(archetype) ? AndroidsCompatMod.PN_STALKER_SPEED.get()
                : "mini_boss".equals(archetype) ? AndroidsCompatMod.PN_STALKER_SPEED.get() * 0.9D : 0.31D + rank * 0.012D;
        soldier.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
        soldier.getAttribute(Attributes.ARMOR).setBaseValue(armor);
        soldier.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
        double rankHealthBonus = Math.max(0.0D, maxHealth - oldMaxHealth);
        soldier.setHealth((float) Math.min(maxHealth, oldHealth + rankHealthBonus * 0.25D));
    }

    private static void adaptToPlayerAttack(PuenteNorteSoldier soldier, LivingDamageEvent event) {
        var data = soldier.getPersistentData();
        boolean fire = event.getSource().is(DamageTypeTags.IS_FIRE);
        boolean projectile = !fire && event.getSource().is(DamageTypeTags.IS_PROJECTILE);
        String method = fire ? "fuego" : projectile ? "distancia" : "cuerpo a cuerpo";
        String levelTag = fire ? PuenteNorteSoldier.FIRE_ADAPTATION_TAG
            : projectile ? PuenteNorteSoldier.PROJECTILE_ADAPTATION_TAG
            : PuenteNorteSoldier.MELEE_ADAPTATION_TAG;
        String hitsTag = fire ? PuenteNorteSoldier.FIRE_HIT_COUNT_TAG
            : projectile ? PuenteNorteSoldier.PROJECTILE_HIT_COUNT_TAG
            : PuenteNorteSoldier.MELEE_HIT_COUNT_TAG;
        int hits = data.getInt(hitsTag) + 1;
        data.putInt(hitsTag, hits);
        data.putString(PuenteNorteSoldier.LAST_METHOD_TAG, method);
        data.putInt(PuenteNorteSoldier.METHOD_COUNT_TAG, hits);
        if (hits >= AndroidsCompatMod.PN_ADAPTATION_HITS.get()) {
            int traitLevel = Math.min(3, data.getInt(levelTag) + 1);
            data.putInt(levelTag, traitLevel);
            data.putString(PuenteNorteSoldier.TRAIT_TAG, method);
            data.putInt(PuenteNorteSoldier.TRAIT_LEVEL_TAG, traitLevel);
            data.putInt(hitsTag, 0);
            data.putInt(PuenteNorteSoldier.METHOD_COUNT_TAG, 0);
        }
        // Migrate older Nemesis that had only one learned resistance.
        int legacyLevel = method.equals(data.getString(PuenteNorteSoldier.TRAIT_TAG))
            ? data.getInt(PuenteNorteSoldier.TRAIT_LEVEL_TAG) : 0;
        int resistanceLevel = Math.max(data.getInt(levelTag), legacyLevel);
        if (resistanceLevel <= 0) return;
        float maximum = AndroidsCompatMod.PN_MAX_ADAPTATION_RESISTANCE.get().floatValue();
        float resistance = Math.min(maximum, resistanceLevel * maximum / 3.0F);
        event.setAmount(event.getAmount() * (1.0F - resistance));
    }

    static String adaptationSummary(PuenteNorteSoldier soldier) {
        var data = soldier.getPersistentData();
        List<String> traits = new ArrayList<>();
        int fire = Math.max(data.getInt(PuenteNorteSoldier.FIRE_ADAPTATION_TAG),
            "fuego".equals(data.getString(PuenteNorteSoldier.TRAIT_TAG))
                ? data.getInt(PuenteNorteSoldier.TRAIT_LEVEL_TAG) : 0);
        int projectile = Math.max(data.getInt(PuenteNorteSoldier.PROJECTILE_ADAPTATION_TAG),
            "distancia".equals(data.getString(PuenteNorteSoldier.TRAIT_TAG))
                ? data.getInt(PuenteNorteSoldier.TRAIT_LEVEL_TAG) : 0);
        int melee = Math.max(data.getInt(PuenteNorteSoldier.MELEE_ADAPTATION_TAG),
            "cuerpo a cuerpo".equals(data.getString(PuenteNorteSoldier.TRAIT_TAG))
                ? data.getInt(PuenteNorteSoldier.TRAIT_LEVEL_TAG) : 0);
        if (fire > 0) traits.add("fuego R" + fire);
        if (projectile > 0) traits.add("proyectiles R" + projectile);
        if (melee > 0) traits.add("melé R" + melee);
        return String.join(", ", traits);
    }

    static BlockPos nearestVillage(ServerLevel level, BlockPos origin) {
        try {
            return level.findNearestMapStructure(StructureTags.VILLAGE, origin, 96, false);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void launchRaid(ServerLevel level, ServerPlayer player, BlockPos village, boolean playerBase) {
        if (!gracePeriodElapsed(level)) return;
        if (!playerBase && AndroidsCompatMod.PN_VILLAGE_CONQUEST.get()) {
            VillageOccupationData data = VillageOccupationData.get(level.getServer());
            VillageOccupationData.VillageRecord occupation = data.getVillage(
                    level.dimension().location(), village);
            occupation.siegeActive = true;
            occupation.lastProgressAt = level.getGameTime();
            data.setDirty();
        }
        if (playerBase) player.getPersistentData().putInt("androidscompat_pn_base_loot_count", 0);
        int rememberedDeaths = player.getPersistentData().getInt(NEMESIS_DEATHS);
        int nemesisKills = player.getPersistentData().getInt(NEMESIS_KILLS);
        int pressure = AndroidsCompatMod.PN_NEMESIS.get() ? rememberedDeaths + nemesisKills : 0;
        int nemesisRank = AndroidsCompatMod.PN_NEMESIS.get()
                ? Math.min(AndroidsCompatMod.PN_MAX_NEMESIS_RANK.get(), 1 + pressure / 3) : 1;
        String subfaction = subfactionFor(pressure);
        int strength = Math.min(AndroidsCompatMod.PN_MAX_SQUAD_SIZE.get(),
                (int) Math.ceil(squadSize(subfaction, nemesisRank) * AndroidsCompatMod.PN_SQUAD_SIZE_MULTIPLIER.get()));
        if (AndroidsCompatMod.PN_BOSSES.get() && pressure >= 2
                && level.getRandom().nextDouble() < AndroidsCompatMod.PN_BOSS_CHANCE.get()) {
            PuenteNorteBoss boss = PuenteNorteEntities.BOSS.get().create(level);
            if (boss != null) {
                String bossKind = subfaction;
                boss.setBossKind(bossKind);
                BlockPos pos = spawnPoint(level, village, 16, 24);
                boss.moveTo(pos, level.getRandom().nextFloat() * 360.0F, 0.0F);
                boss.finalizeSpawn(level, level.getCurrentDifficultyAt(pos),
                        net.minecraft.world.entity.MobSpawnType.EVENT, null, null);
                boss.getPersistentData().putInt(PuenteNorteRaider.RANK_TAG, nemesisRank);
                double baseHealth = switch (bossKind) {
                    case "superintendente" -> 240.0D;
                    case "filemon" -> 185.0D;
                    case "bacterio" -> 195.0D;
                    default -> 220.0D;
                };
                double baseArmor = switch (bossKind) {
                    case "superintendente" -> 20.0D;
                    case "filemon" -> 8.0D;
                    case "bacterio" -> 10.0D;
                    default -> 14.0D;
                };
                boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(baseHealth + nemesisRank * 20.0D);
                boss.getAttribute(Attributes.ARMOR).setBaseValue(baseArmor + nemesisRank * 2.0D);
                boss.setHealth((float) boss.getAttribute(Attributes.MAX_HEALTH).getValue());
                boss.getPersistentData().putString("androidscompat_pn_target", player.getStringUUID());
                configureBaseRaid(boss, village, playerBase);
                boss.setCustomName(net.minecraft.network.chat.Component.literal(boss.getCustomName().getString()
                        + " · Némesis de " + player.getGameProfile().getName()));
                boss.setTarget(player);
                level.addFreshEntity(boss);
            }
        }
        int lineCount = Math.round(strength * lineRatio(subfaction));
        for (int i = 0; i < strength; i++) {
            PuenteNorteSoldier soldier = PuenteNorteEntities.SOLDIER.get().create(level);
            if (soldier == null) continue;
            String variant = i < lineCount ? "linea" : "apoyo";
            String archetype = AndroidsCompatMod.PN_NEMESIS.get()
                    && level.getRandom().nextFloat() < stalkerRatio(subfaction)
                    ? "stalker" : "normal";
            soldier.configure(variant, subfaction, archetype, nemesisRank);
            BlockPos pos = spawnPoint(level, village, 12, 28);
            soldier.moveTo(pos, level.getRandom().nextFloat() * 360.0F, 0.0F);
            soldier.finalizeSpawn(level, level.getCurrentDifficultyAt(pos),
                    net.minecraft.world.entity.MobSpawnType.EVENT, null, null);
            soldier.getAttribute(Attributes.MAX_HEALTH).setBaseValue(
                    24.0D + nemesisRank * 3.0D + ("apoyo".equals(variant) ? 4.0D : 0.0D));
            soldier.getAttribute(Attributes.ARMOR).setBaseValue(
                    nemesisRank + ("apoyo".equals(variant) ? 3.0D : 0.0D));
            soldier.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(
                    "stalker".equals(archetype) ? 0.39D : 0.31D);
            soldier.setHealth((float) soldier.getAttribute(Attributes.MAX_HEALTH).getValue());
            soldier.getPersistentData().putString("androidscompat_pn_target", player.getStringUUID());
            configureBaseRaid(soldier, village, playerBase);
            soldier.setTarget(player);
            level.addFreshEntity(soldier);
        }
        spawnFactionAndroid(level, village, player, subfaction, false, playerBase);
        if (pressure >= 3 && level.getRandom().nextFloat() < 0.5F) {
            spawnFactionAndroid(level, village, player, subfaction, true, playerBase);
        }
        if (AndroidsCompatMod.PN_AIR_SUPPORT.get()
                && level.getRandom().nextDouble() < AndroidsCompatMod.PN_AIR_SUPPORT_CHANCE.get()) {
            launchAircraftSupport(level, player, village, subfaction, nemesisRank, playerBase);
        }
    }

    private static void spawnFactionAndroid(ServerLevel level, BlockPos village, ServerPlayer target,
                                            String subfaction, boolean rogue, boolean playerBase) {
        Mob android = rogue ? PuenteNorteEntities.ROGUE_ANDROID.get().create(level)
                : PuenteNorteEntities.BASIC_ANDROID.get().create(level);
        if (android == null) return;
        BlockPos pos = spawnPoint(level, village, 10, 22);
        android.moveTo(pos, level.getRandom().nextFloat() * 360.0F, 0.0F);
        android.finalizeSpawn(level, level.getCurrentDifficultyAt(pos),
                net.minecraft.world.entity.MobSpawnType.EVENT, null, null);
        android.getPersistentData().putBoolean(PuenteNorteRaider.FACTION_TAG, true);
        android.getPersistentData().putBoolean("androidscompat_pn_enemy_android", true);
        android.getPersistentData().putString("androidscompat_pn_target", target.getStringUUID());
        configureBaseRaid(android, village, playerBase);
        android.setTarget(target);
        android.setPersistenceRequired();
        android.setCustomName(net.minecraft.network.chat.Component.literal(
                (rogue ? "Rogue" : "Androide básico") + " de Puente Norte · " + subfaction));
        android.setCustomNameVisible(true);
        level.addFreshEntity(android);
    }

    private static void launchAircraftSupport(ServerLevel level, ServerPlayer target, BlockPos village,
                                               String subfaction, int rank, boolean playerBase) {
        if (!ModList.get().isLoaded("immersive_aircraft")) return;
        var search = new net.minecraft.world.phys.AABB(village).inflate(128.0D, 48.0D, 128.0D);
        Entity aircraft = level.getEntitiesOfClass(Entity.class, search,
                        AndroidsCompatMod::hasCompatibleAircraftWeapons).stream()
                .filter(candidate -> candidate.getPassengers().isEmpty())
                .min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(village.getX(),
                        village.getY(), village.getZ())))
                .orElse(null);
        if (aircraft == null) return;
        PuenteNorteSoldier pilot = PuenteNorteEntities.SOLDIER.get().create(level);
        if (pilot == null) return;
        pilot.configure("apoyo", subfaction, "stalker", Math.max(1, rank));
        pilot.getPersistentData().putInt(PuenteNorteRaider.RANK_TAG, 1);
        pilot.getPersistentData().putString("androidscompat_pn_target", target.getStringUUID());
        configureBaseRaid(pilot, village, playerBase);
        pilot.moveTo(aircraft.position().x, aircraft.position().y, aircraft.position().z,
                aircraft.getYRot(), 0.0F);
        pilot.finalizeSpawn(level, level.getCurrentDifficultyAt(aircraft.blockPosition()),
                net.minecraft.world.entity.MobSpawnType.EVENT, null, null);
        if (!AndroidsCompatMod.assignPuenteNorteAircraftPilot(pilot, aircraft)) return;
        pilot.setTarget(target);
        pilot.setPersistenceRequired();
        pilot.setCustomName(net.minecraft.network.chat.Component.literal("Piloto de Puente Norte"));
        pilot.setCustomNameVisible(true);
        level.addFreshEntity(pilot);
    }

    private static void configureBaseRaid(LivingEntity raider, BlockPos home, boolean playerBase) {
        if (!playerBase) return;
        var data = raider.getPersistentData();
        data.putBoolean("androidscompat_pn_base_raid", true);
        data.putInt("androidscompat_pn_base_x", home.getX());
        data.putInt("androidscompat_pn_base_y", home.getY());
        data.putInt("androidscompat_pn_base_z", home.getZ());
    }

    @SubscribeEvent
    public static void plunderPlayerBase(LivingEvent.LivingTickEvent event) {
        if (!AndroidsCompatMod.PN_ENABLED.get() || !AndroidsCompatMod.PN_PLAYER_BASE_RAIDS.get()
                || !AndroidsCompatMod.PN_PLAYER_THEFT.get()
                || !(event.getEntity() instanceof Mob raider)
                || !(raider.level() instanceof ServerLevel level)
                || !raider.getPersistentData().getBoolean("androidscompat_pn_base_raid")
                || raider.getPersistentData().getBoolean("androidscompat_pn_looted_base")
                || raider.tickCount % 20 != 0) return;
        int homeX = raider.getPersistentData().getInt("androidscompat_pn_base_x");
        int homeY = raider.getPersistentData().getInt("androidscompat_pn_base_y");
        int homeZ = raider.getPersistentData().getInt("androidscompat_pn_base_z");
        ServerPlayer owner = null;
        String target = raider.getPersistentData().getString("androidscompat_pn_target");
        try {
            Entity entity = level.getEntity(java.util.UUID.fromString(target));
            if (entity instanceof ServerPlayer player && player.isAlive()) owner = player;
        } catch (IllegalArgumentException ignored) { }
        if (owner == null || owner.getPersistentData().getInt("androidscompat_pn_base_loot_count")
                >= AndroidsCompatMod.PN_BASE_LOOT_LIMIT.get()) return;
        BlockPos chest = null;
        Container container = null;
        outer: for (BlockPos pos : BlockPos.betweenClosed(homeX - 8, homeY - 4, homeZ - 8,
                homeX + 8, homeY + 4, homeZ + 8)) {
            if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof Container candidate)) continue;
            for (int slot = 0; slot < candidate.getContainerSize(); slot++) {
                if (isValuableLoot(candidate.getItem(slot))) {
                    chest = pos.immutable();
                    container = candidate;
                    break outer;
                }
            }
        }
        if (chest == null || container == null) return;
        if (raider.distanceToSqr(chest.getX() + 0.5D, chest.getY(), chest.getZ() + 0.5D) > 9.0D) {
            raider.setTarget(null);
            raider.getNavigation().moveTo(chest.getX() + 0.5D, chest.getY(), chest.getZ() + 0.5D, 1.0D);
            return;
        }
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stored = container.getItem(slot);
            if (!isValuableLoot(stored)) continue;
            ItemStack stolen = stored.copyWithCount(1);
            stored.shrink(1);
            container.setChanged();
            owner.getPersistentData().putInt("androidscompat_pn_base_loot_count",
                    owner.getPersistentData().getInt("androidscompat_pn_base_loot_count") + 1);
            net.minecraft.nbt.ListTag stolenItems = raider.getPersistentData().getList(
                    "androidscompat_pn_stolen_items", net.minecraft.nbt.Tag.TAG_COMPOUND);
            stolenItems.add(stolen.save(new net.minecraft.nbt.CompoundTag()));
            raider.getPersistentData().put("androidscompat_pn_stolen_items", stolenItems);
            owner.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§cPuente Norte saquea " + stolen.getHoverName().getString() + " de tu base."), true);
            raider.getPersistentData().putBoolean("androidscompat_pn_looted_base", true);
            try {
                raider.setTarget(owner);
            } catch (RuntimeException ignored) { }
            return;
        }
    }

    @SubscribeEvent
    public static void returnFactionLoot(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob raider) || raider.isPassenger()
                || !(raider.level() instanceof ServerLevel level) || raider.tickCount % 20 != 0
                || !raider.getPersistentData().getBoolean(PuenteNorteRaider.FACTION_TAG)) return;
        net.minecraft.nbt.ListTag cargo = raider.getPersistentData().getList(
                "androidscompat_pn_stolen_items", net.minecraft.nbt.Tag.TAG_COMPOUND);
        if (cargo.isEmpty()) return;
        BaseBuildJob base = BASE_BUILDS.values().stream()
                .filter(job -> job.dimension.equals(level.dimension().location().toString())
                        && job.next >= job.plan.size())
                .min(Comparator.comparingDouble(job -> raider.distanceToSqr(job.center.getX(),
                        job.center.getY(), job.center.getZ())))
                .orElse(null);
        if (base == null) return;
        BlockPos storePos = base.center.offset(0, 0, 2);
        if (raider.distanceToSqr(storePos.getX() + 0.5D, storePos.getY(), storePos.getZ() + 0.5D) > 9.0D) {
            raider.setTarget(null);
            raider.getNavigation().moveTo(storePos.getX() + 0.5D, storePos.getY(),
                    storePos.getZ() + 0.5D, 1.0D);
            return;
        }
        if (!(level.getBlockEntity(storePos) instanceof Container stockpile)) return;
        for (int i = 0; i < cargo.size(); i++) {
            ItemStack item = ItemStack.of(cargo.getCompound(i));
            if (!item.isEmpty()) {
                ItemStack leftover = insertIntoContainer(stockpile, item);
                if (!leftover.isEmpty()) raider.spawnAtLocation(leftover);
            }
        }
        stockpile.setChanged();
        raider.getPersistentData().remove("androidscompat_pn_stolen_items");
        raider.getPersistentData().putBoolean("androidscompat_pn_looted_base", false);
    }

    private static String subfactionFor(int progress) {
        if (progress >= 9 && progress % 3 == 0) return "mortadelo";
        if (progress < 2) return "superintendente";
        return switch (Math.floorMod(progress - 2, 3)) {
            case 0 -> "superintendente";
            case 1 -> "filemon";
            default -> "bacterio";
        };
    }

    private static int squadSize(String subfaction, int rank) {
        int base = switch (subfaction) {
            case "superintendente" -> 6;
            case "filemon" -> 7;
            case "bacterio" -> 5;
            default -> 8;
        };
        int perRank = "bacterio".equals(subfaction) ? 1 : 2;
        return base + rank * perRank;
    }

    private static float lineRatio(String subfaction) {
        return switch (subfaction) {
            case "filemon" -> AndroidsCompatMod.PN_FILEMON_LINE_RATIO.get().floatValue();
            case "bacterio" -> AndroidsCompatMod.PN_BACTERIO_LINE_RATIO.get().floatValue();
            case "mortadelo" -> AndroidsCompatMod.PN_MORTADELO_LINE_RATIO.get().floatValue();
            default -> AndroidsCompatMod.PN_SUPER_LINE_RATIO.get().floatValue();
        };
    }

    private static float stalkerRatio(String subfaction) {
        return switch (subfaction) {
            case "filemon" -> AndroidsCompatMod.PN_FILEMON_STALKER_RATIO.get().floatValue();
            case "bacterio" -> AndroidsCompatMod.PN_BACTERIO_STALKER_RATIO.get().floatValue();
            case "mortadelo" -> AndroidsCompatMod.PN_MORTADELO_STALKER_RATIO.get().floatValue();
            default -> AndroidsCompatMod.PN_SUPER_STALKER_RATIO.get().floatValue();
        };
    }

    static BlockPos spawnPoint(ServerLevel level, BlockPos village, int inner, int outer) {
        for (int i = 0; i < 24; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            int radius = inner + level.getRandom().nextInt(outer - inner + 1);
            BlockPos p = BlockPos.containing(village.getX() + Math.cos(angle) * radius,
                    village.getY(), village.getZ() + Math.sin(angle) * radius);
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
            BlockPos at = new BlockPos(p.getX(), y, p.getZ());
            if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()) return at;
        }
        return village.above();
    }

    private static void buildFort(ServerLevel level, BlockPos village) {
        if (BASE_BUILDS.values().stream().anyMatch(job -> job.dimension.equals(
                level.dimension().location().toString())
                && job.center.distSqr(village) < 96.0D * 96.0D)) return;

        BlockPos center = findOutpostSite(level, village);
        if (center == null) return;
        String key = level.dimension().location() + ":" + center.getX() + ":" + center.getZ();
        if (level.getBlockState(center).is(Blocks.CHISELED_STONE_BRICKS)) {
            BASE_BUILDS.computeIfAbsent(key, ignored -> new BaseBuildJob(
                    level.dimension().location().toString(), center, List.of(), -1,
                    center.offset(-2, 0, -2), false));
            return;
        }
        if (BASE_BUILDS.containsKey(key)) return;

        int design = level.getRandom().nextInt(3);
        boolean hasPrisoner = level.getRandom().nextDouble()
            < AndroidsCompatMod.PN_OUTPOST_PRISONER_CHANCE.get();
        BlockPos cell = center.offset(-2, 0, -2);
        List<BlockPlacement> plan = new ArrayList<>();
        addOutpostLayout(center, plan, design, hasPrisoner);
        plan.add(new BlockPlacement(center, Blocks.CHISELED_STONE_BRICKS.defaultBlockState()));
        BaseBuildJob job = new BaseBuildJob(level.dimension().location().toString(), center,
                plan, design, cell, hasPrisoner);
        job.builders.add(spawnBuilder(level, center, false));
        job.builders.add(spawnBuilder(level, center, true));
        BASE_BUILDS.put(key, job);
    }

    private static BlockPos findOutpostSite(ServerLevel level, BlockPos village) {
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
            int radius = 24 + level.getRandom().nextInt(17);
            int x = village.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = village.getZ() + (int) Math.round(Math.sin(angle) * radius);
            if (!level.hasChunkAt(new BlockPos(x, village.getY(), z))) continue;
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos center = new BlockPos(x, y, z);
            int minHeight = y;
            int maxHeight = y;
            boolean loaded = true;
            boolean clear = true;
            for (int dx = -5; dx <= 5 && clear; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    BlockPos ground = new BlockPos(x + dx, y - 1, z + dz);
                    BlockPos air = ground.above();
                    if (!level.hasChunkAt(air)) {
                        loaded = false;
                        clear = false;
                        break;
                    }
                    int columnHeight = level.getHeight(
                            net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            x + dx, z + dz);
                    minHeight = Math.min(minHeight, columnHeight);
                    maxHeight = Math.max(maxHeight, columnHeight);
                    if (!level.getBlockState(air).isAir()
                            || !level.getBlockState(ground).isSolid()) {
                        clear = false;
                        break;
                    }
                }
            }
            if (loaded && clear && maxHeight - minHeight <= 3
                    && !level.getBlockState(center).is(Blocks.CHISELED_STONE_BRICKS)) return center;
        }
        return null;
    }

    private static void addOutpostLayout(BlockPos center, List<BlockPlacement> plan,
                                         int design, boolean hasPrisoner) {
        net.minecraft.world.level.block.state.BlockState wall = switch (design) {
            case 0 -> Blocks.DARK_OAK_FENCE.defaultBlockState();
            case 1 -> Blocks.COBBLESTONE.defaultBlockState();
            default -> Blocks.SPRUCE_PLANKS.defaultBlockState();
        };
        net.minecraft.world.level.block.state.BlockState tower = switch (design) {
            case 0 -> Blocks.DARK_OAK_LOG.defaultBlockState();
            case 1 -> Blocks.STONE_BRICKS.defaultBlockState();
            default -> Blocks.SPRUCE_LOG.defaultBlockState();
        };
        int radius = design == 1 ? 5 : 6;
        int wallHeight = design == 1 ? 2 : 1;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (Math.abs(x) != radius && Math.abs(z) != radius) continue;
                boolean corner = Math.abs(x) == radius && Math.abs(z) == radius;
                boolean gate = !corner && x == 0 && z == -radius;
                if (gate) {
                    plan.add(new BlockPlacement(center.offset(x, 0, z),
                            Blocks.DARK_OAK_FENCE_GATE.defaultBlockState()));
                    continue;
                }
                int height = corner ? (design == 2 ? 4 : 5) : wallHeight;
                for (int y = 0; y < height; y++) {
                    plan.add(new BlockPlacement(center.offset(x, y, z), corner ? tower : wall));
                }
                if (corner) {
                    plan.add(new BlockPlacement(center.offset(x, height, z),
                            design == 1 ? Blocks.COBBLESTONE_WALL.defaultBlockState()
                                    : Blocks.SPRUCE_FENCE.defaultBlockState()));
                }
            }
        }

        plan.add(new BlockPlacement(center.above(), Blocks.CAMPFIRE.defaultBlockState()));
        plan.add(new BlockPlacement(center.offset(0, 0, 2), Blocks.CHEST.defaultBlockState()));
        if (design == 0) {
            plan.add(new BlockPlacement(center.offset(2, 0, 2), Blocks.BARREL.defaultBlockState()));
            plan.add(new BlockPlacement(center.offset(-2, 0, 2), Blocks.CRAFTING_TABLE.defaultBlockState()));
        } else if (design == 1) {
            plan.add(new BlockPlacement(center.offset(2, 0, 2), Blocks.FURNACE.defaultBlockState()));
            plan.add(new BlockPlacement(center.offset(-2, 0, 2), Blocks.BARREL.defaultBlockState()));
        } else {
            plan.add(new BlockPlacement(center.offset(2, 0, 2), Blocks.WHITE_WOOL.defaultBlockState()));
            plan.add(new BlockPlacement(center.offset(-2, 0, 2), Blocks.RED_WOOL.defaultBlockState()));
        }

        if (hasPrisoner) {
            for (int x = -3; x <= -1; x++) {
                for (int z = -3; z <= -1; z++) {
                    if (Math.abs(x + 2) == 1 || Math.abs(z + 2) == 1) {
                        boolean door = x == -3 && z == -2;
                        plan.add(new BlockPlacement(center.offset(x, 0, z), door
                                ? Blocks.DARK_OAK_FENCE_GATE.defaultBlockState()
                                : Blocks.IRON_BARS.defaultBlockState()));
                    }
                }
            }
        }
    }

    private static void rememberExistingFactionBase(ServerLevel level, BlockPos village) {
        if (BASE_BUILDS.values().stream().anyMatch(job -> job.dimension.equals(
            level.dimension().location().toString())
            && job.center.distSqr(village) < 96.0D * 96.0D)) return;
        for (int x = village.getX() - 42; x <= village.getX() + 42; x++) {
            for (int z = village.getZ() - 42; z <= village.getZ() + 42; z++) {
                if (!level.hasChunkAt(new BlockPos(x, village.getY(), z))) continue;
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos center = null;
                for (int markerY = y - 1; markerY <= y + 1; markerY++) {
                    BlockPos candidate = new BlockPos(x, markerY, z);
                    if (level.getBlockState(candidate).is(Blocks.CHISELED_STONE_BRICKS)) {
                        center = candidate;
                        break;
                    }
                }
                if (center == null) continue;
                BlockPos baseCenter = center;
                String key = level.dimension().location() + ":" + baseCenter.getX() + ":" + baseCenter.getZ();
                BASE_BUILDS.computeIfAbsent(key, ignored -> new BaseBuildJob(
                    level.dimension().location().toString(), baseCenter, List.of(), -1,
                    baseCenter.offset(-2, 0, -2), false));
            }
        }
    }

    private static Mob spawnBuilder(ServerLevel level, BlockPos center, boolean rogue) {
        Mob builder = rogue ? PuenteNorteEntities.ROGUE_ANDROID.get().create(level)
                : PuenteNorteEntities.BASIC_ANDROID.get().create(level);
        if (builder == null) return null;
        builder.moveTo(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        builder.finalizeSpawn(level, level.getCurrentDifficultyAt(center),
                net.minecraft.world.entity.MobSpawnType.EVENT, null, null);
        builder.getPersistentData().putBoolean(PuenteNorteRaider.FACTION_TAG, true);
        builder.getPersistentData().putBoolean("androidscompat_pn_builder", true);
        builder.setPersistenceRequired();
        builder.setCustomName(net.minecraft.network.chat.Component.literal(
                rogue ? "Constructor Rogue · Puente Norte" : "Constructor Android · Puente Norte"));
        builder.setCustomNameVisible(true);
        level.addFreshEntity(builder);
        return builder;
    }

    private static void advanceBaseConstruction(net.minecraft.server.MinecraftServer server) {
        var iterator = BASE_BUILDS.entrySet().iterator();
        while (iterator.hasNext()) {
            BaseBuildJob job = iterator.next().getValue();
            ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    new ResourceLocation(job.dimension)));
            if (level == null) {
                iterator.remove();
                continue;
            }
            if (job.next >= job.plan.size()) {
                if (job.hasPrisoner && !job.prisonerSpawned && level.hasChunkAt(job.prisonCell)) {
                    spawnRebelPrisoner(level, job.prisonCell);
                    job.prisonerSpawned = true;
                }
                if (server.getTickCount() - job.lastCraftAttempt >= 200) {
                    job.lastCraftAttempt = server.getTickCount();
                    if (craftAircraftStep(level, job)) job.lastPlaneTick = level.getGameTime();
                }
                continue;
            }
            BlockPlacement placement = job.plan.get(job.next);
            if (!level.hasChunkAt(placement.position)) continue;
            job.next++;
            if (level.getBlockState(placement.position).isAir()
                    || level.getBlockState(placement.position).canBeReplaced()) {
                level.setBlock(placement.position, placement.state, 3);
            }
            for (int i = 0; i < job.builders.size(); i++) {
                Mob builder = job.builders.get(i);
                if (builder != null && builder.isAlive()) {
                    double dx = (i == 0 ? 2.0D : -2.0D);
                    builder.getNavigation().moveTo(placement.position.getX() + dx,
                            placement.position.getY(), placement.position.getZ(), 1.0D);
                }
            }
        }
    }

    private static void spawnRebelPrisoner(ServerLevel level, BlockPos cell) {
        Villager prisoner = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        if (prisoner == null) return;
        prisoner.moveTo(cell.getX() + 0.5D, cell.getY(), cell.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        prisoner.setNoAi(true);
        prisoner.setPersistenceRequired();
        prisoner.setCustomName(net.minecraft.network.chat.Component.literal("Rebelde capturado · Puente Norte"));
        prisoner.setCustomNameVisible(true);
        prisoner.getPersistentData().putBoolean("androidscompat_pn_prisoner", true);
        prisoner.getPersistentData().putInt("androidscompat_pn_cell_gate_x", cell.getX() - 1);
        prisoner.getPersistentData().putInt("androidscompat_pn_cell_gate_y", cell.getY());
        prisoner.getPersistentData().putInt("androidscompat_pn_cell_gate_z", cell.getZ());
        level.addFreshEntity(prisoner);
        notifyVillage(level, cell, "Un rebelde está encerrado en el puesto de Puente Norte.");
    }

    @SubscribeEvent
    public static void rescuePrisoner(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity().level().isClientSide
                || !(event.getTarget() instanceof Villager prisoner)
                || !prisoner.getPersistentData().getBoolean("androidscompat_pn_prisoner")) return;
        BlockPos gate = new BlockPos(
                prisoner.getPersistentData().getInt("androidscompat_pn_cell_gate_x"),
                prisoner.getPersistentData().getInt("androidscompat_pn_cell_gate_y"),
                prisoner.getPersistentData().getInt("androidscompat_pn_cell_gate_z"));
        if (prisoner.level().getBlockState(gate).is(Blocks.DARK_OAK_FENCE_GATE)) {
            prisoner.level().destroyBlock(gate, false);
        }
        prisoner.setNoAi(false);
        prisoner.getPersistentData().remove("androidscompat_pn_prisoner");
        prisoner.setCustomName(net.minecraft.network.chat.Component.literal("Rebelde liberado"));
        event.getEntity().sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "Has liberado al rebelde capturado de Puente Norte."));
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    private static boolean craftAircraftStep(ServerLevel level, BaseBuildJob base) {
        int aircraftCap = AndroidsCompatMod.PN_AIRCRAFT_PER_BASE.get();
        if (aircraftCap == 0) return false;
        var aircraftBounds = new net.minecraft.world.phys.AABB(base.center).inflate(128.0D);
        long existingAircraft = level.getEntitiesOfClass(Entity.class, aircraftBounds,
                entity -> entity.getPersistentData().getBoolean("androidscompat_pn_crafted_aircraft")
                        && AndroidsCompatMod.isCompatibleAircraftInstance(entity)).size();
        if (existingAircraft >= aircraftCap) return false;
        BlockPos storePos = base.center.offset(0, 0, 2);
        if (!(level.getBlockEntity(storePos) instanceof Container stockpile)) return false;
        CraftingContainer grid = new TransientCraftingContainer(new AbstractContainerMenu(null, -1) {
            @Override public void slotsChanged(Container container) {}
            @Override public net.minecraft.world.item.ItemStack quickMoveStack(Player player, int slot) {
                return ItemStack.EMPTY;
            }
            @Override public boolean stillValid(Player player) { return false; }
        }, 3, 3);
        List<CraftingRecipe> recipes = new ArrayList<>(level.getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING));
        recipes.removeIf(recipe -> {
            ItemStack output = recipe.getResultItem(level.registryAccess());
            if (output.isEmpty()) return true;
            String namespace = BuiltInRegistries.ITEM.getKey(output.getItem()).getNamespace();
            return !namespace.equals("immersive_aircraft") && !namespace.equals("man_of_many_planes");
        });
        recipes.sort(Comparator.comparing(recipe -> isPlaneOutput(recipe.getResultItem(level.registryAccess()))));
        for (CraftingRecipe recipe : recipes) {
            boolean planeRecipe = isPlaneOutput(recipe.getResultItem(level.registryAccess()));
            if (planeRecipe && level.getGameTime() - base.lastPlaneTick
                    < AndroidsCompatMod.PN_AIRCRAFT_CRAFT_INTERVAL.get()) continue;
            int[] map = new int[9];
            java.util.Arrays.fill(map, -1);
            boolean filled = fillCraftGrid(recipe, stockpile, grid, map, level);
            if (!filled) continue;
            ItemStack output = recipe.assemble(grid, level.registryAccess());
            if (output.isEmpty()) continue;
            ResourceLocation outputId = BuiltInRegistries.ITEM.getKey(output.getItem());
            var aircraftType = BuiltInRegistries.ENTITY_TYPE.getOptional(outputId).orElse(null);
            if (aircraftType != null && isCompatibleAircraftType(aircraftType)) {
                Entity aircraft = aircraftType.create(level);
                if (aircraft == null) continue;
                aircraft.getPersistentData().putBoolean("androidscompat_pn_crafted_aircraft", true);
                aircraft.moveTo(base.center.getX() + 0.5D, base.center.getY() + 5.0D,
                        base.center.getZ() + 0.5D, level.getRandom().nextFloat() * 360.0F, 0.0F);
                equipCraftedAircraft(level, stockpile, aircraft);
                level.addFreshEntity(aircraft);
                PuenteNorteRogueAndroid pilot = PuenteNorteEntities.ROGUE_ANDROID.get().create(level);
                if (pilot != null) {
                    pilot.moveTo(aircraft.position().x, aircraft.position().y, aircraft.position().z,
                            aircraft.getYRot(), 0.0F);
                    pilot.finalizeSpawn(level, level.getCurrentDifficultyAt(aircraft.blockPosition()),
                            net.minecraft.world.entity.MobSpawnType.EVENT, null, null);
                    pilot.getPersistentData().putBoolean(PuenteNorteRaider.FACTION_TAG, true);
                    pilot.getPersistentData().putBoolean("androidscompat_pn_enemy_android", true);
                    pilot.setPersistenceRequired();
                    pilot.setCustomName(net.minecraft.network.chat.Component.literal("Piloto de Puente Norte"));
                    pilot.setCustomNameVisible(true);
                    ServerPlayer target = level.players().stream().filter(p -> !p.isSpectator())
                            .min(Comparator.comparingDouble(p -> p.distanceToSqr(base.center.getX(),
                                    base.center.getY(), base.center.getZ()))).orElse(null);
                    if (target != null) pilot.setTarget(target);
                    if (AndroidsCompatMod.assignPuenteNorteAircraftPilot(pilot, aircraft, false)) {
                        level.addFreshEntity(pilot);
                    }
                }
            } else {
                ItemStack leftover = insertIntoContainer(stockpile, output.copy());
                if (!leftover.isEmpty()) aircraftDiscardOrStore(level, base, leftover);
            }
            consumeCraftIngredients(stockpile, grid, map);
            stockpile.setChanged();
            return aircraftType != null && isCompatibleAircraftType(aircraftType);
        }
        return false;
    }

    private static boolean isPlaneOutput(ItemStack output) {
        return !output.isEmpty() && BuiltInRegistries.ENTITY_TYPE.getOptional(
                BuiltInRegistries.ITEM.getKey(output.getItem())).isPresent();
    }

    private static boolean isCompatibleAircraftType(net.minecraft.world.entity.EntityType<?> type) {
        String name = BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace();
        return (name.equals("immersive_aircraft") || name.equals("man_of_many_planes"))
                && ModList.get().isLoaded("immersive_aircraft");
    }

    private static boolean fillCraftGrid(CraftingRecipe recipe, Container stockpile,
                                         CraftingContainer grid, int[] slotMap, ServerLevel level) {
        List<net.minecraft.world.item.crafting.Ingredient> ingredients = recipe.getIngredients();
        if (recipe instanceof ShapedRecipe shaped) {
            for (int offsetY = 0; offsetY <= 3 - shaped.getHeight(); offsetY++) {
                for (int offsetX = 0; offsetX <= 3 - shaped.getWidth(); offsetX++) {
                    for (boolean mirrored : new boolean[]{false, true}) {
                        grid.clearContent();
                        java.util.Arrays.fill(slotMap, -1);
                        Map<Integer, Integer> used = new HashMap<>();
                        boolean valid = true;
                        for (int y = 0; y < shaped.getHeight() && valid; y++) {
                            for (int x = 0; x < shaped.getWidth(); x++) {
                                var ingredient = ingredients.get(y * shaped.getWidth() + x);
                                if (ingredient.getItems().length == 0) continue;
                                int sx = mirrored ? shaped.getWidth() - 1 - x : x;
                                int craftSlot = (y + offsetY) * 3 + sx + offsetX;
                                int stockSlot = findIngredient(stockpile, ingredient, used);
                                if (stockSlot < 0) { valid = false; break; }
                                used.merge(stockSlot, 1, Integer::sum);
                                slotMap[craftSlot] = stockSlot;
                                grid.setItem(craftSlot, stockpile.getItem(stockSlot).copyWithCount(1));
                            }
                        }
                        if (valid && recipe.matches(grid, level)) return true;
                    }
                }
            }
            return false;
        }
        grid.clearContent();
        java.util.Arrays.fill(slotMap, -1);
        Map<Integer, Integer> used = new HashMap<>();
        int craftSlot = 0;
        for (var ingredient : ingredients) {
            if (ingredient.getItems().length == 0) continue;
            while (craftSlot < 9 && !grid.getItem(craftSlot).isEmpty()) craftSlot++;
            if (craftSlot >= 9) return false;
            int stockSlot = findIngredient(stockpile, ingredient, used);
            if (stockSlot < 0) return false;
            used.merge(stockSlot, 1, Integer::sum);
            slotMap[craftSlot] = stockSlot;
            grid.setItem(craftSlot++, stockpile.getItem(stockSlot).copyWithCount(1));
        }
        return recipe.matches(grid, level);
    }

    private static int findIngredient(Container stockpile,
                                      net.minecraft.world.item.crafting.Ingredient ingredient,
                                      Map<Integer, Integer> used) {
        for (int i = 0; i < stockpile.getContainerSize(); i++) {
            ItemStack stack = stockpile.getItem(i);
            if (!stack.isEmpty() && stack.getCount() > used.getOrDefault(i, 0) && ingredient.test(stack)) return i;
        }
        return -1;
    }

    private static void consumeCraftIngredients(Container stockpile, CraftingContainer grid, int[] slotMap) {
        for (int craftSlot = 0; craftSlot < slotMap.length; craftSlot++) {
            int stockSlot = slotMap[craftSlot];
            if (stockSlot < 0 || grid.getItem(craftSlot).isEmpty()) continue;
            stockpile.getItem(stockSlot).shrink(1);
            if (stockpile.getItem(stockSlot).isEmpty()) stockpile.setItem(stockSlot, ItemStack.EMPTY);
        }
    }

    private static ItemStack insertIntoContainer(Container container, ItemStack input) {
        ItemStack remaining = input.copy();
        for (int slot = 0; slot < container.getContainerSize() && !remaining.isEmpty(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (!existing.isEmpty() && ItemStack.isSameItemSameTags(existing, remaining)) {
                int moved = Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount());
                if (moved > 0) {
                    existing.grow(moved);
                    remaining.shrink(moved);
                    container.setItem(slot, existing);
                }
            }
        }
        for (int slot = 0; slot < container.getContainerSize() && !remaining.isEmpty(); slot++) {
            if (!container.getItem(slot).isEmpty()) continue;
            int moved = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            container.setItem(slot, remaining.copyWithCount(moved));
            remaining.shrink(moved);
        }
        container.setChanged();
        return remaining;
    }

    private static void aircraftDiscardOrStore(ServerLevel level, BaseBuildJob base, ItemStack item) {
        level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level,
                base.center.getX() + 0.5D, base.center.getY() + 1.0D, base.center.getZ() + 0.5D, item));
    }

    private static void equipCraftedAircraft(ServerLevel level, Container stockpile, Entity aircraft) {
        if (!ModList.get().isLoaded("immersive_aircraft")) return;
        try {
            Object slotsValue = aircraft.getClass().getMethod("getSlots", String.class).invoke(aircraft, "weapon");
            Object inventoryValue = aircraft.getClass().getMethod("getInventory").invoke(aircraft);
            if (!(slotsValue instanceof List<?> slots) || !(inventoryValue instanceof Container inventory)) return;
            ItemStack weapon = ItemStack.EMPTY;
            for (String path : List.of("rotary_cannon", "heavy_crossbow")) {
                ItemStack candidate = new ItemStack(BuiltInRegistries.ITEM.get(
                        new ResourceLocation("immersive_aircraft", path)));
                if (!candidate.isEmpty()) { weapon = candidate; break; }
            }
            if (weapon.isEmpty()) return;
            for (Object value : slots) {
                if (!(value instanceof Number slot) || !inventory.getItem(slot.intValue()).isEmpty()) continue;
                inventory.setItem(slot.intValue(), weapon);
                inventory.setChanged();
                weapon = ItemStack.EMPTY;
                break;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    private record BlockPlacement(BlockPos position, net.minecraft.world.level.block.state.BlockState state) {}

    private static final class BaseBuildJob {
        private final String dimension;
        private final BlockPos center;
        private final List<BlockPlacement> plan;
        private final int design;
        private final BlockPos prisonCell;
        private final boolean hasPrisoner;
        private final List<Mob> builders = new ArrayList<>();
        private int next;
        private boolean prisonerSpawned;
        private long lastCraftAttempt;
        private long lastPlaneTick;
        private BaseBuildJob(String dimension, BlockPos center, List<BlockPlacement> plan,
                             int design, BlockPos prisonCell, boolean hasPrisoner) {
            this.dimension = dimension;
            this.center = center;
            this.plan = plan;
            this.design = design;
            this.prisonCell = prisonCell;
            this.hasPrisoner = hasPrisoner;
            this.prisonerSpawned = !hasPrisoner || plan.isEmpty();
        }
    }

    static ItemStack findCgsWeapon(net.minecraft.util.RandomSource random) {
        if (!AndroidsCompatMod.PN_CGS_WEAPONS.get() || !ModList.get().isLoaded("cgs") || !ModList.get().isLoaded("ntgl")) return ItemStack.EMPTY;
        try {
            List<Item> candidates = cgsWeapons();
            if (candidates.isEmpty()) return ItemStack.EMPTY;
            ItemStack selected = new ItemStack(candidates.get(random.nextInt(candidates.size())));
            addCompatibleCgsAttachment(selected, random);
            return selected;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static List<Item> cgsWeapons() throws ReflectiveOperationException {
        List<Item> cached = CACHED_CGS_WEAPONS;
        if (cached != null) return cached;
        synchronized (PuenteNorteFaction.class) {
            if (CACHED_CGS_WEAPONS == null) {
                Class<?> weapon = Class.forName(WEAPON_INTERFACE);
                CACHED_CGS_WEAPONS = BuiltInRegistries.ITEM.entrySet().stream()
                        .filter(entry -> "cgs".equals(entry.getKey().location().getNamespace()))
                        .filter(entry -> weapon.isInstance(entry.getValue()))
                        .map(Map.Entry::getValue)
                        .toList();
            }
            return CACHED_CGS_WEAPONS;
        }
    }

    private static List<Item> cgsAttachments() throws ReflectiveOperationException {
        List<Item> cached = CACHED_CGS_ATTACHMENTS;
        if (cached != null) return cached;
        synchronized (PuenteNorteFaction.class) {
            if (CACHED_CGS_ATTACHMENTS == null) {
                Class<?> attachmentApi = Class.forName(
                        "com.nukateam.ntgl.common.data.attachment.IAttachment");
                CACHED_CGS_ATTACHMENTS = BuiltInRegistries.ITEM.entrySet().stream()
                        .filter(entry -> "cgs".equals(entry.getKey().location().getNamespace()))
                        .filter(entry -> attachmentApi.isInstance(entry.getValue()))
                        .map(Map.Entry::getValue)
                        .toList();
            }
            return CACHED_CGS_ATTACHMENTS;
        }
    }

    /** Adds one compatible attachment using NTGL's own attachment validation and NBT format. */
    private static void addCompatibleCgsAttachment(ItemStack weapon, net.minecraft.util.RandomSource random) {
        if (random.nextFloat() >= 0.70F) return;
        try {
                Class<?> attachmentApi = Class.forName("com.nukateam.ntgl.common.data.attachment.IAttachment");
            Class<?> weaponDataType = Class.forName("com.nukateam.ntgl.common.data.WeaponData");
            Class<?> stateHelper = Class.forName("com.nukateam.ntgl.common.util.util.WeaponStateHelper");
                List<Item> compatible = cgsAttachments().stream()
                    .filter(item -> {
                        try {
                            return Boolean.TRUE.equals(attachmentApi
                                    .getMethod("canAttachTo", ItemStack.class)
                            .invoke(item, weapon));
                        } catch (ReflectiveOperationException error) {
                            return false;
                        }
                    })
                    .toList();
            if (compatible.isEmpty()) return;

            Item attachmentItem = compatible.get(random.nextInt(compatible.size()));
            ItemStack attachment = new ItemStack(attachmentItem);
            Object data = weaponDataType.getConstructor(ItemStack.class, LivingEntity.class)
                    .newInstance(weapon, null);
            stateHelper.getMethod("saveAttachments", weaponDataType, java.util.Collection.class)
                    .invoke(null, data, List.of(attachment));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // Optional API/version mismatch: keep the weapon and its normal configuration.
        }
    }
}
