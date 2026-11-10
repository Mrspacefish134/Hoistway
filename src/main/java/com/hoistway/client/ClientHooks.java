package com.hoistway.client;

import com.hoistway.CarStepPayload;
import com.hoistway.ElevatorCarEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-only pieces of the elevator: carrying the local player and reading the ride controls. */
public final class ClientHooks {
    private static int ridingCarId = -1;
    private static long ridingTick = Long.MIN_VALUE;
    private static boolean jumpWasDown;
    private static boolean sneakWasDown;

    private ClientHooks() {
    }

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            jumpWasDown = false;
            sneakWasDown = false;
            return;
        }

        // Runs after every entity has ticked, so the car's move this tick (yo -> y) is final and the
        // rider's own previous position is intact: both then interpolate over the same frame span.
        if (!minecraft.isPaused()) {
            for (ElevatorCarEntity car : minecraft.level.getEntitiesOfClass(ElevatorCarEntity.class,
                    player.getBoundingBox().inflate(2.0, 3.0, 2.0))) {
                carry(player, car, car.yo + ElevatorCarEntity.FLOOR_HEIGHT, car.getY() + ElevatorCarEntity.FLOOR_HEIGHT);
            }
        }

        // Jump sends the car up a stop, sneak sends it down a stop.
        boolean jump = minecraft.options.keyJump.isDown();
        boolean sneak = minecraft.options.keyShift.isDown();
        boolean riding = minecraft.screen == null && minecraft.level.getGameTime() - ridingTick <= 2L;
        if (riding) {
            if (jump && !jumpWasDown) {
                PacketDistributor.sendToServer(new CarStepPayload(ridingCarId, true));
            } else if (sneak && !sneakWasDown) {
                PacketDistributor.sendToServer(new CarStepPayload(ridingCarId, false));
            }
        }
        jumpWasDown = jump;
        sneakWasDown = sneak;
    }

    /**
     * The local player's position is decided by this client, not the server, so the car has to
     * move them here to keep their feet on its floor.
     */
    private static void carry(LocalPlayer player, ElevatorCarEntity car, double oldTop, double newTop) {
        if (player.isSpectator() || player.isPassenger() || player.noPhysics || player.getAbilities().flying) {
            return;
        }
        double half = car.getSize() / 2.0 + 0.1;
        if (Math.abs(player.getX() - car.getX()) > half || Math.abs(player.getZ() - car.getZ()) > half) {
            return;
        }
        double feet = player.getY();
        double low = Math.min(oldTop, newTop) - ElevatorCarEntity.FLOOR_HEIGHT - 0.05;
        double high = Math.max(oldTop, newTop) + 0.15;
        if (feet < low || feet > high) {
            return;
        }

        if (oldTop != newTop || feet < newTop) {
            player.setPos(player.getX(), newTop, player.getZ());
        }
        player.setOnGround(true);
        player.resetFallDistance();
        Vec3 motion = player.getDeltaMovement();
        if (motion.y < 0.0) {
            player.setDeltaMovement(motion.x, 0.0, motion.z);
        }

        ridingCarId = car.getId();
        ridingTick = car.level().getGameTime();
    }
}
