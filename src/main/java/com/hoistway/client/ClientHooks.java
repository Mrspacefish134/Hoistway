package com.hoistway.client;

import java.util.List;

import javax.annotation.Nullable;

import com.hoistway.CallButtonBlock;
import com.hoistway.CarStepPayload;
import com.hoistway.ElevatorCarEntity;
import com.hoistway.Hoistway;
import com.hoistway.MotorBlockEntity;
import com.hoistway.PanelPayloads;

import net.minecraft.resources.ResourceLocation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-only pieces of the elevator: carrying the local player, the ride controls and the music. */
public final class ClientHooks {
    private static int ridingCarId = -1;
    private static long ridingTick = Long.MIN_VALUE;
    private static boolean jumpWasDown;
    private static boolean sneakWasDown;
    @Nullable
    private static ElevatorMusic music;
    /** Which of the motor's discs is playing. */
    private static int track;
    private static long retryAt = Long.MIN_VALUE;

    private ClientHooks() {
    }

    /** The car the local player is standing in, if any. */
    @Nullable
    public static ElevatorCarEntity ridingCar() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.getGameTime() - ridingTick > 2L) {
            return null;
        }
        return minecraft.level.getEntity(ridingCarId) instanceof ElevatorCarEntity car ? car : null;
    }

    /** Asks the server for the floor panel of the car the player is standing in. */
    public static boolean requestPanel() {
        ElevatorCarEntity car = ridingCar();
        if (car == null) {
            return false;
        }
        PacketDistributor.sendToServer(new PanelPayloads.Open(car.getId()));
        return true;
    }

    public static void openPanel(PanelPayloads.Contents contents) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null && ridingCar() != null) {
            minecraft.setScreen(new CarPanelScreen(contents));
        }
    }

    /** Right-click with an empty hand while standing in the car opens its floor panel. */
    static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!event.isUseItem() || player == null || !player.getMainHandItem().isEmpty() || ridingCar() == null) {
            return;
        }
        HitResult hit = minecraft.hitResult;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                && minecraft.level.getBlockState(blockHit.getBlockPos()).getBlock() instanceof CallButtonBlock) {
            return;
        }
        if (hit instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            if (!(target instanceof ElevatorCarEntity)) {
                return;
            }
        }
        if (requestPanel()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
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

        // Elevator music: starts when you step into a car, fades out when you step off. Music discs
        // in the motor play instead, one after another, round and round.
        ElevatorCarEntity car = ridingCar();
        if (music != null && (music.isStopped() || !minecraft.getSoundManager().isActive(music))) {
            if (!music.isStopped()) {
                // The disc finished by itself: on to the next. One that ended at once could not be
                // played, so wait a while before trying again rather than retrying every tick.
                track++;
                if (music.ticksPlayed() < 20) {
                    retryAt = minecraft.level.getGameTime() + 100L;
                }
            }
            music = null;
        }
        if (car == null) {
            return;
        }
        MotorBlockEntity motor = car.getMotor();
        List<ResourceLocation> discs = motor != null ? motor.getClientDiscs() : List.of();
        ResourceLocation wanted = discs.isEmpty() ? Hoistway.ELEVATOR_MUSIC.getId() : discs.get(Math.floorMod(track, discs.size()));
        if (music != null && music.car() == car && music.track().equals(wanted)) {
            return;
        }
        if (music == null && minecraft.level.getGameTime() < retryAt) {
            return;
        }
        if (music != null) {
            if (music.car() != car) {
                track = 0;
            }
            music.fadeOut();
        }
        music = new ElevatorMusic(car, wanted, discs.isEmpty());
        minecraft.getSoundManager().play(music);
    }

    static boolean isRiding(ElevatorCarEntity car) {
        return ridingCar() == car;
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
