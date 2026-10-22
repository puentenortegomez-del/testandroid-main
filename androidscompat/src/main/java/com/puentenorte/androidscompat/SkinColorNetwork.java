package com.puentenorte.androidscompat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;
import java.util.function.Supplier;

/** Synchronizes the cosmetic skin tint without changing the Android variant. */
final class SkinColorNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AndroidsCompatMod.MOD_ID, "skin_color"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);
    private static boolean initialized;

    private SkinColorNetwork() {
    }

    static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        CHANNEL.registerMessage(0, SkinColorMessage.class,
                SkinColorMessage::encode,
                SkinColorMessage::decode,
                SkinColorMessage::handle);
    }

    static void sendToTracking(Entity entity, int color) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                new SkinColorMessage(entity.getUUID(), color));
    }

    static void sendToPlayer(ServerPlayer player, Entity entity, int color) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SkinColorMessage(entity.getUUID(), color));
    }

    private record SkinColorMessage(UUID entityId, int color) {
        private static void encode(SkinColorMessage message,
                                   net.minecraft.network.FriendlyByteBuf buffer) {
            buffer.writeUUID(message.entityId);
            buffer.writeInt(message.color);
        }

        private static SkinColorMessage decode(net.minecraft.network.FriendlyByteBuf buffer) {
            return new SkinColorMessage(buffer.readUUID(), buffer.readInt());
        }

        private static void handle(SkinColorMessage message,
                                   Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() ->
                    net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                            net.minecraftforge.api.distmarker.Dist.CLIENT,
                            () -> () -> SkinColorClient.apply(
                                    message.entityId, message.color)));
            context.setPacketHandled(true);
        }
    }
}
