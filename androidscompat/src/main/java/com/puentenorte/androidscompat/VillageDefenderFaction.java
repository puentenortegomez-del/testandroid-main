package com.puentenorte.androidscompat;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Allied village guard, shared diplomacy, skirmishes, dynamic contracts and recruitment. */
@Mod.EventBusSubscriber(modid = AndroidsCompatMod.MOD_ID)
public final class VillageDefenderFaction {
    private static final int VILLAGE_SCAN_TICKS = 200;
    private static final int SKIRMISH_COOLDOWN_TICKS = 24_000;
    private static final String QUEST_ACTIVE = "androidscompat_village_quest_active";
    private static final String QUEST_COMPLETE = "androidscompat_village_quest_complete";
    private static final String QUEST_RANK = "androidscompat_village_quest_rank";
    private static final String QUEST_TARGET = "androidscompat_village_quest_target";
    private static final String QUEST_MINIONS = "androidscompat_village_quest_minions";
    private static final String QUEST_FACTION = "androidscompat_village_quest_faction";
    private static final String QUEST_NEXT_ALLOWED = "androidscompat_village_quest_next_allowed";
    private static final String RECRUIT_COUNT = "androidscompat_village_recruits";
    private static final java.util.Map<String, Long> LOGISTICS_NEXT = new java.util.HashMap<>();
    private static final String WEAPON_INTERFACE =
            "com.nukateam.ntgl.common.foundation.item.interfaces.IWeapon";
    private static final String ATTACHMENT_INTERFACE =
            "com.nukateam.ntgl.common.data.attachment.IAttachment";
    private static final String WEAPON_DATA = "com.nukateam.ntgl.common.data.WeaponData";
    private static final String WEAPON_STATE = "com.nukateam.ntgl.common.util.util.WeaponStateHelper";

    private VillageDefenderFaction() {}

    static boolean isHostileToPlayers(Level level) {
        if (level.getServer() == null) return false;
        return VillageOccupationData.get(level.getServer()).villagesAreHostile();
    }

