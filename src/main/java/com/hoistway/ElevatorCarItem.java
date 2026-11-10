package com.hoistway;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Places an elevator car at the bottom of a shaft that has a motor somewhere above it. */
public class ElevatorCarItem extends Item {
    private final int size;

    public ElevatorCarItem(Properties properties, int size) {
        super(properties);
        this.size = size;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        Direction face = context.getClickedFace();
        BlockPos base = context.getClickedPos().relative(face);
        Player player = context.getPlayer();

        MotorBlockEntity motor = null;
        for (int dy = 3; base.getY() + dy < level.getMaxBuildHeight(); dy++) {
            if (level.getBlockEntity(base.above(dy)) instanceof MotorBlockEntity found) {
                motor = found;
                break;
            }
        }
        if (motor == null) {
            return fail(player, "hoistway.car.no_motor");
        }
        if (MotorBlockEntity.hasCar(level, motor.getBlockPos())) {
            return fail(player, "hoistway.car.occupied");
        }

        ElevatorCarEntity car = Hoistway.CAR.get().create(level);
        if (car == null) {
            return InteractionResult.FAIL;
        }
        car.setSize(this.size);
        car.setMotorPos(motor.getBlockPos());
        car.setPos(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
        if (level.getBlockCollisions(car, car.cabinBox().deflate(0.02, 0.001, 0.02)).iterator().hasNext()) {
            return fail(player, "hoistway.car.no_room");
        }

        level.addFreshEntity(car);
        level.playSound(null, base, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5F, 0.8F);
        if (player != null && base.getY() < motor.getBlockPos().getY() - 3 - motor.getCableLength()) {
            player.displayClientMessage(Component.translatable("hoistway.car.no_cable"), true);
        }
        context.getItemInHand().consume(1, player);
        return InteractionResult.CONSUME;
    }

    private static InteractionResult fail(@Nullable Player player, String key) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(key), true);
        }
        return InteractionResult.FAIL;
    }
}
