package com.hoistway;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeMap;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/**
 * The winch at the top of the shaft. Holds the cable and upgrades, works out which floors are
 * stops (every call button near the shaft that the cable can reach) and queues the calls.
 */
public class MotorBlockEntity extends BaseContainerBlockEntity {
    public static final int SLOTS = 5;
    /** Call buttons this many blocks sideways from the motor belong to its shaft. */
    public static final int SCAN_RADIUS = 3;

    private static final double BASE_SPEED = 0.12;
    private static final double SPEED_PER_UPGRADE = 0.08;
    private static final int BASE_STOPS = 3;
    private static final int STOPS_PER_UPGRADE = 2;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final IntArrayList calls = new IntArrayList();
    private final List<Integer> stops = new ArrayList<>();
    private final Map<Integer, List<BlockPos>> buttons = new HashMap<>();
    private long lastScan = Long.MIN_VALUE;

    // Client only: what the renderer needs.
    private int clientCableLength;
    @Nullable
    private ElevatorCarEntity clientCar;
    private int clientSearchDelay;

    public MotorBlockEntity(BlockPos pos, BlockState state) {
        super(Hoistway.MOTOR_BE.get(), pos, state);
    }

    // ---- Contents ----

    public static boolean accepts(ItemStack stack) {
        return stack.is(Hoistway.CABLE) || stack.is(Hoistway.SPEED_UPGRADE) || stack.is(Hoistway.STOP_UPGRADE);
    }

    private int count(Item item) {
        int total = 0;
        for (ItemStack stack : this.items) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Blocks per tick at full speed. */
    public double getSpeed() {
        return BASE_SPEED + SPEED_PER_UPGRADE * this.count(Hoistway.SPEED_UPGRADE.get());
    }

    /** How many blocks below its top position the car can be lowered. */
    public int getCableLength() {
        return this.level != null && this.level.isClientSide ? this.clientCableLength : this.count(Hoistway.CABLE.get());
    }

    public int getMaxStops() {
        return BASE_STOPS + STOPS_PER_UPGRADE * this.count(Hoistway.STOP_UPGRADE.get());
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.lastScan = Long.MIN_VALUE;
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
        }
    }

    // ---- Stops and calls ----

    /** Floor heights the car may stop at, lowest first. */
    public List<Integer> getStops() {
        if (this.level != null && this.level.getGameTime() - this.lastScan > 10L) {
            this.scan();
        }
        return this.stops;
    }