    static boolean isAtWar(Level level) {
        return isHostileToPlayers(level);
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("aldea")
                .then(net.minecraft.commands.Commands.literal("estado")
                        .executes(ctx -> villageStatus(ctx.getSource())))
                .then(net.minecraft.commands.Commands.literal("reclutar")
                        .executes(ctx -> recruit(ctx.getSource())))
                .then(net.minecraft.commands.Commands.literal("mision")
                        .executes(ctx -> questStatus(ctx.getSource()))
                        .then(net.minecraft.commands.Commands.literal("aceptar")
                                .executes(ctx -> acceptQuest(ctx.getSource())))
                        .then(net.minecraft.commands.Commands.literal("cobrar")
                                .executes(ctx -> claimQuest(ctx.getSource())))));
    }

    @SubscribeEvent
    public static void worldTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !AndroidsCompatMod.VILLAGE_DEFENDERS_ENABLED.get()
            || event.getServer().getTickCount() % VILLAGE_SCAN_TICKS != 0) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.dimension() == Level.NETHER || level.dimension() == Level.END) continue;
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator()) continue;
                BlockPos village = PuenteNorteFaction.nearestVillage(level, player.blockPosition());
                if (village == null || player.distanceToSqr(village.getX(), village.getY(), village.getZ()) > 128.0D * 128.0D) continue;
                VillageOccupationData data = VillageOccupationData.get(event.getServer());
                data.getVillage(level.dimension().location(), village);
                ensureGarrison(level, village);
                boolean villageWar = isVillageAtWarWithPuenteNorte(data, level, village);
                if (data.villagesAreHostile() || villageWar)
                    synchronizeWarTargets(level, village, data.villagesAreHostile());
                scheduleLogisticsRoute(level, village, data);
                if (AndroidsCompatMod.VILLAGE_DEFENDERS_ENABLED.get()
                    && data.canStartSkirmish(level.dimension().location(), village, level.getGameTime())
                    && (villageWar || level.getRandom().nextDouble()
                        < AndroidsCompatMod.VILLAGE_SKIRMISH_CHANCE.get())) {
                    data.scheduleSkirmish(level.dimension().location(), village,
                            level.getGameTime() + SKIRMISH_COOLDOWN_TICKS);
                    startSkirmish(level, village, player);
                }
            }
        }
    }

    @SubscribeEvent
    public static void defenderTick(LivingEvent.LivingTickEvent event) {
        if (!AndroidsCompatMod.VILLAGE_DEFENDERS_ENABLED.get()
            || !(event.getEntity() instanceof VillageDefender guard)
                || !(guard.level() instanceof ServerLevel level) || guard.tickCount % 10 != 0) return;
        if (guard.isPassenger() && guard.getVehicle() != null) return;

        VillageOccupationData diplomacy = VillageOccupationData.get(level.getServer());
        LivingEntity current = guard.getTarget();
        BlockPos home = guard.getPersistentData().contains(VillageDefender.HOME_X_TAG)
                ? new BlockPos(guard.getPersistentData().getInt(VillageDefender.HOME_X_TAG),
                    guard.getPersistentData().getInt(VillageDefender.HOME_Y_TAG),
                    guard.getPersistentData().getInt(VillageDefender.HOME_Z_TAG)) : null;
        boolean villageWar = home != null && isVillageAtWarWithPuenteNorte(diplomacy, level, home);
        if (current instanceof PuenteNorteRaider raider) {
            boolean skirmish = guard.getPersistentData().getBoolean("androidscompat_village_skirmish")
                    && raider.getPersistentData().getBoolean("androidscompat_village_skirmish");
            if (!diplomacy.villagesAreHostile() && !villageWar && !skirmish) {
                guard.setTarget(null);
                current = null;
            }
        } else if (current instanceof Player && !diplomacy.villagesAreHostile()) {
            guard.setTarget(null);
            current = null;
        }

        if (current == null && (diplomacy.villagesAreHostile() || villageWar)) {
            LivingEntity enemy = nearestPuenteNorte(guard, 48.0D);
            if (enemy == null) enemy = level.getEntitiesOfClass(ServerPlayer.class,
                    guard.getBoundingBox().inflate(32.0D), player -> player.isAlive() && !player.isSpectator())
                    .stream().min(Comparator.comparingDouble(guard::distanceToSqr)).orElse(null);
            guard.setTarget(enemy);
        }

        if (guard.getTarget() == null) {
            if (guard.getPersistentData().getBoolean(VillageDefender.RECRUITED_TAG)
                    && guard.getPersistentData().hasUUID("androidscompat_village_owner")) {
                ServerPlayer owner = level.getServer().getPlayerList().getPlayer(
                    guard.getPersistentData().getUUID("androidscompat_village_owner"));
                if (owner != null && owner.serverLevel() == level
                        && guard.distanceToSqr(owner) > 8.0D * 8.0D) {
                    guard.getNavigation().moveTo(owner, 1.0D);
                }
            } else if (guard.getPersistentData().contains(VillageDefender.HOME_X_TAG)) {
                if (guard.distanceToSqr(home.getX(), home.getY(), home.getZ()) > 24.0D * 24.0D) {
                    guard.getNavigation().moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, 0.9D);
                }
            }
        }

        if (isNtglWeapon(guard.getMainHandItem())) {
            AndroidsCompatMod.ensureFactionGunGoal(guard);
            maintainMagazine(guard, level);
        }
    }

    @SubscribeEvent
    public static void playerAttacksVillagePeople(LivingDamageEvent event) {
        if (!AndroidsCompatMod.VILLAGE_DEFENDERS_ENABLED.get()
            || !(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        LivingEntity victim = event.getEntity();
        boolean guard = victim instanceof VillageDefender;
        boolean villager = victim instanceof Villager;
        if (!guard && !villager) return;
        BlockPos village = PuenteNorteFaction.nearestVillage(player.serverLevel(), victim.blockPosition());
        if (village == null || victim.distanceToSqr(village.getX(), village.getY(), village.getZ()) > 96.0D * 96.0D) return;
        addAggression(player.serverLevel(), guard ? 12 : 5, player);
    }

    @SubscribeEvent
    public static void deaths(LivingDeathEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (!AndroidsCompatMod.VILLAGE_DEFENDERS_ENABLED.get()
            || !(attacker instanceof ServerPlayer player)) return;
        LivingEntity victim = event.getEntity();
        if (victim instanceof VillageDefender) {
            addAggression(player.serverLevel(), 30, player);
        } else if (victim instanceof Villager) {
            BlockPos village = PuenteNorteFaction.nearestVillage(player.serverLevel(), victim.blockPosition());
            if (village != null && victim.distanceToSqr(village.getX(), village.getY(), village.getZ()) < 96.0D * 96.0D) {
                addAggression(player.serverLevel(), 20, player);
            }
        }
        progressQuest(player, victim);
    }

    private static void addAggression(ServerLevel level, int amount, ServerPlayer offender) {
        VillageOccupationData data = VillageOccupationData.get(level.getServer());
        int before = data.aggression();
        int after = data.addAggression(amount);
        if (after == before) return;
        int[] thresholds = {25, 50, 75, 100};
        for (int threshold : thresholds) {
            if (before < threshold && after >= threshold) {
                String message = threshold == 100
                        ? "La Guardia Aldeana ha declarado hostil a " + offender.getGameProfile().getName()
                                + ". Todas las aldeas conocen la agresión."
                        : "La reputación de " + offender.getGameProfile().getName()
                                + " cae con las aldeas (" + after + "%).";
                for (ServerPlayer online : level.getServer().getPlayerList().getPlayers()) {
                    online.sendSystemMessage(Component.literal(message));
                }
                break;
            }
        }
    }

    private static void ensureGarrison(ServerLevel level, BlockPos village) {
        AABB area = new AABB(village).inflate(48.0D, 24.0D, 48.0D);
        List<VillageDefender> defenders = level.getEntitiesOfClass(VillageDefender.class, area,
                guard -> guard.isAlive() && !guard.getPersistentData().getBoolean(VillageDefender.RECRUITED_TAG));
        boolean chief = defenders.stream().anyMatch(VillageDefenderChief.class::isInstance);
        if (defenders.isEmpty()) {
            spawnDefender(level, village, true, false);
            spawnDefender(level, village, false, false);
            spawnDefender(level, village, false, false);
            return;
        }
        if (!chief && defenders.size() < 4) spawnDefender(level, village, true, false);
        else if (defenders.size() < 3) spawnDefender(level, village, false, false);
    }

    private static VillageDefender spawnDefender(ServerLevel level, BlockPos village,
                                                 boolean chief, boolean recruited) {
        int dx = level.getRandom().nextInt(13) - 6;
        int dz = level.getRandom().nextInt(13) - 6;
        BlockPos spawn = safeSurface(level, village.offset(dx, 0, dz));
        if (!level.hasChunkAt(spawn) || !level.getBlockState(spawn).isAir()
                || !level.getBlockState(spawn.above()).isAir()) return null;
        VillageDefender guard = chief
                ? PuenteNorteEntities.VILLAGE_DEFENDER_CHIEF.get().create(level)
                : PuenteNorteEntities.VILLAGE_DEFENDER.get().create(level);
        if (guard == null) return null;
        guard.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        DifficultyInstance difficulty = level.getCurrentDifficultyAt(spawn);
        guard.finalizeSpawn(level, difficulty, MobSpawnType.EVENT, null, null);
        guard.setVillageHome(village);
        guard.setSkinVariant(level.getRandom().nextInt(5));
        guard.setCommanderSkin(level.getRandom().nextFloat() < 0.22F);
        guard.setPersistenceRequired();
        guard.getPersistentData().putBoolean(VillageDefender.RECRUITED_TAG, recruited);
        level.addFreshEntity(guard);
        return guard;
    }

    private static BlockPos safeSurface(ServerLevel level, BlockPos around) {
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                around.getX(), around.getZ());
        return new BlockPos(around.getX(), y, around.getZ());
    }

    private static void startSkirmish(ServerLevel level, BlockPos village, ServerPlayer witness) {
        List<VillageDefender> defenders = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            VillageDefender guard = spawnDefender(level, village, i == 0, false);
            if (guard != null) {
                guard.getPersistentData().putBoolean("androidscompat_village_skirmish", true);
                defenders.add(guard);
            }
        }
        if (defenders.isEmpty()) return;
        List<PuenteNorteSoldier> raiders = new ArrayList<>();
        String subfaction = "superintendente";
        for (int i = 0; i < 3; i++) {
            PuenteNorteSoldier raider = PuenteNorteEntities.SOLDIER.get().create(level);
            if (raider == null) continue;
            raider.configure(i == 0 ? "linea" : "apoyo", subfaction, "normal", 1);
            BlockPos spawn = safeSurface(level, village.offset(12 + level.getRandom().nextInt(7), 0,
                    level.getRandom().nextInt(17) - 8));
            raider.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                    level.getRandom().nextFloat() * 360.0F, 0.0F);
            raider.finalizeSpawn(level, level.getCurrentDifficultyAt(spawn), MobSpawnType.EVENT, null, null);
            raider.setPersistenceRequired();
            raider.getPersistentData().putBoolean("androidscompat_village_skirmish", true);
            level.addFreshEntity(raider);
            raiders.add(raider);
        }
        if (raiders.isEmpty()) return;
        for (int i = 0; i < defenders.size(); i++) defenders.get(i).setTarget(raiders.get(i % raiders.size()));
        for (int i = 0; i < raiders.size(); i++) raiders.get(i).setTarget(defenders.get(i % defenders.size()));
        witness.sendSystemMessage(Component.literal("¡Escaramuza! La Guardia Aldeana combate a Puente Norte cerca de "
                + village.getX() + ", " + village.getZ() + "."));
        launchAirSupport(level, village, defenders.get(0));
    }

    private static void launchAirSupport(ServerLevel level, BlockPos village, VillageDefender guard) {
        if (!ModList.get().isLoaded("immersive_aircraft") || level.getRandom().nextFloat() > 0.25F) return;
        Entity aircraft = level.getEntitiesOfClass(Entity.class,
                        new AABB(village).inflate(128.0D, 48.0D, 128.0D),
                        AndroidsCompatMod::hasCompatibleAircraftWeapons).stream()
                .filter(candidate -> candidate.getPassengers().isEmpty())
                .min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(village.getX(),
                        village.getY(), village.getZ())))
                .orElse(null);
        if (aircraft != null) AndroidsCompatMod.assignVillageAircraftPilot(
            guard, aircraft, village, village, true);
    }

        private static void scheduleLogisticsRoute(ServerLevel level, BlockPos village,
                               VillageOccupationData data) {
        String key = level.dimension().location() + ":" + village.getX() + ":" + village.getZ();
        long now = level.getGameTime();
        if (now < LOGISTICS_NEXT.getOrDefault(key, 0L)) return;
        LOGISTICS_NEXT.put(key, now + 6000L);
        if (!ModList.get().isLoaded("immersive_aircraft") || level.getRandom().nextFloat() > 0.08F) return;
        VillageOccupationData.VillageRecord destination = data.villages().stream()
            .filter(record -> record.dimension.equals(level.dimension().location().toString()))
            .filter(record -> record.center.distSqr(village) >= 128.0D * 128.0D
                && record.center.distSqr(village) <= 512.0D * 512.0D)
            .filter(record -> level.hasChunkAt(record.center))
            .min(Comparator.comparingDouble(record -> record.center.distSqr(village)))
            .orElse(null);
        if (destination == null) return;
        Entity aircraft = level.getEntitiesOfClass(Entity.class,
                new AABB(village).inflate(96.0D, 48.0D, 96.0D),
                AndroidsCompatMod::isCompatibleAircraftInstance).stream()
            .filter(candidate -> candidate.getPassengers().isEmpty())
            .min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(village.getX(),
                village.getY(), village.getZ())))
            .orElse(null);
        if (aircraft == null) return;
        VillageDefender courier = spawnDefender(level, village, false, false);
        if (courier == null) return;
        if (AndroidsCompatMod.assignVillageAircraftPilot(courier, aircraft,
            village, destination.center, false)) {
            courier.setCustomName(Component.literal("Piloto logístico de la Guardia Aldeana"));
            courier.setCustomNameVisible(true);
        } else {
            courier.discard();
        }
        }

    private static LivingEntity nearestPuenteNorte(LivingEntity from, double range) {
        return from.level().getEntitiesOfClass(PuenteNorteRaider.class,
                        from.getBoundingBox().inflate(range), raider -> raider.isAlive())
                .stream().min(Comparator.comparingDouble(from::distanceToSqr)).orElse(null);
    }

    private static boolean isVillageAtWarWithPuenteNorte(VillageOccupationData data,
                                                          ServerLevel level, BlockPos village) {
        VillageOccupationData.VillageRecord record = data.getVillage(
                level.dimension().location(), village);
        return record.isSiegeActive() || record.isOccupied();
    }

    private static void synchronizeWarTargets(ServerLevel level, BlockPos village,
                                               boolean globalHostility) {
        AABB area = new AABB(village).inflate(64.0D, 24.0D, 64.0D);
        List<VillageDefender> defenders = level.getEntitiesOfClass(VillageDefender.class, area,
                guard -> guard.isAlive());
        if (defenders.isEmpty()) return;
        if (!globalHostility && !isVillageAtWarWithPuenteNorte(
            VillageOccupationData.get(level.getServer()), level, village)) return;
        for (PuenteNorteRaider raider : level.getEntitiesOfClass(PuenteNorteRaider.class, area,
                mob -> mob.isAlive())) {
            LivingEntity current = raider.getTarget();
            if (current instanceof Player player && player.isAlive() && !player.isSpectator()) continue;
            VillageDefender target = defenders.stream()
                    .min(Comparator.comparingDouble(raider::distanceToSqr)).orElse(null);
            if (target != null) raider.setTarget(target);
        }
    }

    private static boolean isNtglWeapon(ItemStack stack) {
        if (stack.isEmpty() || !ModList.get().isLoaded("ntgl")) return false;
        try {
            return Class.forName(WEAPON_INTERFACE).isInstance(stack.getItem());
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    static void equipDefenderWeapon(VillageDefender defender, net.minecraft.util.RandomSource random,
                                    boolean chief) {
        if (!ModList.get().isLoaded("cgs") || !ModList.get().isLoaded("ntgl")) {
            defender.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CROSSBOW));
            return;
        }
        String weaponPath;
        if (chief || random.nextFloat() < 0.64F) weaponPath = "flintlock";
        else {
            int roll = random.nextInt(100);
            weaponPath = roll < 32 ? "shotgun" : roll < 58 ? "revolver"
                    : roll < 82 ? "nailgun" : roll < 94 ? "gatling" : "launcher";
        }
        ItemStack weapon = registeredStack("cgs", weaponPath);
        if (weapon.isEmpty() || !isNtglWeapon(weapon)) {
            defender.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CROSSBOW));
            return;
        }
        if ("flintlock".equals(weaponPath)) {
            applyFlintlockAttachments(weapon, defender, true);
        }
        defender.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        defender.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0F);
        defender.getPersistentData().putInt(VillageDefender.AMMO_RESERVE_TAG, chief ? 7 : 4);
        defender.getPersistentData().putLong(VillageDefender.NEXT_RELOAD_TAG, 0L);
    }

    private static ItemStack registeredStack(String namespace, String path) {
        try {
            return BuiltInRegistries.ITEM.getOptional(new ResourceLocation(namespace, path))
                    .map(ItemStack::new).orElse(ItemStack.EMPTY);
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static void applyFlintlockAttachments(ItemStack weapon, LivingEntity wielder, boolean stockAndMusket) {
        try {
            Class<?> attachmentType = Class.forName(ATTACHMENT_INTERFACE);
            Class<?> weaponDataType = Class.forName(WEAPON_DATA);
            Class<?> state = Class.forName(WEAPON_STATE);
            List<ItemStack> attachments = new ArrayList<>();
            if (stockAndMusket) {
                for (String path : List.of("stock", "flintlock_long_barrel")) {
                    ItemStack attachment = registeredStack("cgs", path);
                    if (attachment.isEmpty() || !attachmentType.isInstance(attachment.getItem())) continue;
                    Object instance = attachment.getItem();
                    boolean compatible = Boolean.TRUE.equals(attachmentType
                            .getMethod("canAttachTo", ItemStack.class).invoke(instance, weapon));
                    if (compatible) attachments.add(attachment);
                }
            }
            Object data = weaponDataType.getConstructor(ItemStack.class, LivingEntity.class)
                    .newInstance(weapon, wielder);
            state.getMethod("saveAttachments", weaponDataType, java.util.Collection.class)
                    .invoke(null, data, attachments);
            state.getMethod("setAmmoCount", weaponDataType, int.class).invoke(null, data, 1);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Leave the weapon usable if a local NTGL version changes its optional attachment API.
        }
    }

    private static void maintainMagazine(VillageDefender guard, ServerLevel level) {
        if (guard.tickCount % 20 != 0) return;
        ItemStack weapon = guard.getMainHandItem();
        try {
            Class<?> dataType = Class.forName(WEAPON_DATA);
            Class<?> state = Class.forName(WEAPON_STATE);
            Object data = dataType.getConstructor(ItemStack.class, LivingEntity.class)
                    .newInstance(weapon, guard);
            Method getAmmo = state.getMethod("getAmmoCount", dataType);
            if ((Integer) getAmmo.invoke(null, data) > 0) return;
            long now = level.getGameTime();
            if (now < guard.getPersistentData().getLong(VillageDefender.NEXT_RELOAD_TAG)) return;
            int reserve = guard.getPersistentData().getInt(VillageDefender.AMMO_RESERVE_TAG);
            if (reserve <= 0) return;
            state.getMethod("setAmmoCount", dataType, int.class).invoke(null, data, 1);
            guard.setItemInHand(InteractionHand.MAIN_HAND, weapon);
            guard.getPersistentData().putInt(VillageDefender.AMMO_RESERVE_TAG, reserve - 1);
            guard.getPersistentData().putLong(VillageDefender.NEXT_RELOAD_TAG, now + 80L);
        } catch (ReflectiveOperationException | ClassCastException | LinkageError ignored) {
            // Ammo-less or incompatible weapon: let the mob's vanilla equipment AI continue.
        }
    }

    private static int villageStatus(net.minecraft.commands.CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Este comando requiere un jugador."));
            return 0;
        }
        VillageOccupationData data = VillageOccupationData.get(player.getServer());
        source.sendSuccess(() -> Component.literal("Guardia Aldeana: "
                + (data.villagesAreHostile() ? "HOSTIL" : "aliada")
                + " | tensión global " + data.aggression() + "%"), false);
        return 1;
    }

    private static int recruit(net.minecraft.commands.CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Solo un jugador puede reclutar guardias."));
            return 0;
        }
        BlockPos village = PuenteNorteFaction.nearestVillage(player.serverLevel(), player.blockPosition());
        if (village == null || player.distanceToSqr(village.getX(), village.getY(), village.getZ()) > 64.0D * 64.0D) {
            source.sendFailure(Component.literal("Acércate a una aldea para reclutar a su guardia."));
            return 0;
        }
        VillageOccupationData data = VillageOccupationData.get(player.getServer());
        if (data.villagesAreHostile()) {
            source.sendFailure(Component.literal("Las aldeas se niegan a reclutar para ti mientras sigas siendo enemigo."));
            return 0;
        }
        int recruited = player.getPersistentData().getInt(RECRUIT_COUNT);
        if (recruited >= 5) {
            source.sendFailure(Component.literal("Ya tienes el máximo de cinco guardias reclutados."));
            return 0;
        }
        int price = recruitmentPrice(data, recruited);
        if (!takeItems(player, Items.EMERALD, price)) {
            source.sendFailure(Component.literal("Reclutar cuesta " + price + " esmeraldas."));
            return 0;
        }
        VillageDefender guard = spawnDefender((ServerLevel) player.level(), village,
                false, true);
        if (guard == null) {
            player.getInventory().add(new ItemStack(Items.EMERALD, price));
            source.sendFailure(Component.literal("No hay espacio seguro para el nuevo guardia."));
            return 0;
        }
        guard.getPersistentData().putUUID("androidscompat_village_owner", player.getUUID());
        guard.getPersistentData().putString("androidscompat_village_owner_name", player.getGameProfile().getName());
        guard.setCustomName(Component.literal("Guardia reclutado · " + player.getGameProfile().getName()));
        guard.setCustomNameVisible(true);
        player.getPersistentData().putInt(RECRUIT_COUNT, recruited + 1);
        source.sendSuccess(() -> Component.literal("Guardia reclutado por " + price
                + " esmeraldas. Arma: " + weaponName(guard.getMainHandItem()) + " ("
                + (recruited + 1) + "/5)."), true);
        return 1;
    }

    private static int recruitmentPrice(VillageOccupationData data, int alreadyRecruited) {
        return 8 + data.aggression() / 12 + alreadyRecruited * 3;
    }

    private static boolean takeItems(ServerPlayer player, Item item, int count) {
        int available = 0;
        for (ItemStack stack : player.getInventory().items) if (stack.is(item)) available += stack.getCount();
        if (available < count) return false;
        int remaining = count;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.is(item)) continue;
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            remaining -= taken;
            if (remaining == 0) break;
        }
        player.getInventory().setChanged();
        return true;
    }

    private static String weaponName(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? stack.getHoverName().getString() : id.toString();
    }

    private static int acceptQuest(net.minecraft.commands.CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Solo un jugador puede aceptar misiones."));
            return 0;
        }
        var data = player.getPersistentData();
        if (data.getBoolean(QUEST_ACTIVE) || data.getBoolean(QUEST_COMPLETE)) {
            source.sendFailure(Component.literal("Ya tienes una misión activa o pendiente de cobrar."));
            return 0;
        }
        if (player.level().getGameTime() < data.getLong(QUEST_NEXT_ALLOWED)) {
            source.sendFailure(Component.literal("La guardia está preparando otro encargo; vuelve más tarde."));
            return 0;
        }
        VillageOccupationData diplomacy = VillageOccupationData.get(player.getServer());
        int rank = Math.min(AndroidsCompatMod.PN_MAX_NEMESIS_RANK.get(),
                1 + diplomacy.aggression() / 25);
        String subfaction = nearestPuenteNorteSubfaction(player.serverLevel(), player.blockPosition());
        data.putBoolean(QUEST_ACTIVE, true);
        data.putInt(QUEST_RANK, rank);
        data.putString(QUEST_FACTION, subfaction);
        data.putBoolean(QUEST_TARGET, false);
        data.putInt(QUEST_MINIONS, 0);
        source.sendSuccess(() -> Component.literal("Encargo: elimina un soldado de Puente Norte rango "
            + rank + " o superior de la subfacción " + subfaction
            + " y a cinco de sus subordinados. Recompensa: "
                + questReward(rank) + " esmeraldas."), true);
        return 1;
    }

    private static int questStatus(net.minecraft.commands.CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Solo un jugador puede consultar misiones."));
            return 0;
        }
        var data = player.getPersistentData();
        if (data.getBoolean(QUEST_COMPLETE)) {
            source.sendSuccess(() -> Component.literal("Misión completada: /aldea mision cobrar"), false);
        } else if (data.getBoolean(QUEST_ACTIVE)) {
            source.sendSuccess(() -> Component.literal("Soldado objetivo rango "
                    + data.getInt(QUEST_RANK) + ": " + (data.getBoolean(QUEST_TARGET) ? "eliminado" : "pendiente")
                    + " | subordinados " + data.getInt(QUEST_MINIONS) + "/5"), false);
        } else {
            source.sendSuccess(() -> Component.literal("No tienes un encargo. Usa /aldea mision aceptar."), false);
        }
        return 1;
    }

    private static void progressQuest(ServerPlayer player, LivingEntity victim) {
        var data = player.getPersistentData();
        if (!data.getBoolean(QUEST_ACTIVE) || !(victim instanceof PuenteNorteRaider raider)) return;
        if (!matchesSubfaction(victim, data.getString(QUEST_FACTION))) return;
        if (!data.getBoolean(QUEST_TARGET) && victim instanceof PuenteNorteSoldier soldier
                && soldier.getPersistentData().getInt(PuenteNorteRaider.RANK_TAG) >= data.getInt(QUEST_RANK)) {
            data.putBoolean(QUEST_TARGET, true);
        } else {
            data.putInt(QUEST_MINIONS, data.getInt(QUEST_MINIONS) + 1);
        }
        if (data.getBoolean(QUEST_TARGET) && data.getInt(QUEST_MINIONS) >= 5) {
            data.putBoolean(QUEST_ACTIVE, false);
            data.putBoolean(QUEST_COMPLETE, true);
            player.sendSystemMessage(Component.literal("¡Encargo completado! Cobra la recompensa con /aldea mision cobrar."));
        } else {
            player.displayClientMessage(Component.literal("Encargo: objetivo "
                    + (data.getBoolean(QUEST_TARGET) ? "✓" : "pendiente")
                    + " | subordinados " + data.getInt(QUEST_MINIONS) + "/5"), true);
        }
    }

    private static int claimQuest(net.minecraft.commands.CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)
                || !player.getPersistentData().getBoolean(QUEST_COMPLETE)) {
            source.sendFailure(Component.literal("No tienes una recompensa pendiente."));
            return 0;
        }
        int reward = questReward(player.getPersistentData().getInt(QUEST_RANK));
        player.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD, reward));
        player.getPersistentData().putBoolean(QUEST_COMPLETE, false);
        player.getPersistentData().putLong(QUEST_NEXT_ALLOWED, player.level().getGameTime() + 12_000L);
        source.sendSuccess(() -> Component.literal("Recompensa recibida: " + reward + " esmeraldas."), true);
        return 1;
    }

    private static int questReward(int rank) { return 16 + rank * 8; }

    private static String nearestPuenteNorteSubfaction(ServerLevel level, BlockPos origin) {
        return level.getEntitiesOfClass(PuenteNorteSoldier.class,
                        new AABB(origin).inflate(128.0D), soldier -> soldier.isAlive())
                .stream().min(Comparator.comparingDouble(soldier -> soldier.distanceToSqr(
                    origin.getX(), origin.getY(), origin.getZ())))
                .map(soldier -> soldier.getPersistentData().getString(PuenteNorteSoldier.SUBFACTION_TAG))
                .filter(name -> !name.isEmpty()).orElse("superintendente");
    }

    private static boolean matchesSubfaction(LivingEntity entity, String expected) {
        if (entity instanceof PuenteNorteSoldier soldier) {
            return expected.equals(soldier.getPersistentData().getString(PuenteNorteSoldier.SUBFACTION_TAG));
        }
        return entity instanceof PuenteNorteBoss boss && expected.equals(boss.getBossKind());
    }
}
