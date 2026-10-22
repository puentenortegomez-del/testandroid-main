package com.puentenorte.androidscompat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/** Server-authoritative roster snapshots for the Nemesis screen. */
final class NemesisNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AndroidsCompatMod.MOD_ID, "nemesis_roster"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static boolean initialized;

    private NemesisNetwork() {}

    static void init() {
        if (initialized) return;
        initialized = true;
        CHANNEL.registerMessage(0, Request.class, Request::encode, Request::decode, Request::handle);
        CHANNEL.registerMessage(1, Snapshot.class, Snapshot::encode, Snapshot::decode, Snapshot::handle);
    }

    static void request() { CHANNEL.sendToServer(new Request()); }

    private static Snapshot snapshot(ServerPlayer player) {
        List<LivingEntity> nearby = player.serverLevel().getEntitiesOfClass(
                LivingEntity.class, player.getBoundingBox().inflate(AndroidsCompatMod.PN_ROSTER_RADIUS.get()),
                entity -> entity.isAlive() && entity.getPersistentData()
                        .getBoolean(PuenteNorteRaider.FACTION_TAG));
        nearby.sort(Comparator.comparingInt((LivingEntity entity) ->
                        (entity.getPersistentData().getBoolean(PuenteNorteRaider.NEMESIS_TAG) ? 1000 : 0)
                                + (entity instanceof PuenteNorteBoss ? 500 : 0)
                                + entity.getPersistentData().getInt(PuenteNorteRaider.RANK_TAG))
                .reversed().thenComparingDouble(player::distanceToSqr));
        List<String> lines = new ArrayList<>();
        int activeCount = Math.min(20, nearby.size());
        for (LivingEntity raider : nearby.stream().limit(activeCount).toList()) {
            int rank = Math.max(1, raider.getPersistentData().getInt(PuenteNorteRaider.RANK_TAG));
            String kind;
            String faction;
            String adaptation = "";
            if (raider instanceof PuenteNorteBoss boss) {
                kind = boss.getCustomName() == null ? "Jefe" : boss.getCustomName().getString();
                faction = boss.getBossKind();
            } else if (raider instanceof PuenteNorteSoldier soldier) {
                kind = switch (soldier.getArchetype()) {
                    case "mini_boss" -> "Mini jefe";
                    case "stalker" -> "Stalker";
                    default -> rank > 1 ? "Veterano" : "Soldado";
                };
                faction = soldier.getPersistentData().getString(PuenteNorteSoldier.SUBFACTION_TAG);
                String trait = soldier.getPersistentData().getString(PuenteNorteSoldier.TRAIT_TAG);
                if (!trait.isEmpty()) adaptation = " · adaptación " + trait + " "
                        + soldier.getPersistentData().getInt(PuenteNorteSoldier.TRAIT_LEVEL_TAG);
                String traits = PuenteNorteFaction.adaptationSummary(soldier);
                if (!traits.isEmpty()) adaptation = " · resiste " + traits;
                if (soldier.getPersistentData().getBoolean(PuenteNorteRaider.NEMESIS_TAG)) {
                    adaptation += " · rastrea a su objetivo";
                }
            } else if (raider instanceof PuenteNorteRogueAndroid) {
                kind = "Android Rogue";
                faction = "Puente Norte";
            } else if (raider instanceof PuenteNorteBasicAndroid) {
                kind = "Android básico";
                faction = "Puente Norte";
            } else {
                kind = "Combatiente";
                faction = "Puente Norte";
            }
            String name = raider.getCustomName() == null ? kind : raider.getCustomName().getString();
            String trait = raider.getPersistentData().getBoolean(PuenteNorteRaider.NEMESIS_TAG)
                    ? "NÉMESIS" : kind;
            lines.add("§6R" + rank + " §f" + name + " §8| §e" + trait + " §8| §7"
                    + faction + " §8| §c" + Math.round(raider.getHealth()) + "/"
                    + Math.round(raider.getMaxHealth()) + adaptation);
        }
        int deaths = player.getPersistentData().getInt("androidscompat_pn_deaths");
        int nemesisKills = player.getPersistentData().getInt("androidscompat_pn_nemesis_kills");
        int threatRank = Math.min(5, 1 + (deaths + nemesisKills) / 3);
        String header = "Muertes: " + deaths + " · Némesis derrotados: " + nemesisKills
                + " · amenaza: R" + threatRank + " · radio " + AndroidsCompatMod.PN_ROSTER_RADIUS.get() + ": " + activeCount;
        if (lines.isEmpty()) lines.add("§7No hay tropas ni Némesis activos dentro del radio configurado.");
        net.minecraft.nbt.ListTag history = player.getPersistentData().getList(
                "androidscompat_pn_nemesis_history", net.minecraft.nbt.Tag.TAG_COMPOUND);
        if (!history.isEmpty()) {
            lines.add("§bHistorial reciente de Némesis derrotados:");
            for (int i = Math.max(0, history.size() - 8); i < history.size(); i++) {
                net.minecraft.nbt.CompoundTag record = history.getCompound(i);
                String adaptation = record.getString("adaptation");
                if (record.contains("adaptationSummary")) {
                    adaptation = record.getString("adaptationSummary");
                }
                lines.add("§8R" + Math.max(1, record.getInt("rank")) + " · §7" + record.getString("name")
                        + " §8| " + record.getString("faction") + " / " + record.getString("type")
                    + (adaptation.isEmpty() ? "" : " · resistencias: " + adaptation));
            }
        }
        return new Snapshot(header, lines);
    }

    private record Request() {
        private static void encode(Request packet, FriendlyByteBuf buffer) {}
        private static Request decode(FriendlyByteBuf buffer) { return new Request(); }
        private static void handle(Request packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), snapshot(player));
            });
            context.setPacketHandled(true);
        }
    }

    private record Snapshot(String header, List<String> lines) {
        private static void encode(Snapshot packet, FriendlyByteBuf buffer) {
            buffer.writeUtf(packet.header, 256);
            buffer.writeVarInt(packet.lines.size());
            packet.lines.forEach(line -> buffer.writeUtf(line, 512));
        }
        private static Snapshot decode(FriendlyByteBuf buffer) {
            String header = buffer.readUtf(256);
            int count = Math.min(32, buffer.readVarInt());
            List<String> lines = new ArrayList<>(count);
            for (int i = 0; i < count; i++) lines.add(buffer.readUtf(512));
            return new Snapshot(header, lines);
        }
        private static void handle(Snapshot packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> NemesisClient.update(packet.header, packet.lines)));
            context.setPacketHandled(true);
        }
    }
}
