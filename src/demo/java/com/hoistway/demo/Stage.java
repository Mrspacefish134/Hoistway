package com.hoistway.demo;

import com.hoistway.CallButtonBlock;
import com.hoistway.ElevatorCarEntity;
import com.hoistway.EscalatorBlock;
import com.hoistway.Hoistway;
import com.hoistway.MotorBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** The showcase world: a four-floor elevator tower with a glass side, and a pair of escalators. */
public final class Stage {
    /** Standing heights of floors 1 to 4. */
    public static final int[] FLOORS = {-60, -55, -50, -45};
    public static final BlockPos MOTOR = new BlockPos(0, -41, 0);
    /** Where the large car sits; it covers x and z from -1 to 1. */
    public static final BlockPos CAR_BASE = new BlockPos(0, -60, 0);
    /** Every floor's call button is on the corner pillar left of the doorway. */
    public static final int BUTTON_X = -2;
    public static final int BUTTON_Z = 3;

    public static final int ESCALATOR_X = 9;
    public static final int ESCALATOR_STEPS = 5;
    public static final int UP_Z = 0;
    public static final int DOWN_Z = 2;

    private Stage() {
    }

    public static BlockPos button(int floor) {
        return new BlockPos(BUTTON_X, FLOORS[floor - 1] + 1, BUTTON_Z);
    }

    public static void build(ServerLevel level, boolean withFirstButton) {
        BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();
        BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        // Clear the space in case the world is reused.
        fill(level, -8, -60, -6, 22, -30, 12, air);

        // Shaft: brick on the west and north, open on the east so the car and cables can be seen,
        // glass on the front between floors.
        fill(level, -2, -60, -2, -2, -43, 2, brick);
        fill(level, -2, -60, -2, 2, -43, -2, brick);
        fill(level, 2, -60, 2, 2, -43, 2, brick);
        fill(level, -1, -60, 2, 1, -43, 2, glass);

        // Ground floor and landings.
        fill(level, -4, -61, 2, 4, -61, 9, floor);
        for (int i = 1; i < FLOORS.length; i++) {
            int y = FLOORS[i] - 1;
            fill(level, -4, y, 3, 4, y, 8, floor);
            fill(level, -1, y, 2, 1, y, 2, floor);
            fill(level, -4, y + 1, 8, 4, y + 1, 8, pane);
            fill(level, -4, y + 1, 3, -4, y + 1, 8, pane);
            fill(level, 4, y + 1, 3, 4, y + 1, 8, pane);
        }
        fill(level, -4, -60, 8, -4, -45, 8, brick);
        fill(level, 4, -60, 8, 4, -45, 8, brick);

        // Doorways, one per floor.
        for (int y : FLOORS) {
            fill(level, -1, y, 2, 1, y + 2, 2, air);
        }

        // Roof with a hole for the cables, a deck in front of the motor, and the motor itself.
        fill(level, -2, -42, -2, 2, -42, 5, floor);
        level.setBlock(new BlockPos(0, -42, 0), air, 3);
        fill(level, -2, -41, 5, 2, -41, 5, pane);
        level.setBlock(MOTOR, Hoistway.MOTOR.get().defaultBlockState().setValue(MotorBlock.FACING, Direction.SOUTH), 3);

        for (int floorNumber = withFirstButton ? 1 : 2; floorNumber <= FLOORS.length; floorNumber++) {
            placeButton(level, floorNumber);
        }

        // Escalators: one run going up, one beside it going down, and a landing at the top.
        fill(level, 5, -61, -1, 8, -61, 3, floor);
        int top = FLOORS[0] + ESCALATOR_STEPS - 1;
        fill(level, ESCALATOR_X + ESCALATOR_STEPS, top, -1, ESCALATOR_X + ESCALATOR_STEPS + 3, top, 3, floor);
        fill(level, ESCALATOR_X + ESCALATOR_STEPS + 3, -60, -1, ESCALATOR_X + ESCALATOR_STEPS + 3, top - 1, -1, brick);
        fill(level, ESCALATOR_X + ESCALATOR_STEPS + 3, -60, 3, ESCALATOR_X + ESCALATOR_STEPS + 3, top - 1, 3, brick);
        BlockState escalator = Hoistway.ESCALATOR.get().defaultBlockState().setValue(EscalatorBlock.FACING, Direction.EAST);
        for (int i = 0; i < ESCALATOR_STEPS; i++) {
            level.setBlock(new BlockPos(ESCALATOR_X + i, FLOORS[0] + i, UP_Z), escalator, 3);
            level.setBlock(new BlockPos(ESCALATOR_X + i, FLOORS[0] + i, DOWN_Z), escalator.setValue(EscalatorBlock.REVERSED, true), 3);
        }
    }

    public static void placeButton(ServerLevel level, int floorNumber) {
        level.setBlock(button(floorNumber), Hoistway.CALL_BUTTON.get().defaultBlockState()
                .setValue(CallButtonBlock.FACING, Direction.SOUTH), 3);
    }

    public static ElevatorCarEntity placeCar(ServerLevel level) {
        ElevatorCarEntity car = Hoistway.CAR.get().create(level);
        car.setSize(3);
        car.setMotorPos(MOTOR);
        car.setPos(CAR_BASE.getX() + 0.5, CAR_BASE.getY(), CAR_BASE.getZ() + 0.5);
        level.addFreshEntity(car);
        return car;
    }

    /** Flips the direction of the escalator run at {@code z}. */
    public static void reverse(ServerLevel level, int z) {
        for (int i = 0; i < ESCALATOR_STEPS; i++) {
            BlockPos pos = new BlockPos(ESCALATOR_X + i, FLOORS[0] + i, z);
            BlockState state = level.getBlockState(pos);
            level.setBlock(pos, state.setValue(EscalatorBlock.REVERSED, !state.getValue(EscalatorBlock.REVERSED)), 3);
        }
    }

    private static void fill(ServerLevel level, int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (BlockPos pos : BlockPos.betweenClosed(x1, y1, z1, x2, y2, z2)) {
            level.setBlock(pos, state, 2);
        }
    }
}
