package com.hoistway;

import com.mojang.datafixers.util.Pair;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A hopper-style row of five slots for cable and upgrades, and below it three slots for music
 * discs that replace the elevator music.
 */
public class MotorMenu extends AbstractContainerMenu {
    /** Where the empty disc slots show a disc outline, like the smithing table's empty slots. */
    public static final ResourceLocation EMPTY_DISC_SLOT = Hoistway.id("item/empty_slot_music_disc");
    /** How far the player inventory sits below where the hopper screen has it. */
    public static final int DISC_ROW_HEIGHT = 22;

    private final Container motor;

    public MotorMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(MotorBlockEntity.SLOTS));
    }

    public MotorMenu(int id, Inventory inventory, Container motor) {
        super(Hoistway.MOTOR_MENU.get(), id);
        this.motor = motor;
        checkContainerSize(motor, MotorBlockEntity.SLOTS);
        motor.startOpen(inventory.player);

        for (int slot = 0; slot < MotorBlockEntity.UPGRADE_SLOTS; slot++) {
            this.addSlot(new Slot(motor, slot, 44 + slot * 18, 20) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return MotorBlockEntity.accepts(stack);
                }
            });
        }
        for (int disc = 0; disc < MotorBlockEntity.DISC_SLOTS; disc++) {
            this.addSlot(new Slot(motor, MotorBlockEntity.UPGRADE_SLOTS + disc, 62 + disc * 18, 42) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return MotorBlockEntity.isDisc(stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                    return Pair.of(InventoryMenu.BLOCK_ATLAS, EMPTY_DISC_SLOT);
                }
            });
        }
        int inventoryTop = 51 + DISC_ROW_HEIGHT;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, row * 18 + inventoryTop));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(inventory, column, 8 + column * 18, inventoryTop + 58));
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
