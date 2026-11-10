package com.hoistway.client;

import com.hoistway.MotorMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * The motor's slots, drawn with the vanilla hopper screen. A second row of three slots for music
 * discs is spliced in under the hopper row, cut from the same texture.
 */
public class MotorScreen extends AbstractContainerScreen<MotorMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/hopper.png");
    /** First row of the hopper texture below its slot row; plain background down to the inventory. */
    private static final int SPLIT = 37;
    private static final int HOPPER_HEIGHT = 133;

    public MotorScreen(MotorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageHeight = HOPPER_HEIGHT + MotorMenu.DISC_ROW_HEIGHT;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;
        int gap = MotorMenu.DISC_ROW_HEIGHT;
        // The hopper down to the bottom of its slot row.
        graphics.blit(TEXTURE, left, top, 0, 0, this.imageWidth, SPLIT);
        // Plain background for the new row, then three of the hopper's slot frames on it.
        graphics.blit(TEXTURE, left, top + SPLIT, 0, SPLIT, this.imageWidth, 13);
        graphics.blit(TEXTURE, left, top + SPLIT + 13, 0, SPLIT, this.imageWidth, gap - 13);
        graphics.blit(TEXTURE, left + 61, top + 41, 43, 19, 54, 18);
        // The rest of the hopper screen, moved down.
        graphics.blit(TEXTURE, left, top + SPLIT + gap, 0, SPLIT, this.imageWidth, HOPPER_HEIGHT - SPLIT);
    }
}
