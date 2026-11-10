package com.hoistway.demo;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.hoistway.ElevatorCarEntity;
import com.hoistway.Hoistway;
import com.hoistway.MotorBlockEntity;
import com.hoistway.client.CarPanelScreen;
import com.hoistway.client.ClientHooks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The walkthrough, written as a plain sequence of steps on its own thread. Game state is only
 * touched through {@link #client} and {@link #server}, which run on the game's own threads.
 */
public final class Director implements Runnable {
    static volatile long ticks;
    @Nullable
    static volatile String caption;
    @Nullable
    static volatile String title;
    @Nullable
    static volatile String subtitle;
    static volatile long captionTick;
    @Nullable
    static volatile Vec3 walkTarget;
    static volatile double walkSpeed = 0.16;
    @Nullable
    static volatile Recorder recorder;

    private final Path output;
    @Nullable
    private Villager villager;

    public Director(Path output) {
        this.output = output;
    }

    // ---- Plumbing ----

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static void client(Runnable action) {
        mc().submit(action).join();
    }

    private static <T> T clientGet(Callable<T> query) {
        return mc().submit(() -> {
            try {
                return query.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).join();
    }

    private static MinecraftServer serverInstance() {
        return mc().getSingleplayerServer();
    }

    private static void server(Consumer<ServerLevel> action) {
        MinecraftServer server = serverInstance();
        server.submit(() -> action.accept(server.overworld())).join();
    }

    private static ServerPlayer serverPlayer(ServerLevel level) {
        return level.getServer().getPlayerList().getPlayers().get(0);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static void waitTicks(long count) {
        long end = ticks + count;
        while (ticks < end) {
            sleep(5);
        }
    }

    private static void waitUntil(BooleanSupplier condition, long timeoutTicks) {
        long end = ticks + timeoutTicks;
        while (ticks < end && !clientGet(condition::getAsBoolean)) {
            sleep(15);
        }
    }

    private static void shot(Camera.Shot shot) {
        Camera.play(shot, ticks);
    }

    private static void say(@Nullable String text) {
        caption = text;
        captionTick = ticks;
    }

    private static void titleCard(@Nullable String big, @Nullable String small) {
        title = big;
        subtitle = small;
        captionTick = ticks;
    }

    private static void select(int slot) {
        client(() -> mc().player.getInventory().selected = slot);
    }

    private static void swing() {
        client(() -> mc().player.swing(InteractionHand.MAIN_HAND));
    }

    private static void walk(Vec3 target, double speed) {
        walkSpeed = speed;
        walkTarget = target;
    }

    private static void walkAndWait(Vec3 target, double speed, long timeout) {
        walk(target, speed);
        long end = ticks + timeout;
        while (walkTarget != null && ticks < end) {
            sleep(10);
        }
        walkTarget = null;
    }

    @Nullable
    private static ElevatorCarEntity car() {
        if (mc().level == null) {
            return null;
        }
        List<ElevatorCarEntity> cars = mc().level.getEntitiesOfClass(ElevatorCarEntity.class,
                new net.minecraft.world.phys.AABB(-3, -64, -3, 4, -30, 4));
        return cars.isEmpty() ? null : cars.get(0);
    }

    private static boolean parkedAt(int floor) {
        ElevatorCarEntity car = car();
        return car != null && Math.abs(car.getY() - Stage.FLOORS[floor - 1]) < 0.02 && car.getY() == car.yo;
    }

    private static Vec3 carCenter() {
        ElevatorCarEntity car = car();
        return car == null ? new Vec3(0.5, -59, 0.5) : new Vec3(car.getX(), car.getY() + 1.3, car.getZ());
    }

    private static void pressButton(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        state.useWithoutItem(level, serverPlayer(level),
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
    }

    private static void teleport(double x, double y, double z, float yaw, float pitch) {
        server(level -> serverPlayer(level).teleportTo(level, x, y, z, yaw, pitch));
        waitTicks(3);
    }

    private static MotorBlockEntity motor(ServerLevel level) {
        return (MotorBlockEntity) level.getBlockEntity(Stage.MOTOR);
    }

    // ---- The walkthrough ----

    @Override
    public void run() {
        try {
            this.perform();
        } catch (Throwable t) {
            DemoMod.LOGGER.error("Demo script failed", t);
        } finally {
            Recorder active = recorder;
            recorder = null;
            if (active != null) {
                DemoMod.LOGGER.info("Demo recording finished: {}", active.finish());
            }
            client(() -> mc().stop());
        }
    }

    private void perform() {
        waitUntil(() -> mc().player != null && mc().level != null && serverInstance() != null, 2400);
        waitTicks(20);

        server(level -> {
            level.setDayTime(6000);
            Stage.build(level, false);
            ServerPlayer player = serverPlayer(level);
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(Hoistway.MOTOR_ITEM.get()));
            player.getInventory().setItem(1, new ItemStack(Hoistway.LARGE_ELEVATOR_CAR.get()));
            player.getInventory().setItem(2, new ItemStack(Hoistway.CABLE.get(), 64));
            player.getInventory().setItem(3, new ItemStack(Hoistway.CALL_BUTTON_ITEM.get(), 16));
            player.getInventory().setItem(4, new ItemStack(Hoistway.STOP_UPGRADE.get()));
            player.getInventory().setItem(5, new ItemStack(Hoistway.SPEED_UPGRADE.get()));
            player.getInventory().setItem(6, new ItemStack(Hoistway.ESCALATOR_ITEM.get(), 16));
            player.getInventory().setItem(7, new ItemStack(Hoistway.CREATIVE_UPGRADE.get()));
            player.teleportTo(level, 0.5, -60, 6.5, 180, 20);

            Villager guest = EntityType.VILLAGER.create(level);
            guest.setNoAi(true);
            guest.setSilent(true);
            guest.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.LIBRARIAN, 2));
            guest.moveTo(0.5, Stage.FLOORS[1], 5.5, 180, 0);
            guest.setYHeadRot(180);
            level.addFreshEntity(guest);
            this.villager = guest;
        });
        client(() -> {
            mc().gui.getChat().clearMessages(false);
            mc().getToasts().clear();
            mc().options.pauseOnLostFocus = false;
        });
        select(1);
        shot(Camera.firstPerson(180, 20, 180, 20, 1));
        waitTicks(80);

        recorder = clientGet(() -> Recorder.start(this.output, Recorder.findFfmpeg().getPath()));

        // 1. Title
        Vec3 tower = new Vec3(1.5, -51, 3);
        shot(Camera.orbit(tower, 19, -46, 25, 70, 150));
        titleCard("Hoistway", "Working elevators and escalators for NeoForge 1.21.1");
        waitTicks(150);
        titleCard(null, null);

        // 2. Place the car
        shot(Camera.firstPerson(180, 22, 180, 30, 80));
        say("Put an Elevator Car at the bottom of the shaft");
        waitTicks(40);
        swing();
        server(level -> {
            Stage.placeCar(level);
            level.playSound(null, Stage.CAR_BASE, net.minecraft.sounds.SoundEvents.ANVIL_PLACE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, 0.8F);
        });
        waitTicks(60);

        // 3. The motor and its cables
        shot(Camera.dolly(new Vec3(9.5, -57.0, -2.5), new Vec3(9.5, -42.0, -2.5),
                new Vec3(0.5, -59.0, 0.5), new Vec3(0.5, -42.0, 0.5), 130));
        say("The Elevator Motor at the top lowers and lifts the car on two cables");
        waitTicks(150);

        // 4. Load the motor
        teleport(0.5, -41, 3.6, 180, 38);
        select(2);
        shot(Camera.firstPerson(180, 38, 180, 38, 1));
        say("Fill the motor with Hoist Cable");
        waitTicks(30);
        swing();
        server(level -> serverPlayer(level).openMenu(motor(level)));
        waitTicks(25);
        say("One cable for every block the car travels. This shaft is 16 blocks deep, so: 16 cable");
        for (int n = 1; n <= 16; n++) {
            int count = n;
            server(level -> motor(level).setItem(0, new ItemStack(Hoistway.CABLE.get(), count)));
            waitTicks(4);
        }
        waitTicks(40);
        say("A motor serves 3 floors. Each Stop Upgrade adds 2 more");
        server(level -> motor(level).setItem(1, new ItemStack(Hoistway.STOP_UPGRADE.get())));
        waitTicks(55);
        say("Speed Upgrades make the car faster");
        server(level -> motor(level).setItem(2, new ItemStack(Hoistway.SPEED_UPGRADE.get())));
        waitTicks(55);
        server(level -> serverPlayer(level).closeContainer());
        // Send the car upstairs out of sight, so it can be called back down.
        server(level -> motor(level).request(Stage.FLOORS[2]));

        // 5. Call buttons
        teleport(1.6, -60, 6.8, 160, 18);
        select(3);
        Vec3 eye = new Vec3(1.6, -58.38, 6.8);
        Vec3 buttonSpot = new Vec3(-1.5, -58.5, 3.05);
        Vec3 doorway = new Vec3(0.2, -58.6, 2.0);
        shot(Camera.firstPerson(Camera.yawTo(eye, buttonSpot), Camera.pitchTo(eye, buttonSpot),
                Camera.yawTo(eye, buttonSpot), Camera.pitchTo(eye, buttonSpot), 1));
        say("Put a Call Button beside the doorway on every floor");
        waitTicks(35);
        swing();
        server(level -> {
            Stage.placeButton(level, 1);
            level.playSound(null, Stage.button(1), net.minecraft.sounds.SoundEvents.METAL_PLACE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        });
        waitTicks(35);
        select(8);
        waitUntil(() -> parkedAt(3), 200);
        say("Press it, and the car comes to your floor");
        waitTicks(15);
        swing();
        server(level -> pressButton(level, Stage.button(1)));
        shot(Camera.firstPerson(Camera.yawTo(eye, buttonSpot), Camera.pitchTo(eye, buttonSpot),
                Camera.yawTo(eye, doorway), Camera.pitchTo(eye, doorway), 40));
        waitUntil(() -> parkedAt(1), 300);
        waitTicks(15);

        // 6. Ride with the floor panel
        say("Step in and right-click with an empty hand to open the floor panel");
        shot(Camera.firstPerson(Camera.yawTo(eye, doorway), Camera.pitchTo(eye, doorway), 180, 5, 25));
        walkAndWait(new Vec3(0.5, 0, 0.6), 0.16, 100);
        shot(Camera.firstPerson(180, 5, 360, 8, 35));
        waitTicks(40);
        client(ClientHooks::requestPanel);
        waitUntil(() -> mc().screen instanceof CarPanelScreen, 60);
        client(() -> GLFW.glfwSetCursorPos(mc().getWindow().getWindow(), 2, 2));
        say("Pick a floor");
        waitTicks(30);
        focusFloor(4);
        waitTicks(18);
        client(() -> {
            if (mc().screen instanceof CarPanelScreen panel) {
                panel.press(4);
            }
        });
        say("The car announces each floor it passes");
        waitUntil(() -> parkedAt(4), 400);
        waitTicks(35);

        // 7. Collective control: someone else calls the car on the way down
        client(ClientHooks::requestPanel);
        waitUntil(() -> mc().screen instanceof CarPanelScreen, 60);
        client(() -> GLFW.glfwSetCursorPos(mc().getWindow().getWindow(), 2, 2));
        say("Going down to floor 1...");
        waitTicks(20);
        focusFloor(1);
        waitTicks(15);
        client(() -> {
            if (mc().screen instanceof CarPanelScreen panel) {
                panel.press(1);
            }
        });
        waitTicks(5);
        shot(Camera.track(new Vec3(9.0, -50.5, -2.5), () -> carCenter().add(0, 0, 1.6)));
        say("...but someone on floor 2 presses the Call Button");
        waitUntil(() -> car() != null && car().getY() < Stage.FLOORS[2] - 0.6, 200);
        server(level -> pressButton(level, Stage.button(2)));
        waitUntil(() -> parkedAt(2), 200);
        say("So the car stops to pick them up on the way");
        this.walkVillagerIn();
        waitTicks(10);
        say("It keeps heading the same way, stopping wherever it was asked to");
        waitUntil(() -> parkedAt(1), 300);
        waitTicks(30);

        // 8. Escalators
        teleport(5.5, -60, 0.5, -90, 10);
        shot(Camera.dolly(new Vec3(7.0, -57.0, 1.0), new Vec3(8.0, -56.6, 1.0),
                new Vec3(13.0, -57.0, 1.0), new Vec3(13.5, -56.5, 1.0), 70));
        say("Escalators: place them in a diagonal run, like stairs");
        waitTicks(70);
        shot(Camera.fixed(new Vec3(11.5, -57.2, 9.8), new Vec3(11.5, -58.2, 1.0)));
        say("The steps really move, and carry you up");
        walkAndWait(new Vec3(9.7, 0, 0.5), 0.14, 60);
        waitUntil(() -> mc().player.getX() > Stage.ESCALATOR_X + Stage.ESCALATOR_STEPS + 0.6, 260);
        walkAndWait(new Vec3(15.2, 0, 1.0), 0.14, 40);
        walkAndWait(new Vec3(14.7, 0, 2.5), 0.14, 40);
        say("Run one beside it the other way to come back down");
        walkAndWait(new Vec3(13.6, 0, 2.5), 0.14, 30);
        waitUntil(() -> mc().player.getX() < Stage.ESCALATOR_X - 0.2, 260);
        walkAndWait(new Vec3(6.5, 0, 2.5), 0.14, 40);
        shot(Camera.fixed(new Vec3(8.0, -55.5, 1.0), new Vec3(11.0, -58.3, 1.0)));
        say("Right-click a run with an empty hand to reverse it");
        waitTicks(40);
        server(level -> {
            Stage.reverse(level, Stage.UP_Z);
            level.playSound(null, new BlockPos(Stage.ESCALATOR_X, Stage.FLOORS[0], Stage.UP_Z),
                    net.minecraft.sounds.SoundEvents.LEVER_CLICK, net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 0.5F);
        });
        waitTicks(90);

        // 9. Outro
        say(null);
        shot(Camera.orbit(new Vec3(5.5, -52, 2.5), 24, -45, 60, 100, 160));
        titleCard("Hoistway", "Free and open source: github.com/Mrspacefish134/Hoistway");
        waitTicks(160);
    }

    /** Highlights a floor button on the open panel, the way it looks under the mouse. */
    private static void focusFloor(int number) {
        client(() -> {
            if (mc().screen instanceof CarPanelScreen panel) {
                for (var child : panel.children()) {
                    if (child instanceof Button button && button.getMessage().getString().equals(Integer.toString(number))) {
                        panel.setFocused(button);
                        ((AbstractWidget) button).setFocused(true);
                    }
                }
            }
        });
    }

    /** The guest walks from the landing into the car while its doors are open. */
    private void walkVillagerIn() {
        Villager guest = this.villager;
        if (guest == null) {
            return;
        }
        Vec3 from = new Vec3(0.5, Stage.FLOORS[1], 5.5);
        Vec3 to = new Vec3(-0.45, Stage.FLOORS[1] + ElevatorCarEntity.FLOOR_HEIGHT, 0.2);
        int steps = 26;
        for (int i = 1; i <= steps; i++) {
            double f = i / (double) steps;
            double x = from.x + (to.x - from.x) * f;
            double z = from.z + (to.z - from.z) * f;
            double y = z < 2.0 ? to.y : from.y;
            float yaw = Camera.yawTo(from, to);
            server(level -> {
                guest.moveTo(x, y, z, yaw, 0);
                guest.setYHeadRot(yaw);
                guest.yBodyRot = yaw;
            });
            waitTicks(1);
        }
        server(level -> {
            guest.setYRot(0);
            guest.setYHeadRot(0);
            guest.yBodyRot = 0;
        });
    }

    // ---- Called from the client tick ----

    static void steerPlayer(LocalPlayer player) {
        Vec3 target = walkTarget;
        if (target == null) {
            return;
        }
        double dx = target.x - player.getX();
        double dz = target.z - player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.06) {
            player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
            walkTarget = null;
            return;
        }
        double step = Math.min(walkSpeed, distance);
        player.setDeltaMovement(dx / distance * step, player.getDeltaMovement().y, dz / distance * step);
    }
}
