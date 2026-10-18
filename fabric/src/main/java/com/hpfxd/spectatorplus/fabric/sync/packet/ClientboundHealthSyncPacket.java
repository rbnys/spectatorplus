package com.hpfxd.spectatorplus.fabric.sync.packet;

import com.hpfxd.spectatorplus.fabric.sync.ClientboundSyncPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record ClientboundHealthSyncPacket(
        UUID playerId,
        float health
) implements ClientboundSyncPacket {
    public static final StreamCodec<FriendlyByteBuf, ClientboundHealthSyncPacket> STREAM_CODEC = CustomPacketPayload.codec(ClientboundHealthSyncPacket::write, ClientboundHealthSyncPacket::new);
    public static final CustomPacketPayload.Type<ClientboundHealthSyncPacket> TYPE = new CustomPacketPayload.Type<>(Identifier.parse("spectatorplus:health_sync"));

    public static ClientboundHealthSyncPacket initializing(ServerPlayer target) {
        return new ClientboundHealthSyncPacket(target.getUUID(), target.getHealth());
    }

    public ClientboundHealthSyncPacket(FriendlyByteBuf buf) {
        this(buf.readUUID(), buf.readFloat());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.playerId);
        buf.writeFloat(this.health);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public boolean canSend(ServerPlayer receiver) {
        return true;
    }
}
