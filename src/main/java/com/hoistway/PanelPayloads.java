package com.hoistway;

import java.util.ArrayList;
import java.util.List;

import com.hoistway.client.ClientHooks;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The floor panel inside the car: the client asks to open it, the server sends the floors, the client picks one. */
public final class PanelPayloads {
    private PanelPayloads() {
    }

    /** Client to server: the rider wants to see the panel. */
    public record Open(int carId) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(Hoistway.id("panel_open"));
        public static final StreamCodec<ByteBuf, Open> STREAM_CODEC =
                ByteBufCodecs.VAR_INT.map(Open::new, Open::carId);

        @Override
        public Type<Open> type() {
            return TYPE;
        }

        public static void handle(Open payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                Player player = context.player();
                Entity entity = player.level().getEntity(payload.carId());
                if (entity instanceof ElevatorCarEntity car && car.isRider(player) && player instanceof ServerPlayer serverPlayer) {
                    Contents contents = car.panelContents();
                    if (contents != null) {
                        PacketDistributor.sendToPlayer(serverPlayer, contents);
                    }
                }
            });
        }
    }

    /** Server to client: the stops, which one the car is parked at, and which are already requested. */
    public record Contents(int carId, List<Integer> stops, int current, List<Integer> requested) implements CustomPacketPayload {
        public static final Type<Contents> TYPE = new Type<>(Hoistway.id("panel_contents"));
        public static final StreamCodec<ByteBuf, Contents> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Contents::carId,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Contents::stops,
                ByteBufCodecs.INT, Contents::current,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Contents::requested,
                Contents::new);

        public Contents {
            stops = List.copyOf(stops);
            requested = List.copyOf(requested);
        }

        public static Contents of(int carId, List<Integer> stops, int current, Iterable<Integer> requested) {
            List<Integer> lit = new ArrayList<>();
            requested.forEach(lit::add);
            return new Contents(carId, stops, current, lit);
        }

        @Override
        public Type<Contents> type() {
            return TYPE;
        }

        public static void handle(Contents payload, IPayloadContext context) {
            context.enqueueWork(() -> ClientHooks.openPanel(payload));
        }
    }

    /** Client to server: a floor was pressed on the panel. */
    public record Select(int carId, int floorY) implements CustomPacketPayload {
        public static final Type<Select> TYPE = new Type<>(Hoistway.id("panel_select"));
        public static final StreamCodec<ByteBuf, Select> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Select::carId,
                ByteBufCodecs.INT, Select::floorY,
                Select::new);

        @Override
        public Type<Select> type() {
            return TYPE;
        }

        public static void handle(Select payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                Player player = context.player();
                Entity entity = player.level().getEntity(payload.carId());
                if (entity instanceof ElevatorCarEntity car && car.isRider(player)) {
                    car.requestFloor(payload.floorY());
                }
            });
        }
    }
}
