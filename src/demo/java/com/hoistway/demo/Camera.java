package com.hoistway.demo;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * Scripted camera. A shot gives a pose for any (fractional) tick since it began. Free shots move
 * an invisible client-side entity that the game renders from; first-person shots render from the
 * player and only set the view angles.
 */
public final class Camera {
    /** {@code pos == null} means first person. */
    public record Pose(@Nullable Vec3 pos, float yaw, float pitch) {
    }

    public interface Shot {
        Pose at(double t);
    }

    private static volatile Shot shot;
    private static volatile long shotStart;
    private static volatile boolean cut;
    private static Display.BlockDisplay rig;

    private Camera() {
    }

    public static void play(Shot next, long now) {
        shot = next;
        shotStart = now;
        cut = true;
    }

    // ---- Shots ----

    public static float yawTo(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    public static float pitchTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return (float) -Math.toDegrees(Math.atan2(to.y - from.y, Math.sqrt(dx * dx + dz * dz)));
    }

    public static double ease(double t, double duration) {
        double x = Math.max(0.0, Math.min(1.0, t / duration));
        return x * x * (3.0 - 2.0 * x);
    }

    private static Vec3 lerp(double f, Vec3 a, Vec3 b) {
        return a.add(b.subtract(a).scale(f));
    }

    public static Shot fixed(Vec3 pos, Vec3 look) {
        return t -> new Pose(pos, yawTo(pos, look), pitchTo(pos, look));
    }

    public static Shot dolly(Vec3 from, Vec3 to, Vec3 lookFrom, Vec3 lookTo, double duration) {
        return t -> {
            double f = ease(t, duration);
            Vec3 pos = lerp(f, from, to);
            Vec3 look = lerp(f, lookFrom, lookTo);
            return new Pose(pos, yawTo(pos, look), pitchTo(pos, look));
        };
    }

    public static Shot orbit(Vec3 center, double radius, double height, double fromDeg, double toDeg, double duration) {
        return t -> {
            double a = Math.toRadians(fromDeg + (toDeg - fromDeg) * Math.min(1.0, t / duration));
            Vec3 pos = new Vec3(center.x + Math.cos(a) * radius, height, center.z + Math.sin(a) * radius);
            return new Pose(pos, yawTo(pos, center), pitchTo(pos, center));
        };
    }

    /** A fixed camera that keeps a moving target in frame. */
    public static Shot track(Vec3 pos, Supplier<Vec3> target) {
        return t -> {
            Vec3 look = target.get();
            return new Pose(pos, yawTo(pos, look), pitchTo(pos, look));
        };
    }

    public static Shot firstPerson(float yawFrom, float pitchFrom, float yawTo, float pitchTo, double duration) {
        return t -> {
            double f = ease(t, duration);
            return new Pose(null, (float) (yawFrom + (yawTo - yawFrom) * f), (float) (pitchFrom + (pitchTo - pitchFrom) * f));
        };
    }

    // ---- Driving the game ----

    @Nullable
    private static Pose pose(double ticks) {
        Shot current = shot;
        return current == null ? null : current.at(ticks - shotStart);
    }

    /** Client tick, before the world ticks: keep the rig's previous position for interpolation. */
    static void beforeTick() {
        if (rig != null) {
            rig.xo = rig.getX();
            rig.yo = rig.getY();
            rig.zo = rig.getZ();
        }
    }

    /** Client tick, after the world ticks: place the camera for this tick. */
    static void afterTick(long now) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        Pose pose = pose(now);
        if (pose == null) {
            return;
        }
        if (pose.pos() == null) {
            if (minecraft.getCameraEntity() != minecraft.player) {
                minecraft.setCameraEntity(minecraft.player);
            }
            minecraft.player.setYRot(pose.yaw());
            minecraft.player.setXRot(pose.pitch());
            minecraft.player.yRotO = pose.yaw();
            minecraft.player.xRotO = pose.pitch();
            minecraft.player.setYHeadRot(pose.yaw());
            minecraft.player.yBodyRot = pose.yaw();
        } else {
            if (rig == null || rig.level() != minecraft.level || rig.isRemoved()) {
                rig = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, minecraft.level);
                rig.setId(-424242);
                minecraft.level.addEntity(rig);
                cut = true;
            }
            rig.setPos(pose.pos().x, pose.pos().y, pose.pos().z);
            if (cut) {
                rig.xo = rig.getX();
                rig.yo = rig.getY();
                rig.zo = rig.getZ();
            }
            rig.setYRot(pose.yaw());
            rig.setXRot(pose.pitch());
            if (minecraft.getCameraEntity() != rig) {
                minecraft.setCameraEntity(rig);
            }
        }
        cut = false;
    }

    /** Per frame: smooth view angles straight from the shot. */
    @Nullable
    static Pose frame(long now, double partialTick) {
        return pose(now + partialTick);
    }
}
