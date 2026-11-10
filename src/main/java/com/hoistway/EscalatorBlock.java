package com.hoistway;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One block of a moving staircase. {@code facing} is the uphill direction; place them in a
 * diagonal run like stairs. Right-click with an empty hand to reverse the whole run.
 */
public class EscalatorBlock extends BaseEntityBlock {
    public static final MapCodec<EscalatorBlock> CODEC = simpleCodec(EscalatorBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Steps run downhill instead of uphill. */
    public static final BooleanProperty REVERSED = BooleanProperty.create("reversed");
    /** No escalator continues above this block. */
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    /** No escalator continues below this block. */
    public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom");

    /** Sideways push per tick for anything standing on the steps. */
    private static final double PUSH = 0.045;

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            SHAPES.put(facing, Shapes.or(
                    rotated(facing, 1, 0, 8, 15, 8, 16),   // low step
                    rotated(facing, 1, 0, 0, 15, 16, 8),   // high step
                    rotated(facing, 0, 0, 0, 1, 24, 16),   // balustrades
                    rotated(facing, 15, 0, 0, 16, 24, 16)));
        }
    }

    /** A box given for a north-facing escalator, turned to {@code facing}. */
    private static VoxelShape rotated(Direction facing, double x1, double y1, double z1, double x2, double y2, double z2) {
        return switch (facing) {
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            default -> Block.box(x1, y1, z1, x2, y2, z2);
        };
    }

    public EscalatorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(REVERSED, false)
                .setValue(TOP, true)
                .setValue(BOTTOM, true));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, REVERSED, TOP, BOTTOM);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EscalatorBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    // ---- Runs ----

    private static BlockPos uphill(BlockPos pos, Direction facing) {
        return pos.relative(facing).above();
    }

    private static BlockPos downhill(BlockPos pos, Direction facing) {
        return pos.relative(facing.getOpposite()).below();
    }

    private boolean continuesAt(LevelReader level, BlockPos pos, Direction facing) {
        BlockState state = level.getBlockState(pos);
        return state.is(this) && state.getValue(FACING) == facing;
    }

    private BlockState withEnds(LevelReader level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        return state
                .setValue(TOP, !this.continuesAt(level, uphill(pos, facing), facing))
                .setValue(BOTTOM, !this.continuesAt(level, downhill(pos, facing), facing));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction facing = context.getHorizontalDirection();
        BlockState state = this.withEnds(level, pos, this.defaultBlockState().setValue(FACING, facing));

        // Join the direction of the run this block extends.
        for (BlockPos neighbor : new BlockPos[] {uphill(pos, facing), downhill(pos, facing)}) {
            if (this.continuesAt(level, neighbor, facing)) {
                return state.setValue(REVERSED, level.getBlockState(neighbor).getValue(REVERSED));
            }
        }
        return state;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            // Blocks placed by commands or structures skip getStateForPlacement, so fix our own ends too.
            BlockState fixed = this.withEnds(level, pos, state);
            if (fixed != state) {
                level.setBlock(pos, fixed, 2);
            }
            this.refreshNeighbors(level, pos, state.getValue(FACING));
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!newState.is(this)) {
            this.refreshNeighbors(level, pos, state.getValue(FACING));
        }
    }

    /** Diagonal neighbours do not get shape updates, so the ends are kept current by hand. */
    private void refreshNeighbors(Level level, BlockPos pos, Direction facing) {
        for (BlockPos neighbor : new BlockPos[] {uphill(pos, facing), downhill(pos, facing)}) {
            BlockState state = level.getBlockState(neighbor);
            if (state.is(this) && state.getValue(FACING) == facing) {
                BlockState updated = this.withEnds(level, neighbor, state);
                if (updated != state) {
                    level.setBlock(neighbor, updated, 3);
                }
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            Direction facing = state.getValue(FACING);
            boolean reversed = !state.getValue(REVERSED);
            for (BlockPos cursor = pos; this.continuesAt(level, cursor, facing); cursor = uphill(cursor, facing)) {
                level.setBlock(cursor, level.getBlockState(cursor).setValue(REVERSED, reversed), 3);
            }
            for (BlockPos cursor = downhill(pos, facing); this.continuesAt(level, cursor, facing); cursor = downhill(cursor, facing)) {
                level.setBlock(cursor, level.getBlockState(cursor).setValue(REVERSED, reversed), 3);
            }
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4F, reversed ? 0.5F : 0.6F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        Direction travel = state.getValue(FACING);
        if (state.getValue(REVERSED)) {
            travel = travel.getOpposite();
        }
        Vec3 motion = entity.getDeltaMovement();
        entity.setDeltaMovement(motion.x + travel.getStepX() * PUSH, motion.y, motion.z + travel.getStepZ() * PUSH);
        super.stepOn(level, pos, state, entity);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
