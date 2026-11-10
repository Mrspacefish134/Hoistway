package com.hoistway.client;

import java.util.List;

import com.hoistway.PanelPayloads;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The buttons inside the car, one per stop, highest floor at the top. Floors already asked for
 * are shown lit; the floor the car is parked at cannot be pressed.
 */
public class CarPanelScreen extends Screen {
    private static final int COLUMNS = 4;
    private static final int ROWS = 5;
    private static final int PER_PAGE = COLUMNS * ROWS;
    private static final int BUTTON = 24;
    private static final int GAP = 4;

    private final PanelPayloads.Contents contents;
    private int page;

    public CarPanelScreen(PanelPayloads.Contents contents) {
        super(Component.translatable("hoistway.panel.title"));
        this.contents = contents;
        int index = contents.stops().indexOf(contents.current());
        if (index >= 0) {
            this.page = (contents.stops().size() - 1 - index) / PER_PAGE;
        }
    }

    @Override
    protected void init() {
        List<Integer> stops = this.contents.stops();
        int count = stops.size();
        int pages = Math.max(1, (count + PER_PAGE - 1) / PER_PAGE);
        this.page = Math.min(this.page, pages - 1);

        int onPage = Math.min(PER_PAGE, count - this.page * PER_PAGE);
        int columns = Math.min(COLUMNS, Math.max(1, onPage));
        int rows = (onPage + columns - 1) / columns;
        int gridWidth = columns * BUTTON + (columns - 1) * GAP;
        int gridHeight = rows * BUTTON + (rows - 1) * GAP;
        int left = (this.width - gridWidth) / 2;
        int top = (this.height - gridHeight) / 2;

        for (int slot = 0; slot < onPage; slot++) {
            // Highest floor first, reading left to right like a lift panel.
            int index = count - 1 - (this.page * PER_PAGE + slot);
            int floorY = stops.get(index);
            Component label = Component.literal(Integer.toString(index + 1));
            if (this.contents.requested().contains(floorY)) {
                label = label.copy().withStyle(ChatFormatting.YELLOW);
            }
            Button button = Button.builder(label, pressed -> this.select(floorY))
                    .bounds(left + (slot % columns) * (BUTTON + GAP), top + (slot / columns) * (BUTTON + GAP), BUTTON, BUTTON)
                    .build();
            button.active = floorY != this.contents.current();
            this.addRenderableWidget(button);
        }

        if (pages > 1) {
            int y = top + gridHeight + GAP * 2;
            Button previous = Button.builder(Component.literal("<"), pressed -> this.turn(-1))
                    .bounds(left, y, BUTTON, 20).build();
            Button next = Button.builder(Component.literal(">"), pressed -> this.turn(1))
                    .bounds(left + gridWidth - BUTTON, y, BUTTON, 20).build();
            previous.active = this.page > 0;
            next.active = this.page < pages - 1;
            this.addRenderableWidget(previous);
            this.addRenderableWidget(next);
        }
    }

    private void turn(int by) {
        this.page += by;
        this.rebuildWidgets();
    }

    private void select(int floorY) {
        PacketDistributor.sendToServer(new PanelPayloads.Select(this.contents.carId(), floorY));
        this.onClose();
    }

    /** Presses the button for the given floor number (1 is the lowest stop), as if clicked. */
    public void press(int floorNumber) {
        if (floorNumber >= 1 && floorNumber <= this.contents.stops().size()) {
            this.select(this.contents.stops().get(floorNumber - 1));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int count = this.contents.stops().size();
        int onPage = Math.min(PER_PAGE, count - this.page * PER_PAGE);
        int rows = (onPage + COLUMNS - 1) / Math.min(COLUMNS, Math.max(1, onPage));
        int gridHeight = rows * BUTTON + (rows - 1) * GAP;
        graphics.drawCenteredString(this.font, this.title, this.width / 2, (this.height - gridHeight) / 2 - 16, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
