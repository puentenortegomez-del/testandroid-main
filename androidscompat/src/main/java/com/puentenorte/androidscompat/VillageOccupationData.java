package com.puentenorte.androidscompat;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent server-wide occupation state for discovered villages. */
final class VillageOccupationData extends SavedData {
    private static final String DATA_NAME = "androidscompat_puente_norte_villages";
    private final Map<String, VillageRecord> villages = new LinkedHashMap<>();
    private final Map<String, Long> nextSkirmishAt = new LinkedHashMap<>();
    private int villageFactionAggression;

    static VillageOccupationData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                VillageOccupationData::load, VillageOccupationData::new, DATA_NAME);
    }

    VillageRecord getVillage(ResourceLocation dimension, BlockPos center) {
        String dimensionId = dimension.toString();
        String key = dimensionId + ":" + center.getX() + ":" + center.getZ();
        VillageRecord record = villages.get(key);
        if (record == null) {
            record = new VillageRecord(dimensionId, center.immutable());
            villages.put(key, record);
            setDirty();
        }
        return record;
    }

    Collection<VillageRecord> villages() {
        return new ArrayList<>(villages.values());
    }

    int occupiedCount() {
        return (int) villages.values().stream().filter(VillageRecord::isOccupied).count();
    }

    int activeSiegeCount() {
        return (int) villages.values().stream().filter(VillageRecord::isSiegeActive).count();
    }

    int aggression() { return villageFactionAggression; }

    boolean villagesAreHostile() { return villageFactionAggression >= 100; }

    int addAggression(int amount) {
        if (amount <= 0) return villageFactionAggression;
        villageFactionAggression = Math.min(100, villageFactionAggression + amount);
        setDirty();
        return villageFactionAggression;
    }

    boolean canStartSkirmish(ResourceLocation dimension, BlockPos village, long gameTime) {
        return gameTime >= nextSkirmishAt.getOrDefault(villageKey(dimension, village), 0L);
    }

    void scheduleSkirmish(ResourceLocation dimension, BlockPos village, long nextTick) {
        nextSkirmishAt.put(villageKey(dimension, village), nextTick);
        setDirty();
    }

    private static String villageKey(ResourceLocation dimension, BlockPos center) {
        return dimension + ":" + center.getX() + ":" + center.getZ();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (VillageRecord record : villages.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("dimension", record.dimension);
            entry.putInt("x", record.center.getX());
            entry.putInt("y", record.center.getY());
            entry.putInt("z", record.center.getZ());
            entry.putInt("control", record.control);
            entry.putBoolean("siege", record.siegeActive);
            entry.putLong("nextRaidAt", record.nextRaidAt);
            entry.putLong("lastProgressAt", record.lastProgressAt);
            list.add(entry);
        }
        tag.put("villages", list);
        tag.putInt("villageFactionAggression", villageFactionAggression);
        ListTag skirmishes = new ListTag();
        nextSkirmishAt.forEach((key, value) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("key", key);
            entry.putLong("next", value);
            skirmishes.add(entry);
        });
        tag.put("skirmishes", skirmishes);
        return tag;
    }

    private static VillageOccupationData load(CompoundTag tag) {
        VillageOccupationData data = new VillageOccupationData();
        data.villageFactionAggression = Math.max(0,
            Math.min(100, tag.getInt("villageFactionAggression")));
        ListTag list = tag.getList("villages", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String dimension = entry.getString("dimension");
            if (dimension.isEmpty()) continue;
            BlockPos center = new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
            VillageRecord record = new VillageRecord(dimension, center);
            record.control = Math.max(0, Math.min(100, entry.getInt("control")));
            record.siegeActive = entry.getBoolean("siege");
            record.nextRaidAt = Math.max(0L, entry.getLong("nextRaidAt"));
            record.lastProgressAt = Math.max(0L, entry.getLong("lastProgressAt"));
            String key = dimension + ":" + center.getX() + ":" + center.getZ();
            data.villages.put(key, record);
        }
        ListTag skirmishes = tag.getList("skirmishes", 10);
        for (int i = 0; i < skirmishes.size(); i++) {
            CompoundTag entry = skirmishes.getCompound(i);
            String key = entry.getString("key");
            if (!key.isEmpty()) data.nextSkirmishAt.put(key, Math.max(0L, entry.getLong("next")));
        }
        return data;
    }

    static final class VillageRecord {
        final String dimension;
        final BlockPos center;
        int control;
        boolean siegeActive;
        long nextRaidAt;
        long lastProgressAt;

        private VillageRecord(String dimension, BlockPos center) {
            this.dimension = dimension;
            this.center = center;
        }

        int control() { return control; }
        boolean isOccupied() { return control >= 100; }
        boolean isSiegeActive() { return siegeActive; }
        long nextRaidAt() { return nextRaidAt; }
    }
}