    private void scan() {
        this.stops.clear();
        this.buttons.clear();
        if (this.level == null) {
            return;
        }
        this.lastScan = this.level.getGameTime();

        int top = this.worldPosition.getY() - 3;
        int bottom = Math.max(top - this.getCableLength(), this.level.getMinBuildHeight());
        TreeMap<Integer, List<BlockPos>> found = new TreeMap<>(Comparator.reverseOrder());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                int x = this.worldPosition.getX() + dx;
                int z = this.worldPosition.getZ() + dz;
                if (!this.level.isLoaded(cursor.set(x, top, z))) {
                    continue;
                }
                for (int y = bottom; y <= top + 1; y++) {
                    cursor.set(x, y, z);
                    if (this.level.getBlockState(cursor).is(Hoistway.CALL_BUTTON)) {
                        int floor = CallButtonBlock.floorLevel(this.level, cursor);
                        if (floor >= bottom && floor <= top) {
                            found.computeIfAbsent(floor, key -> new ArrayList<>()).add(cursor.immutable());
                        }
                    }
                }
            }
        }

        // Floors nearest the motor get served first when there are more buttons than stops.
        int max = this.getMaxStops();
        for (Map.Entry<Integer, List<BlockPos>> entry : found.entrySet()) {
            if (this.stops.size() >= max) {
                break;
            }
            this.stops.add(entry.getKey());
            this.buttons.put(entry.getKey(), entry.getValue());
        }
        Collections.sort(this.stops);
    }

    /** A call button was pressed. */
    public void call(BlockPos buttonPos, @Nullable Player player) {
        if (this.level == null) {
            return;
        }
        this.scan();
        int floor = CallButtonBlock.floorLevel(this.level, buttonPos);
        if (!this.stops.contains(floor)) {
            if (player != null) {
                int top = this.worldPosition.getY() - 3;
                String key = floor > top ? "hoistway.call.too_high"
                        : floor < top - this.getCableLength() ? "hoistway.call.no_cable"
                        : "hoistway.call.too_many_stops";
                player.displayClientMessage(Component.translatable(key), true);
            }
            return;
        }
        if (!this.calls.contains(floor)) {
            this.calls.add(floor);
            super.setChanged();
        }
        this.setLit(floor, true);
    }

    /** The oldest call that can still be served. */
    public OptionalInt peekCall() {
        while (!this.calls.isEmpty()) {
            int floor = this.calls.getInt(0);
            if (this.getStops().contains(floor)) {
                return OptionalInt.of(floor);
            }
            this.calls.removeInt(0);
        }
        return OptionalInt.empty();
    }

    /** The car reached (or gave up on) a floor. */
    public void finishCall(int floor) {
        if (this.calls.rem(floor)) {
            super.setChanged();
        }
        this.setLit(floor, false);
    }

    private void setLit(int floor, boolean lit) {
        if (this.level == null) {
            return;
        }
        for (BlockPos pos : this.buttons.getOrDefault(floor, List.of())) {
            BlockState state = this.level.getBlockState(pos);
            if (state.is(Hoistway.CALL_BUTTON) && state.getValue(CallButtonBlock.LIT) != lit) {
                this.level.setBlock(pos, state.setValue(CallButtonBlock.LIT, lit), 3);
            }
        }
    }

    /** The motor whose shaft a call button at {@code pos} belongs to. */
    @Nullable
    public static MotorBlockEntity findFor(Level level, BlockPos pos) {
        MotorBlockEntity best = null;
        int bestScore = Integer.MAX_VALUE;
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX + ox, chunkZ + oz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof MotorBlockEntity motor)) {
                        continue;
                    }
                    BlockPos motorPos = motor.getBlockPos();
                    int dx = Math.abs(motorPos.getX() - pos.getX());
                    int dz = Math.abs(motorPos.getZ() - pos.getZ());
                    if (dx > SCAN_RADIUS || dz > SCAN_RADIUS || motorPos.getY() <= pos.getY()) {
                        continue;
                    }
                    int score = Math.max(dx, dz) * 1000 + (motorPos.getY() - pos.getY());
                    if (score < bestScore) {
                        bestScore = score;
                        best = motor;
                    }
                }
            }
        }
        return best;
    }

    /** Whether a car is already hanging from the motor at {@code motorPos}. */
    public static boolean hasCar(Level level, BlockPos motorPos) {
        AABB column = new AABB(motorPos.getX() - 1.5, level.getMinBuildHeight(), motorPos.getZ() - 1.5,
                motorPos.getX() + 2.5, motorPos.getY(), motorPos.getZ() + 2.5);
        return !level.getEntitiesOfClass(ElevatorCarEntity.class, column, car -> motorPos.equals(car.getMotorPos())).isEmpty();
    }

    // ---- Client ----

    @Nullable
    public ElevatorCarEntity getClientCar() {
        return this.clientCar;
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, MotorBlockEntity motor) {
        if (motor.clientCar != null && (motor.clientCar.isRemoved() || motor.clientCar.level() != level)) {
            motor.clientCar = null;
        }
        if (motor.clientCar == null && motor.clientSearchDelay-- <= 0) {
            motor.clientSearchDelay = 10;
            AABB column = new AABB(pos.getX() + 0.25, level.getMinBuildHeight(), pos.getZ() + 0.25,
                    pos.getX() + 0.75, pos.getY(), pos.getZ() + 0.75);
            double nearest = Double.MAX_VALUE;
            for (ElevatorCarEntity car : level.getEntitiesOfClass(ElevatorCarEntity.class, column)) {
                if (Math.abs(car.getX() - (pos.getX() + 0.5)) > 0.1 || Math.abs(car.getZ() - (pos.getZ() + 0.5)) > 0.1) {
                    continue;
                }
                double gap = pos.getY() - car.getY();
                if (gap < nearest) {
                    nearest = gap;
                    motor.clientCar = car;
                }
            }
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("CableLength", this.count(Hoistway.CABLE.get()));
        return tag;
    }

    // ---- Storage ----

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items, registries);
        this.calls.clear();
        for (int floor : tag.getIntArray("Calls")) {
            this.calls.add(floor);
        }
        if (tag.contains("CableLength")) {
            this.clientCableLength = tag.getInt("CableLength");
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, this.items, registries);
        tag.putIntArray("Calls", this.calls.toIntArray());
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.hoistway.elevator_motor");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new MotorMenu(id, inventory, this);
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return accepts(stack);
    }
}
