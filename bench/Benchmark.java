package brick;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.ProtectionDomain;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class Benchmark {
    private static final String LAUNCH_LOADER = "net/minecraft/launchwrapper/LaunchClassLoader";
    private static final long WARMUP = 30_000_000_000L;
    private static final long DURATION = 120_000_000_000L;
    private static final long[] intervals = new long[120_000];
    private static volatile boolean sampling;
    private static volatile String state = "waiting for Minecraft";
    private static final StringBuilder samples = new StringBuilder();
    private static Path resultDirectory;
    private static Field screen;
    private static Field world;
    private static Field player;
    private static Field[] pose;
    private static Object client;
    private static long worldReady;
    private static long started;
    private static long previous;
    private static long nextPoseCheck;
    private static int frames;
    private static boolean loadRequested;
    private static boolean active;
    private static boolean cameraValid = true;
    private static String renderer = "unavailable";
    private static String resolution = "unavailable";
    private static Thread sampler;
    private static Path temporaryWorld;
    private static Instant runTime;

    private Benchmark() {}

    public static void premain(String argument, Instrumentation instrumentation) throws Exception {
        if (argument == null || argument.isEmpty()) {
            throw new IllegalArgumentException("Pass the absolute result directory to the Brick benchmark agent.");
        }
        resultDirectory = Paths.get(argument);
        Files.createDirectories(resultDirectory);
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String name, Class<?> redefining,
                                    ProtectionDomain domain, byte[] bytes) {
                boolean launchLoader = LAUNCH_LOADER.equals(name);
                if (!launchLoader && !"ave".equals(name) && !"org/lwjgl/opengl/Display".equals(name)) {
                    return null;
                }
                boolean minecraft = "ave".equals(name);
                ClassReader reader = new ClassReader(bytes);
                ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
                reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                    @Override
                    public MethodVisitor visitMethod(int flags, String method, String descriptor,
                                                     String signature, String[] exceptions) {
                        MethodVisitor next = super.visitMethod(flags, method, descriptor, signature, exceptions);
                        boolean isHook = launchLoader ? "<init>".equals(method) && "([Ljava/net/URL;)V".equals(descriptor)
                                : "()V".equals(descriptor) && (minecraft ? "av" : "swapBuffers").equals(method);
                        if (!isHook) {
                            return next;
                        }
                        return new MethodVisitor(Opcodes.ASM9, next) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.RETURN) {
                                    if (launchLoader) {
                                        super.visitVarInsn(Opcodes.ALOAD, 0);
                                        super.visitLdcInsn("brick.");
                                        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, LAUNCH_LOADER,
                                                "addClassLoaderExclusion", "(Ljava/lang/String;)V", false);
                                    } else if (minecraft) {
                                        super.visitVarInsn(Opcodes.ALOAD, 0);
                                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "brick/Benchmark",
                                                "gameLoop", "(Ljava/lang/Object;)V", false);
                                    } else {
                                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "brick/Benchmark",
                                                "presented", "()V", false);
                                    }
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }
                }, 0);
                return writer.toByteArray();
            }
        });
    }

    // Minecraft 1.8.9 obfuscated members, verified against the client and MCP 1.8.9 mappings.
    public static void gameLoop(Object game) {
        try {
            if (!loadRequested) {
                Class<?> type = game.getClass();
                screen = getField(type, "m");
                Object gui = screen.get(game);
                if (gui == null || !"aya".equals(gui.getClass().getName())) {
                    return;
                }
                loadRequested = true;
                client = game;
                world = getField(type, "f");
                player = getField(type, "h");
                openWorld(game, type);
                return;
            }
            if (worldReady != 0 || client == null || world.get(game) == null ||
                    player.get(game) == null || screen.get(game) != null) {
                return;
            }
            Object entity = player.get(game);
            String[] fields = {"s", "t", "u", "y", "z"};
            pose = new Field[fields.length];
            for (int index = 0; index < fields.length; index++) {
                pose[index] = getField(entity.getClass(), fields[index]);
            }
            ClassLoader loader = game.getClass().getClassLoader();
            Class<?> gl = Class.forName("org.lwjgl.opengl.GL11", true, loader);
            renderer = String.valueOf(gl.getMethod("glGetString", int.class).invoke(null, 0x1F01));
            Class<?> display = Class.forName("org.lwjgl.opengl.Display", true, loader);
            resolution = display.getMethod("getWidth").invoke(null) + "x" + display.getMethod("getHeight").invoke(null);
            runTime = Instant.now();
            worldReady = System.nanoTime();
            state = "warmup";
            sampling = true;
            sampler = new Thread(Benchmark::sample, "brick-benchmark-telemetry");
            sampler.setDaemon(true);
            sampler.start();
            System.out.println("[Brick benchmark] World loaded. Keep this window focused and leave input untouched.");
        } catch (Exception error) {
            fail(error);
        }
    }

    private static Field getField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> owner = type; owner != null; owner = owner.getSuperclass()) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Continue to the superclass.
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void openWorld(Object game, Class<?> type) throws Exception {
        File gameDir = (File) getField(type, "v").get(game);
        Path saves = gameDir.toPath().resolve("saves");
        Files.createDirectories(saves);
        temporaryWorld = Files.createTempDirectory(saves, "brick-benchmark-v1-");
        Path generated = temporaryWorld;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> eraseGeneratedWorld(generated), "brick-world-cleanup"));
        try (InputStream level = Benchmark.class.getResourceAsStream("/level.dat")) {
            Files.copy(level, temporaryWorld.resolve("level.dat"));
        }
        Class<?> settings = Class.forName("adp", false, type.getClassLoader());
        Method load = type.getDeclaredMethod("a", String.class, String.class, settings);
        load.setAccessible(true);
        load.invoke(game, temporaryWorld.getFileName().toString(), "Brick Benchmark v1", null);
    }

    private static void eraseGeneratedWorld(Path generated) {
        try (java.util.stream.Stream<Path> paths = Files.walk(generated)) {
            Path[] children = paths.sorted(java.util.Comparator.reverseOrder()).toArray(Path[]::new);
            for (Path path : children) Files.deleteIfExists(path);
        } catch (java.io.IOException error) {
            System.err.println("[Brick benchmark] Could not remove generated world " + generated + ": " + error);
        }
    }

    public static void presented() {
        if (client == null || worldReady == 0) {
            return;
        }
        long now = System.nanoTime();
        if (!active && now - worldReady < WARMUP) {
            return;
        }
        try {
            if (!active) {
                active = true;
                started = previous = now;
                state = "measure";
            } else {
                if (frames == intervals.length) {
                    throw new IllegalStateException("Frame buffer capacity exceeded");
                }
                intervals[frames++] = now - previous;
                previous = now;
            }
            if (now >= nextPoseCheck) {
                cameraValid &= poseIsValid();
                nextPoseCheck = now + 1_000_000_000L;
            }
            if (now - started >= DURATION) {
                finish(now);
            }
        } catch (Exception error) {
            fail(error);
        }
    }

    private static boolean poseIsValid() throws IllegalAccessException {
        Object entity = player.get(client);
        if (entity == null || screen.get(client) != null) {
            return false;
        }
        double[] expected = {8.5, 100.0, 8.5, 0, 24};
        for (int index = 0; index < pose.length; index++) {
            double observed = ((Number) pose[index].get(entity)).doubleValue();
            if (!Double.isFinite(observed) || Math.abs(observed - expected[index]) > 0.05) {
                return false;
            }
        }
        return true;
    }

    private static void sample() {
        while (sampling) {
            String snapshot = Telemetry.snapshot();
            synchronized (samples) {
                samples.append(Instant.now()).append(' ').append(state).append(' ').append(snapshot).append('\n');
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException end) {
                return;
            }
        }
    }

    private static void finish(long now) throws Exception {
        sampling = false;
        sampler.interrupt();
        sampler.join(1500);
        long[] sorted = Arrays.copyOf(intervals, frames);
        Arrays.sort(sorted);
        int slowCount = Math.max(1, (int) Math.ceil(frames * 0.01));
        long slowSum = 0;
        int over33 = 0, over50 = 0, over100 = 0;
        for (int index = 0; index < frames; index++) {
            long interval = sorted[index];
            if (index >= frames - slowCount) slowSum += interval;
            if (interval > 33_333_333L) over33++;
            if (interval > 50_000_000L) over50++;
            if (interval > 100_000_000L) over100++;
        }
        String os = Files.exists(Paths.get("/etc/brick-release")) ? "Brick v1" : "Lubuntu baseline / external Linux";
        String report = String.format(Locale.ROOT,
                "Brick benchmark v1%nUTC=%s%nSystem=%s%nKernel=%s%nJava=%s (%s)%nMax Java heap MiB=%d%n" +
                "OpenGL renderer=%s%nFramebuffer=%s%nFixed camera verified=%s%n" +
                "Scene=seed 8675309; position 8.5,100,8.5; yaw 0 pitch 24; 30 s warmup%n" +
                "Duration seconds=%.3f%nFrames=%d%nAverage FPS=%.2f%n1%% low FPS=%.2f%n" +
                "P99 frame ms=%.3f%nLongest frame ms=%.3f%nFrames >33.3/50/100 ms=%d/%d/%d%n",
                runTime, os, System.getProperty("os.version"), System.getProperty("java.version"),
                System.getProperty("java.vendor"), Runtime.getRuntime().maxMemory() / 1048576,
                renderer, resolution, cameraValid, (now - started) / 1e9, frames,
                frames * 1e9 / (now - started), slowCount * 1e9 / slowSum,
                sorted[(int) Math.ceil(frames * 0.99) - 1] / 1e6, sorted[frames - 1] / 1e6,
                over33, over50, over100);
        Path gameDirectory = temporaryWorld.getParent().getParent();
        report += "CPU and graphics settings\n" + Telemetry.metadata(gameDirectory);
        synchronized (samples) {
            report += "\nTelemetry at ~1 Hz (thermal counts are cumulative)\n" + samples;
        }
        report += "\nFrame intervals in milliseconds, chronological\n";
        StringBuilder raw = new StringBuilder();
        for (int index = 0; index < frames; index++) {
            raw.append(intervals[index] / 1e6).append('\n');
        }
        Path output = resultDirectory.resolve("minecraft-benchmark-" + runTime.toString().replace(':', '-') + ".txt");
        Path staging = Files.createTempFile(resultDirectory, ".benchmark-", ".partial");
        try (FileOutputStream file = new FileOutputStream(staging.toFile())) {
            file.write(report.getBytes(StandardCharsets.UTF_8));
            file.write(raw.toString().getBytes(StandardCharsets.UTF_8));
            file.getFD().sync();
        }
        Files.move(staging, output, StandardCopyOption.ATOMIC_MOVE);
        System.out.println("[Brick benchmark] Saved " + output);
        Object game = client;
        client = null;
        // Request Minecraft's normal shutdown and world save.
        Method shutdown = game.getClass().getDeclaredMethod("m");
        shutdown.setAccessible(true);
        shutdown.invoke(game);
    }

    private static void fail(Exception error) {
        loadRequested = true;
        client = null;
        sampling = false;
        if (sampler != null) sampler.interrupt();
        error.printStackTrace();
        System.err.println("[Brick benchmark] Benchmark failed. " + error);
    }
}
