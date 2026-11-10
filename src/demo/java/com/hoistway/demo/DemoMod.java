package com.hoistway.demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Dev-only mod: creates a flat creative world, runs {@link Director} in it and records the result. */
@Mod(value = "hoistwaydemo", dist = Dist.CLIENT)
public class DemoMod {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final String WORLD = "hoistway-demo";
    private static boolean worldRequested;
    private static boolean directorStarted;

    public DemoMod(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(DemoMod::onScreenInit);
        NeoForge.EVENT_BUS.addListener(DemoMod::onLogin);
        NeoForge.EVENT_BUS.addListener(DemoMod::onTickPre);
        NeoForge.EVENT_BUS.addListener(DemoMod::onTickPost);
        NeoForge.EVENT_BUS.addListener(DemoMod::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(DemoMod::onRenderGui);
        NeoForge.EVENT_BUS.addListener(DemoMod::onRenderScreen);
        NeoForge.EVENT_BUS.addListener(DemoMod::onFrame);
        NeoForge.EVENT_BUS.addListener(DemoMod::onSound);
        // A clean picture: no hand or name tag in the free camera, no crosshair at all.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderHandEvent event) -> {
            if (Minecraft.getInstance().getCameraEntity() != Minecraft.getInstance().player) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderNameTagEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.world.entity.player.Player) {
                event.setCanRender(net.neoforged.neoforge.common.util.TriState.FALSE);
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre event) -> {
            if (event.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR)) {
                event.setCanceled(true);
            }
        });
    }

    // ---- World ----

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen) || worldRequested) {
            return;
        }
        worldRequested = true;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.tell(() -> {
            deleteWorld(minecraft.getLevelSource().getLevelPath(WORLD));
            GameRules rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            rules.getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, null);
            LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                    rules, WorldDataConfiguration.DEFAULT);
            minecraft.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(1234L, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                    event.getScreen());
        });
    }

    private static void deleteWorld(Path folder) {
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not delete old demo world", e);
        }
    }

    private static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!directorStarted) {
            directorStarted = true;
            Path output = Minecraft.getInstance().gameDirectory.toPath().resolve("recording");
            Thread thread = new Thread(new Director(output), "Hoistway demo director");
            thread.setDaemon(true);
            thread.start();
        }
    }

    // ---- Ticks and camera ----

    private static void onTickPre(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera.beforeTick();
        if (minecraft.player != null) {
            Director.steerPlayer(minecraft.player);
            minecraft.getToasts().clear();
        }
    }

    private static void onTickPost(ClientTickEvent.Post event) {
        Director.ticks++;
        Camera.afterTick(Director.ticks);
    }

    private static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Camera.Pose pose = Camera.frame(Director.ticks - 1, event.getPartialTick());
        if (pose != null) {
            event.setYaw(pose.yaw());
            event.setPitch(pose.pitch());
        }
    }

    // ---- Captions ----

    private static void onRenderGui(RenderGuiEvent.Post event) {
        if (Minecraft.getInstance().screen == null) {
            drawCaptions(event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
        }
    }

    private static void onRenderScreen(ScreenEvent.Render.Post event) {
        drawCaptions(event.getGuiGraphics(), event.getPartialTick());
    }

    private static void drawCaptions(GuiGraphics graphics, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float age = Director.ticks - Director.captionTick + partialTick;
        int alpha = (int) (Math.min(1.0F, age / 8.0F) * 255);
        if (alpha < 8) {
            return;
        }

        String title = Director.title;
        if (title != null) {
            graphics.fill(0, 0, width, height, (alpha / 3) << 24);
            graphics.pose().pushPose();
            graphics.pose().translate(width / 2.0F, height / 2.0F - 30, 0);
            graphics.pose().scale(4.0F, 4.0F, 1.0F);
            graphics.drawCenteredString(font, title, 0, 0, (alpha << 24) | 0xFFFFFF);
            graphics.pose().popPose();
            String subtitle = Director.subtitle;
            if (subtitle != null) {
                graphics.drawCenteredString(font, subtitle, width / 2, height / 2 + 14, (alpha << 24) | 0xE0E0E0);
            }
        }

        String caption = Director.caption;
        if (caption != null) {
            List<FormattedCharSequence> lines = font.split(Component.literal(caption), Math.min(width - 40, 330));
            int lineHeight = font.lineHeight + 2;
            int boxHeight = lines.size() * lineHeight + 8;
            int boxWidth = 0;
            for (FormattedCharSequence line : lines) {
                boxWidth = Math.max(boxWidth, font.width(line));
            }
            boxWidth += 16;
            int top = 12;
            int left = (width - boxWidth) / 2;
            graphics.fill(left, top, left + boxWidth, top + boxHeight, ((alpha * 160 / 255) << 24));
            for (int i = 0; i < lines.size(); i++) {
                graphics.drawCenteredString(font, lines.get(i), width / 2, top + 5 + i * lineHeight, (alpha << 24) | 0xFFFFFF);
            }
        }
    }

    // ---- Recording ----

    private static void onFrame(RenderFrameEvent.Post event) {
        Recorder recorder = Director.recorder;
        if (recorder != null) {
            recorder.capture();
        }
    }

    private static void onSound(PlaySoundEvent event) {
        Recorder recorder = Director.recorder;
        if (recorder != null && event.getSound() != null) {
            recorder.cue(event.getSound().getLocation().toString());
        }
    }
}
