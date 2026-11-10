package com.hoistway;

import javax.annotation.Nullable;

import net.minecraft.world.item.Item;

/** How the car looks. The fitted cabins are closed in, with doors that open at each stop. */
public enum CabinStyle {
    /** The open steel cage the car comes as. */
    STANDARD("standard"),
    /** Walnut panelling, orange shag and bronze doors. */
    RETRO("retro"),
    /** Brushed steel, smoked glass and granite. */
    CORPORATE("corporate");

    private static final CabinStyle[] VALUES = values();

    private final String name;

    CabinStyle(String name) {
        this.name = name;
    }

    public String getSerializedName() {
        return this.name;
    }

    public boolean hasDoors() {
        return this != STANDARD;
    }

    /** The kit that fits this cabin, or null for the plain cage. */
    @Nullable
    public Item kit() {
        return switch (this) {
            case RETRO -> Hoistway.RETRO_CABIN_KIT.get();
            case CORPORATE -> Hoistway.CORPORATE_CABIN_KIT.get();
            case STANDARD -> null;
        };
    }

    public static CabinStyle byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : STANDARD;
    }
}
