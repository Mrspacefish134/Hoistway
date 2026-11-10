package com.hoistway;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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
    /** How long the cabin doors take to open or close. */
    public static final int DOOR_TICKS = 16;
    /**
     * Clients see the car and its doors a few ticks behind the server. The car waits this long
     * after its doors shut before leaving, and after stopping before opening them, so that on
     * screen the doors are never moving while the car is.
     */
    private static final int DOOR_SETTLE_TICKS = 4;

    private static final EntityDataAccessor<Integer> DATA_SIZE =
            SynchedEntityData.defineId(ElevatorCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STYLE =
            SynchedEntityData.defineId(ElevatorCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Direction> DATA_DOOR_SIDE =
            SynchedEntityData.defineId(ElevatorCarEntity.class, EntityDataSerializers.DIRECTION);
    private static final EntityDataAccessor<Boolean> DATA_DOORS_OPEN =
            SynchedEntityData.defineId(ElevatorCarEntity.class, EntityDataSerializers.BOOLEAN);
    /** What the floor display shows: the floor number (0 for none) and the travel direction. */
    private static final EntityDataAccessor<Integer> DATA_FLOOR =
            SynchedEntityData.defineId(ElevatorCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HEADING =
            SynchedEntityData.defineId(ElevatorCarEntity.class, EntityDataSerializers.INT);
    /** Synced so the client can find the motor's music discs. */
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_MOTOR =
            SynchedEntityData.defineId(ElevatorCarEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);

    private double targetY = Double.NaN;
    private double velocity;
    private int dwell;
    /** 1 travelling up, -1 down, 0 idle. Kept through stops so the car finishes its sweep. */
    private int direction;
    private int announcedFloor = Integer.MIN_VALUE;
    /**
     * Server: how far open the doors are, 0 to DOOR_TICKS. Once shut it keeps counting down to
     * -DOOR_SETTLE_TICKS, and only then may the car leave.
     */
    private int doorTicks = DOOR_TICKS;
    /** Server: ticks until the doors open after the car has stopped. */
    private int doorOpenDelay;
    /** Client: door opening for drawing, 0 shut to 1 open. */
    private float doorOpen = 1.0F;
    private float oDoorOpen = 1.0F;

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
        builder.define(DATA_STYLE, CabinStyle.STANDARD.ordinal());
        builder.define(DATA_DOOR_SIDE, Direction.SOUTH);
        builder.define(DATA_DOORS_OPEN, true);
        builder.define(DATA_MOTOR, Optional.empty());
        builder.define(DATA_FLOOR, 0);
        builder.define(DATA_HEADING, 0);
    }

    /** The floor number on the display, or 0 before the car knows it. */
    public int getDisplayFloor() {
        return this.entityData.get(DATA_FLOOR);
    }

    /** 1 going up, -1 going down, 0 with nowhere to go. */
    public int getHeading() {
        return this.entityData.get(DATA_HEADING);
    }

    public CabinStyle getStyle() {
        return CabinStyle.byId(this.entityData.get(DATA_STYLE));
    }

    public void setStyle(CabinStyle style) {
        this.entityData.set(DATA_STYLE, style.ordinal());
    }

    /** The side of the cabin the doors are on. */
    public Direction getDoorSide() {
        return this.entityData.get(DATA_DOOR_SIDE);
    }

    public void setDoorSide(Direction side) {
        this.entityData.set(DATA_DOOR_SIDE, side.getAxis().isHorizontal() ? side : Direction.SOUTH);
    }

    public boolean areDoorsOpen() {
        return this.entityData.get(DATA_DOORS_OPEN);
    }

    private void setDoorsOpen(boolean open) {
        this.entityData.set(DATA_DOORS_OPEN, open);
    }

    /** Client: how far open the doors are drawn this frame. */
    public float getDoorOpen(float partialTick) {
        return Mth.lerp(partialTick, this.oDoorOpen, this.doorOpen);
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
        return this.entityData.get(DATA_MOTOR).orElse(null);
    }

    public void setMotorPos(@Nullable BlockPos motorPos) {
        this.entityData.set(DATA_MOTOR, Optional.ofNullable(motorPos));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SIZE.equals(key)) {
            this.refreshDimensions();
        }
        if (DATA_DOORS_OPEN.equals(key) && this.firstTick) {
            // A car coming into view shows its doors as they are, without animating them.
            this.doorOpen = this.oDoorOpen = this.areDoorsOpen() ? 1.0F : 0.0F;
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.setSize(Math.max(1, tag.getInt("Size")));
        this.setStyle(CabinStyle.byId(tag.getInt("Style")));
        this.setDoorSide(Direction.from2DDataValue(tag.getInt("DoorSide")));
        boolean open = !tag.contains("DoorsOpen") || tag.getBoolean("DoorsOpen");
        this.setDoorsOpen(open);
        this.doorTicks = open ? DOOR_TICKS : 0;
        this.setMotorPos(tag.contains("Motor") ? BlockPos.of(tag.getLong("Motor")) : null);
        this.targetY = tag.contains("TargetY") ? tag.getDouble("TargetY") : Double.NaN;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Size", this.getSize());
        tag.putInt("Style", this.getStyle().ordinal());
        tag.putInt("DoorSide", this.getDoorSide().get2DDataValue());
        tag.putBoolean("DoorsOpen", this.areDoorsOpen());
        BlockPos motorPos = this.getMotorPos();
        if (motorPos != null) {
            tag.putLong("Motor", motorPos.asLong());
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
            Item kit = this.getStyle().kit();
            if (kit != null) {
                this.spawnAtLocation(kit);
            }
        }
        this.playSound(SoundEvents.ANVIL_BREAK, 0.4F, 0.8F);
        this.discard();
        return true;
    }

    /**
     * Using a cabin kit on the car fits that cabin, with its doors facing you. Using the kit the car
     * already has turns the doors to face you. Sneak and use an empty hand to take the kit back out.
     */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        CabinStyle current = this.getStyle();
        CabinStyle fitting = held.getItem() instanceof CabinKitItem kit ? kit.style() : null;
        boolean removing = fitting == null && held.isEmpty() && player.isSecondaryUseActive() && current != CabinStyle.STANDARD;
        if (fitting == null && !removing) {
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (this.isMoving()) {
            return InteractionResult.FAIL;
        }

        boolean survival = !player.getAbilities().instabuild;
        Item oldKit = current.kit();
        if (removing) {
            this.setStyle(CabinStyle.STANDARD);
        } else {
            this.setDoorSide(player.getDirection().getOpposite());
            if (fitting != current) {
                this.setStyle(fitting);
                if (survival) {
                    held.shrink(1);
                }
            } else {
                oldKit = null;
            }
        }
        if (oldKit != null && survival) {
            ItemStack back = new ItemStack(oldKit);
            if (!player.getInventory().add(back)) {
                player.drop(back, false);
            }
        }
        this.setDoorsOpen(true);
        this.doorTicks = DOOR_TICKS;
        this.playSound(SoundEvents.SMITHING_TABLE_USE, 0.6F, 1.0F);
        return InteractionResult.CONSUME;
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
            this.oDoorOpen = this.doorOpen;
            float step = 1.0F / DOOR_TICKS;
            this.doorOpen = this.areDoorsOpen() ? Math.min(1.0F, this.doorOpen + step) : Math.max(0.0F, this.doorOpen - step);
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
            boolean doors = this.getStyle().hasDoors();
            if (this.doorOpenDelay > 0 && --this.doorOpenDelay == 0) {
                this.setDoorsOpen(true);
            }
            this.doorTicks = this.areDoorsOpen()
                    ? Math.min(DOOR_TICKS, Math.max(this.doorTicks, 0) + 1)
                    : Math.max(-DOOR_SETTLE_TICKS, this.doorTicks - 1);
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
                            if (doors && this.doorTicks > -DOOR_SETTLE_TICKS) {
                                // Shut the doors first; the car leaves once they have closed and settled.
                                this.setDoorsOpen(false);
                            } else {
                                this.beginMove(next.getAsInt());
                            }
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
            } else {
                // Parked: show the floor it is on, which also catches floors being renumbered.
                int here = (int) Math.round(newY);
                if (Math.abs(here - newY) < 0.01) {
                    int number = motor.floorNumber(here);
                    if (number > 0) {
                        this.entityData.set(DATA_FLOOR, number);
                    }
                }
            }
            this.entityData.set(DATA_HEADING, this.direction);
        }

        if (newY != oldY) {
            this.setPos(this.getX(), newY, this.getZ());
        }
        this.carryEntities(oldY + FLOOR_HEIGHT, newY + FLOOR_HEIGHT);
    }

    @Nullable
    public MotorBlockEntity getMotor() {
        BlockPos motorPos = this.getMotorPos();
        if (motorPos != null && this.level().isLoaded(motorPos)
                && this.level().getBlockEntity(motorPos) instanceof MotorBlockEntity motor) {
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

    /** Steps the floor display on to each floor the car passes. */
    private void announcePassing(MotorBlockEntity motor, double y) {
        int nearest = (int) Math.round(y);
        if (Math.abs(nearest - y) > 0.5 || nearest == this.announcedFloor || Double.isNaN(this.targetY)) {
            return;
        }
        int number = motor.floorNumber(nearest);
        if (number > 0) {
            this.announcedFloor = nearest;
            this.entityData.set(DATA_FLOOR, number);
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
        boolean travelled = !Double.isNaN(this.targetY);
        boolean doors = this.getStyle().hasDoors();
        this.targetY = Double.NaN;
        this.velocity = 0.0;
        // With doors, the wait starts once they are open, not while they are still opening.
        this.dwell = DWELL_TICKS + (doors ? DOOR_TICKS + DOOR_SETTLE_TICKS : 0);
        this.announcedFloor = floor;
        motor.clearRequest(floor);
        int number = motor.floorNumber(floor);
        if (number > 0) {
            this.entityData.set(DATA_FLOOR, number);
        }
        if (doors && travelled && !this.areDoorsOpen()) {
            // Just pulled in: let the car come to rest on screen before the doors start to open.
            this.doorOpenDelay = DOOR_SETTLE_TICKS;
        } else {
            // Called while already parked here: open, or reopen from wherever the doors have got to.
            this.setDoorsOpen(true);
        }
        this.level().playSound(null, this.getX(), floor + 1.0, this.getZ(),
                Hoistway.CAR_CHIME.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
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
