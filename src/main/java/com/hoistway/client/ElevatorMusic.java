package com.hoistway.client;

import com.hoistway.ElevatorCarEntity;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The music in the car: the elevator music on a loop, or one of the motor's music discs played
 * through once. Follows the car, fades in while you ride and out when you leave.
 */
public class ElevatorMusic extends AbstractTickableSoundInstance {
    private static final float MAX_VOLUME = 0.5F;
    private static final float FADE = 0.025F;

    private final ElevatorCarEntity car;
    private final ResourceLocation track;
    private boolean leaving;
    private int ticksPlayed;

    public ElevatorMusic(ElevatorCarEntity car, ResourceLocation track, boolean loop) {
        super(SoundEvent.createVariableRangeEvent(track), SoundSource.RECORDS, RandomSource.create());
        this.car = car;
        this.track = track;
        this.looping = loop;
        this.delay = 0;
        this.volume = 0.01F;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.follow();
    }

    public ElevatorCarEntity car() {
        return this.car;
    }

    public ResourceLocation track() {
        return this.track;
    }

    /** Ticks this has been playing; a track that ends almost at once could not be played. */
    public int ticksPlayed() {
        return this.ticksPlayed;
    }

    public void fadeOut() {
        this.leaving = true;
    }

    private void follow() {
        this.x = this.car.getX();
        this.y = this.car.getY() + 1.5;
        this.z = this.car.getZ();
    }

    @Override
    public void tick() {
        if (this.car.isRemoved()) {
            this.stop();
            return;
        }
        this.ticksPlayed++;
        this.follow();
        boolean riding = !this.leaving && ClientHooks.isRiding(this.car);
        this.volume = Math.max(0.0F, Math.min(MAX_VOLUME, this.volume + (riding ? FADE : -FADE)));
        if (this.volume <= 0.0F) {
            this.stop();
        }
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
