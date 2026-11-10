package com.hoistway.demo;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;

/**
 * Streams the finished game frame to ffmpeg at a fixed 30 frames per second of wall-clock time:
 * frames are repeated when the game renders slower than that and dropped when it renders faster.
 * Also writes a list of sound cues with their video timestamps, for adding sound afterwards.
 */
public final class Recorder {
    public static final int FPS = 30;
    private static final byte[] END = new byte[0];

    private final int width;
    private final int height;
    private final Process ffmpeg;
    private final Thread writer;
    private final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(90);
    private final ByteBuffer pixels;
    private final PrintWriter cues;
    private final long start = System.nanoTime();
    private long written;
    private byte[] last;
    private volatile IOException failure;

    private Recorder(int width, int height, Process ffmpeg, PrintWriter cues) {
        this.width = width;
        this.height = height;
        this.ffmpeg = ffmpeg;
        this.cues = cues;
        this.pixels = MemoryUtil.memAlloc(width * height * 4);
        OutputStream out = new BufferedOutputStream(ffmpeg.getOutputStream(), 1 << 22);
        this.writer = new Thread(() -> {
            try {
                while (true) {
                    byte[] frame = this.queue.take();
                    if (frame == END) {
                        break;
                    }
                    out.write(frame);
                }
                out.close();
            } catch (IOException e) {
                this.failure = e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Hoistway demo recorder");
        this.writer.setDaemon(true);
        this.writer.start();
    }

    public static Recorder start(Path folder, String ffmpegPath) throws IOException {
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        int width = target.width;
        int height = target.height;
        Files.createDirectories(folder);
        Path video = folder.resolve("raw.mp4");
        ProcessBuilder builder = new ProcessBuilder(List.of(
                ffmpegPath, "-y", "-loglevel", "error",
                "-f", "rawvideo", "-pix_fmt", "rgba", "-s", width + "x" + height, "-r", Integer.toString(FPS), "-i", "-",
                "-vf", "vflip,scale=trunc(iw/2)*2:trunc(ih/2)*2",
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "18", "-pix_fmt", "yuv420p",
                video.toString()));
        builder.redirectError(folder.resolve("ffmpeg.log").toFile());
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process process = builder.start();
        PrintWriter cues = new PrintWriter(Files.newBufferedWriter(folder.resolve("cues.txt")));
        return new Recorder(width, height, process, cues);
    }

    /** Seconds of video recorded so far. */
    public double seconds() {
        return (System.nanoTime() - this.start) / 1.0E9;
    }

    public void cue(String name) {
        this.cues.printf("%.3f %s%n", this.seconds(), name);
        this.cues.flush();
    }

    /** Called once per rendered frame, after the GUI is drawn. */
    public void capture() {
        if (this.failure != null) {
            return;
        }
        long due = (long) (this.seconds() * FPS) + 1;
        if (due <= this.written) {
            return;
        }
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        byte[] frame;
        if (target.width == this.width && target.height == this.height) {
            RenderSystem.bindTexture(target.getColorTextureId());
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, this.pixels);
            frame = new byte[this.width * this.height * 4];
            this.pixels.get(0, frame);
            this.last = frame;
        } else if (this.last != null) {
            frame = this.last;
        } else {
            return;
        }
        try {
            while (this.written < due) {
                this.queue.put(frame);
                this.written++;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Finishes the file. Blocks until ffmpeg has written it. */
    public boolean finish() {
        try {
            this.queue.put(END);
            this.writer.join();
            int exit = this.ffmpeg.waitFor();
            this.cues.close();
            MemoryUtil.memFree(this.pixels);
            return exit == 0 && this.failure == null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static File findFfmpeg() {
        for (String candidate : new String[] {"C:/ffmpeg/bin/ffmpeg.exe", "/usr/bin/ffmpeg", "/usr/local/bin/ffmpeg", "/opt/homebrew/bin/ffmpeg"}) {
            File file = new File(candidate);
            if (file.isFile()) {
                return file;
            }
        }
        return new File("ffmpeg");
    }
}
