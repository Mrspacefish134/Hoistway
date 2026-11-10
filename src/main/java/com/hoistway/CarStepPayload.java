package com.hoistway;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Sent by a rider: jump asks the car for the next stop up, sneak for the next stop down. */
public record CarStepPayload(int carId, boolean up) implements CustomPacketPayload {
    public static final Type<CarStepPayload> TYPE = new Type<>(Hoistway.id("car_step"));

    public static final StreamCodec<ByteBuf, CarStepPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CarStepPayload::carId,
            ByteBufCodecs.BOOL, CarStepPayload::up,
            CarStepPayload::new);

    @Override
    public Type<CarStepPayload> type() {
        return TYPE;
    }

    public static void handle(CarStepPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            Entity entity = player.level().getEntity(payload.carId());
            if (entity instanceof ElevatorCarEntity car && car.isRider(player)) {
                car.requestStep(payload.up() ? 1 : -1);
            }
        });
    }
}
