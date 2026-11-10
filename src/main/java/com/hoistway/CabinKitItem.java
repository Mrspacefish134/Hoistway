package com.hoistway;

import net.minecraft.world.item.Item;

/** Refits an elevator car's cabin. Used on the car itself; see {@link ElevatorCarEntity#interact}. */
public class CabinKitItem extends Item {
    private final CabinStyle style;

    public CabinKitItem(Properties properties, CabinStyle style) {
        super(properties);
        this.style = style;
    }

    public CabinStyle style() {
        return this.style;
    }
}
