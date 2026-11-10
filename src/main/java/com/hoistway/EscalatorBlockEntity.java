package com.hoistway;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds no data; it exists so the moving steps can be drawn every frame. */
public class EscalatorBlockEntity extends BlockEntity {
    public EscalatorBlockEntity(BlockPos pos, BlockState state) {
        super(Hoistway.ESCALATOR_BE.get(), pos, state);
    }
}
