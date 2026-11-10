package com.hoistway.client;

import com.hoistway.ElevatorCarEntity;
import com.hoistway.Hoistway;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** The music in the car. Follows the car, fades in while you ride and out when you leave. */
public class ElevatorMusic extends AbstractTickableSoundInstance {
    private static final float MAX_VOLUME = 0.5F;
    private static final float FADE = 0.025F;

    private final ElevatorCarEntity car;
    private boolean leaving;

    public ElevatorMusic(ElevatorCarEntity car) {
        super(Hoistway.ELEVATOR_MUSIC.get(), SoundSource.RECORDS, RandomSource.create());
        this.car = car;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01F;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.follow();
    }

    public ElevatorCarEntity car() {
        return this.car;
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
