package com.hoistway;

import java.util.List;
import java.util.OptionalInt;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The elevator car. Its collision box is only the floor slab; everything standing on the slab is
 * carried along as the car travels. The motor block above it decides where it may stop and how
 * fast it runs.
 */
public class ElevatorCarEntity extends Entity {
    /** Thickness of the floor slab. A parked car's floor sits this far above the landing. */
    public static final float FLOOR_HEIGHT = 0.1875F;
    /** Full height of the cage, floor to roof. */
    public static final double CAR_HEIGHT = 2.75;

    private static final double ACCELERATION = 0.012;
    private static final double CREEP_SPEED = 0.02;
    private static final int DWELL_TICKS = 40;

    private static final EntityDataAccessor<Integer> DATA_SIZE =
            SynchedEntityData.defineId(ElevatorCarEntity.class, EntityDataSerializers.INT);

    @Nullable
    private BlockPos motorPos;
    private double targetY = Double.NaN;
    private double velocity;
    private int dwell;
    /** 1 travelling up, -1 down, 0 idle. Kept through stops so the car finishes its sweep. */
    private int direction;
    private int announcedFloor = Integer.MIN_VALUE;

    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;

    public ElevatorCarEntity(EntityType<? extends ElevatorCarEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    // ---- Data ----

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SIZE, 1);
    }

    public int getSize() {
        return this.entityData.get(DATA_SIZE);
    }

    public void setSize(int size) {
        this.entityData.set(DATA_SIZE, size);
        this.refreshDimensions();
    }

    @Nullable
    public BlockPos getMotorPos() {
        return this.motorPos;
    }

    public void setMotorPos(@Nullable BlockPos motorPos) {
        this.motorPos = motorPos;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SIZE.equals(key)) {
            this.refreshDimensions();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.setSize(Math.max(1, tag.getInt("Size")));
        this.motorPos = tag.contains("Motor") ? BlockPos.of(tag.getLong("Motor")) : null;
        this.targetY = tag.contains("TargetY") ? tag.getDouble("TargetY") : Double.NaN;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Size", this.getSize());
        if (this.motorPos != null) {
            tag.putLong("Motor", this.motorPos.asLong());
        }
        if (!Double.isNaN(this.targetY)) {
            tag.putDouble("TargetY", this.targetY);
        }
    }

    // ---- Shape ----

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(this.getSize(), FLOOR_HEIGHT);
    }

    /** The whole cage, floor to roof. */
    public AABB cabinBox() {
        double half = this.getSize() / 2.0;
        return new AABB(this.getX() - half, this.getY(), this.getZ() - half,
                this.getX() + half, this.getY() + CAR_HEIGHT, this.getZ() + half);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return this.cabinBox();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256.0 * 256.0;
    }

    /**
     * Solid only on the client. Each client moves its own player and keeps it on the floor, but the
     * server checks that movement against where the car is on the server, a few ticks ahead of what
     * the client shows. A solid floor there makes the server reject the rider's move and drop them
     * through. Entities the server moves (mobs, items) are held on the floor by {@link #carryEntities}.
     */
    @Override
    public boolean canBeCollidedWith() {
        return this.level().isClientSide;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    protected MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(this.getSize() >= 3 ? Hoistway.LARGE_ELEVATOR_CAR.get() : Hoistway.ELEVATOR_CAR.get());
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isRemoved()) {
            return true;
        }
        if (!(source.getEntity() instanceof Player player) || this.isMoving()) {
            return false;
        }
        if (!player.getAbilities().instabuild) {
            this.spawnAtLocation(this.getPickResult());
        }
        this.playSound(SoundEvents.ANVIL_BREAK, 0.4F, 0.8F);
        this.discard();
        return true;
    }

    // ---- Network smoothing ----

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpSteps = Math.max(steps, 1);
    }

    @Override
    public double lerpTargetX() {
        return this.lerpSteps > 0 ? this.lerpX : this.getX();
    }

    @Override
    public double lerpTargetY() {
        return this.lerpSteps > 0 ? this.lerpY : this.getY();
    }

    @Override
    public double lerpTargetZ() {
        return this.lerpSteps > 0 ? this.lerpZ : this.getZ();
    }

    // ---- Ticking ----

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            // The client only follows the server's position; the local rider is carried in ClientHooks.
            if (this.lerpSteps > 0) {
                this.lerpPositionAndRotationStep(this.lerpSteps, this.lerpX, this.lerpY, this.lerpZ, this.getYRot(), this.getXRot());
                this.lerpSteps--;
            }
            return;
        }

        MotorBlockEntity motor = this.getMotor();
        double oldY = this.getY();
        double newY = oldY;
        if (motor == null) {
            this.velocity = 0.0;
            this.targetY = Double.NaN;
        } else {
            if (Double.isNaN(this.targetY)) {
                this.velocity = 0.0;
                if (this.dwell > 0) {
                    this.dwell--;
                } else {
                    int here = (int) Math.round(oldY);
                    if (Math.abs(here - oldY) < 0.01 && motor.getRequests().contains(here)) {
                        this.arrive(motor, here);
                    } else {
                        OptionalInt next = this.chooseNext(motor, oldY);
                        if (next.isPresent()) {
                            this.beginMove(next.getAsInt());
                        } else {
                            this.direction = 0;
                        }
                    }
                }
            }
            if (!Double.isNaN(this.targetY)) {
                this.retarget(motor, oldY);
                newY = this.advance(motor, oldY);
                this.announcePassing(motor, newY);
            }
        }

        if (newY != oldY) {
            this.setPos(this.getX(), newY, this.getZ());
        }
        this.carryEntities(oldY + FLOOR_HEIGHT, newY + FLOOR_HEIGHT);
    }

    @Nullable
    private MotorBlockEntity getMotor() {
        if (this.motorPos != null && this.level().isLoaded(this.motorPos)
                && this.level().getBlockEntity(this.motorPos) instanceof MotorBlockEntity motor) {
            return motor;
        }
        return null;
    }

    public boolean isMoving() {
        return !Double.isNaN(this.targetY);
    }

    /**
     * Collective control: keep going the way the car is already heading while anyone ahead has
     * asked for a floor, then turn round.
     */
    private OptionalInt chooseNext(MotorBlockEntity motor, double y) {
        Integer above = null;
        Integer below = null;
        for (int floor : motor.getRequests()) {
            if (floor > y + 0.01 && above == null) {
                above = floor;
            } else if (floor < y - 0.01) {
                below = floor;
            }
        }
        if (above == null && below == null) {
            return OptionalInt.empty();
        }
        if (above == null) {
            return OptionalInt.of(below);
        }
        if (below == null) {
            return OptionalInt.of(above);
        }
        if (this.direction > 0) {
            return OptionalInt.of(above);
        }
        if (this.direction < 0) {
            return OptionalInt.of(below);
        }
        return OptionalInt.of(above - y <= y - below ? above : below);
    }

    /** Someone called the car from a floor it is about to pass: stop there if it can still brake. */
    private void retarget(MotorBlockEntity motor, double y) {
        double braking = this.velocity * this.velocity / (2.0 * ACCELERATION) + this.velocity;
        for (int floor : motor.getRequests()) {
            boolean ahead = this.direction > 0
                    ? floor > y + braking && floor < this.targetY
                    : floor < y - braking && floor > this.targetY;
            if (ahead && Math.abs(floor - y) < Math.abs(this.targetY - y)) {
                this.targetY = floor;
            }
        }
    }

    /** Tells the riders which floor they are passing. */
    private void announcePassing(MotorBlockEntity motor, double y) {
        int nearest = (int) Math.round(y);
        if (Math.abs(nearest - y) > 0.5 || nearest == this.announcedFloor || Double.isNaN(this.targetY)) {
            return;
        }
        int number = motor.floorNumber(nearest);
        if (number > 0) {
            this.announcedFloor = nearest;
            this.tellRiders(Component.translatable(this.direction > 0 ? "hoistway.car.passing_up" : "hoistway.car.passing_down", number));
        }
    }

    private void tellRiders(Component message) {
        for (Player player : this.level().getEntitiesOfClass(Player.class, this.cabinBox().inflate(0.3, 0.5, 0.3))) {
            player.displayClientMessage(message, true);
        }
    }

    private void beginMove(int floor) {
        this.direction = floor > this.getY() ? 1 : -1;
        this.targetY = floor;
        this.velocity = 0.0;
        this.dwell = 0;
        this.level().playSound(null, this.getX(), this.getY() + 1.0, this.getZ(),
                SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.25F, 0.6F);
    }

    private void arrive(MotorBlockEntity motor, int floor) {
        this.targetY = Double.NaN;
        this.velocity = 0.0;
        this.dwell = DWELL_TICKS;
        this.announcedFloor = floor;
        motor.clearRequest(floor);
        int number = motor.floorNumber(floor);
        if (number > 0) {
            this.tellRiders(Component.translatable("hoistway.car.floor", number));
        }
        this.level().playSound(null, this.getX(), floor + 1.0, this.getZ(),
                SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.8F, 1.4F);
    }

    /** Stops where it is and gives up on the current destination. */
    private void halt(MotorBlockEntity motor, boolean blocked) {
        this.targetY = Double.NaN;
        this.velocity = 0.0;
        this.dwell = 20;
        this.direction = 0;
        motor.clearRequests();
        if (blocked) {
            this.level().playSound(null, this.getX(), this.getY() + 1.0, this.getZ(),
                    SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.25F, 0.5F);
        }
    }

    /** Moves one tick toward the target and returns the new height. */
    private double advance(MotorBlockEntity motor, double y) {
        double distance = this.targetY - y;
        double remaining = Math.abs(distance);
        if (remaining < 1.0E-6) {
            int floor = (int) Math.round(this.targetY);
            this.arrive(motor, floor);
            return floor;
        }

        double direction = Math.signum(distance);
        double brakingSpeed = Math.sqrt(2.0 * ACCELERATION * remaining);
        this.velocity = Math.min(Math.min(this.velocity + ACCELERATION, motor.getSpeed()), Math.max(brakingSpeed, CREEP_SPEED));
        double step = Math.min(this.velocity, remaining);
        double next = y + direction * step;

        // The cable only reaches so far, and the car cannot rise into the motor.
        int motorY = motor.getBlockPos().getY();
        double lowest = motorY - 3 - motor.getCableLength();
        double highest = motorY - CAR_HEIGHT;
        if (y < lowest - 0.01 || (direction < 0 && next < lowest - 1.0E-6) || (direction > 0 && next > highest + 1.0E-6)) {
            this.halt(motor, false);
            return y;
        }

        AABB moved = this.cabinBox().move(0.0, next - y, 0.0).deflate(0.02, 0.001, 0.02);
        if (this.level().getBlockCollisions(this, moved).iterator().hasNext()) {
            this.halt(motor, true);
            return y;
        }

        if (direction < 0) {
            double half = this.getSize() / 2.0 - 0.05;
            AABB under = new AABB(this.getX() - half, next - 0.05, this.getZ() - half, this.getX() + half, y - 0.01, this.getZ() + half);
            if (!this.level().getEntitiesOfClass(LivingEntity.class, under, entity -> !entity.isSpectator()).isEmpty()) {
                // Somebody is under the car: wait for them to move.
                this.velocity = 0.0;
                return y;
            }
        }

        if (step >= remaining - 1.0E-9) {
            int floor = (int) Math.round(this.targetY);
            this.arrive(motor, floor);
            return floor;
        }
        return next;
    }

    /** Keeps everything standing on the floor glued to it. Players move themselves on their own client. */
    private void carryEntities(double oldTop, double newTop) {
        double half = this.getSize() / 2.0;
        double low = Math.min(oldTop, newTop) - FLOOR_HEIGHT - 0.05;
        double high = Math.max(oldTop, newTop) + 0.15;
        AABB area = new AABB(this.getX() - half, low, this.getZ() - half,
                this.getX() + half, this.getY() + CAR_HEIGHT, this.getZ() + half);

        for (Entity entity : this.level().getEntities(this, area)) {
            if (entity instanceof ElevatorCarEntity || entity instanceof HangingEntity
                    || entity.isSpectator() || entity.noPhysics || entity.isPassenger()) {
                continue;
            }
            if (Math.abs(entity.getX() - this.getX()) > half + 0.1 || Math.abs(entity.getZ() - this.getZ()) > half + 0.1) {
                continue;
            }
            if (entity instanceof ServerPlayer player) {
                // Riding a rising floor looks like flying to the server.
                if (player.connection != null) {
                    player.connection.aboveGroundTickCount = 0;
                }
                player.resetFallDistance();
                continue;
            }
            double feet = entity.getY();
            if (feet < low || feet > high) {
                continue;
            }
            if (oldTop != newTop || feet < newTop) {
                entity.setPos(entity.getX(), newTop, entity.getZ());
            }
            entity.setOnGround(true);
            entity.resetFallDistance();
            Vec3 motion = entity.getDeltaMovement();
            if (motion.y < 0.0) {
                entity.setDeltaMovement(motion.x, 0.0, motion.z);
            }
        }
    }

    // ---- Rider controls ----

    public boolean isRider(Player player) {
        double half = this.getSize() / 2.0 + 0.3;
        return Math.abs(player.getX() - this.getX()) <= half
                && Math.abs(player.getZ() - this.getZ()) <= half
                && player.getY() >= this.getY() - 0.5
                && player.getY() <= this.getY() + CAR_HEIGHT;
    }

    /** Heads for the next stop above ({@code 1}) or below ({@code -1}). Pressing again while moving goes one further. */
    public void requestStep(int direction) {
        MotorBlockEntity motor = this.getMotor();
        if (motor == null) {
            return;
        }
        double from;
        if (Double.isNaN(this.targetY)) {
            from = this.getY();
        } else {
            if (Math.signum(this.targetY - this.getY()) != direction) {
                return;
            }
            from = this.targetY;
        }

        List<Integer> stops = motor.getStops();
        Integer next = null;
        if (direction > 0) {
            for (int stop : stops) {
                if (stop > from + 0.01) {
                    next = stop;
                    break;
                }
            }
        } else {
            for (int i = stops.size() - 1; i >= 0; i--) {
                if (stops.get(i) < from - 0.01) {
                    next = stops.get(i);
                    break;
                }
            }
        }
        if (next != null) {
            motor.request(next);
        }
    }

    /** What the floor panel shows, or null if the car has no motor. */
    @Nullable
    public PanelPayloads.Contents panelContents() {
        MotorBlockEntity motor = this.getMotor();
        if (motor == null) {
            return null;
        }
        int here = (int) Math.round(this.getY());
        int current = Double.isNaN(this.targetY) && Math.abs(here - this.getY()) < 0.01 ? here : Integer.MIN_VALUE;
        return PanelPayloads.Contents.of(this.getId(), motor.getStops(), current, motor.getRequests());
    }

    /** A floor chosen on the panel inside the car. */
    public void requestFloor(int floorY) {
        MotorBlockEntity motor = this.getMotor();
        if (motor != null && !(Double.isNaN(this.targetY) && Math.abs(floorY - this.getY()) < 0.01)) {
            motor.request(floorY);
        }
    }
}
