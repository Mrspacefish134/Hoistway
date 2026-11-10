package com.hoistway;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Five slots laid out like a hopper. They take cable and upgrades only. */
public class MotorMenu extends AbstractContainerMenu {
    private final Container motor;

    public MotorMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(MotorBlockEntity.SLOTS));
    }

    public MotorMenu(int id, Inventory inventory, Container motor) {
        super(Hoistway.MOTOR_MENU.get(), id);
        this.motor = motor;
        checkContainerSize(motor, MotorBlockEntity.SLOTS);
        motor.startOpen(inventory.player);

        for (int slot = 0; slot < MotorBlockEntity.SLOTS; slot++) {
            this.addSlot(new Slot(motor, slot, 44 + slot * 18, 20) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return MotorBlockEntity.accepts(stack);
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, row * 18 + 51));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(inventory, column, 8 + column * 18, 109));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return this.motor.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            moved = stack.copy();
            if (index < this.motor.getContainerSize()) {
                if (!this.moveItemStackTo(stack, this.motor.getContainerSize(), this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, 0, this.motor.getContainerSize(), false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return moved;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.motor.stopOpen(player);
    }
}
