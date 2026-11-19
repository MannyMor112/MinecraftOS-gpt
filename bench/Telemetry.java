package brick;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.Map;

final class Telemetry {
    private static final Path CPU = Paths.get("/sys/devices/system/cpu");
    private static final Path DRM = Paths.get("/sys/class/drm");
    private static final Path THERMAL = Paths.get("/sys/class/thermal");

    private Telemetry() {}

    static String snapshot() {
        Map<String, String> values = new TreeMap<>();
        for (Path cpu : directories(CPU, "cpu[0-9]*")) {
            String prefix = cpu.getFileName().toString();
            values.put(prefix + "_kHz", read(cpu.resolve("cpufreq/scaling_cur_freq")));
            for (Path counter : directories(cpu.resolve("thermal_throttle"), "*throttle_count")) {
                values.put(prefix + "_" + counter.getFileName(), read(counter));
            }
        }
        for (Path card : directories(DRM, "card[0-9]*")) {
            if (!card.getFileName().toString().matches("card[0-9]+")) continue;
            String prefix = card.getFileName().toString();
            String frequency = read(card.resolve("gt_act_freq_mhz"));
            if ("unavailable".equals(frequency)) frequency = read(card.resolve("gt/gt0/rps_act_freq_mhz"));
            values.put(prefix + "_GPU_MHz", frequency);
            for (Path flag : directories(card.resolve("gt/gt0"), "*throttle*")) {
                values.put(prefix + "_" + flag.getFileName(), read(flag));
            }
        }
        for (Path zone : directories(THERMAL, "thermal_zone*")) {
            values.put(zone.getFileName() + "_" + read(zone.resolve("type")) + "_mC", read(zone.resolve("temp")));
        }
        for (Path monitor : directories(Paths.get("/sys/class/hwmon"), "hwmon*")) {
            if (!"coretemp".equals(read(monitor.resolve("name")))) continue;
            for (Path input : directories(monitor, "temp*_input")) {
                values.put(monitor.getFileName() + "_" + input.getFileName() + "_mC", read(input));
            }
        }
        Set<String> memory = new HashSet<>();
        for (String key : new String[] {"MemTotal", "MemAvailable", "SwapTotal", "SwapFree", "SwapCached"}) {
            memory.add(key);
        }
        Path meminfo = Paths.get("/proc/meminfo");
        try {
            for (String line : Files.readAllLines(meminfo, StandardCharsets.US_ASCII)) {
                String[] fields = line.split(":\\s+", 2);
                if (memory.contains(fields[0])) values.put(fields[0] + "_kB", fields[1].replace(" kB", "").trim());
            }
        } catch (IOException error) {
            values.put("meminfo", "unavailable");
        }
        values.put("zram_mm_stat", read(Paths.get("/sys/block/zram0/mm_stat")).replaceAll("\\s+", ","));
        values.put("jvm_heap_used_MiB", Long.toString(
                ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / 1048576));
        StringBuilder line = new StringBuilder();
        for (Map.Entry<String, String> metric : values.entrySet()) {
            if (line.length() > 0) line.append(' ');
            line.append(metric.getKey()).append('=').append(metric.getValue());
        }
        return line.toString();
    }

    static String metadata(Path game) {
        StringBuilder result = new StringBuilder();
        for (Path policy : directories(CPU.resolve("cpufreq"), "policy*")) {
            result.append(policy.getFileName()).append(" governor=")
                  .append(read(policy.resolve("scaling_governor"))).append(" driver=")
                  .append(read(policy.resolve("scaling_driver"))).append('\n');
        }
        for (Path status : directories(CPU.resolve("vulnerabilities"), "*")) {
            result.append(status.getFileName()).append('=').append(read(status)).append('\n');
        }
        result.append("GC=");
        ManagementFactory.getGarbageCollectorMXBeans().forEach(bean -> result.append(bean.getName()).append(" | "));
        result.append('\n');
        result.append("zram compression=").append(read(Paths.get("/sys/block/zram0/comp_algorithm"))).append('\n');
        result.append("Per-core throttling counters and GPU causes are present only if the kernel exports them.\n");
        for (String name : new String[] {"options.txt", "optionsof.txt"}) {
            Path path = game.resolve(name);
            result.append(name).append(" SHA256=").append(hash(path)).append('\n');
        }
        return result.toString();
    }

    private static String hash(Path path) {
        try {
            byte[] bytes = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
            StringBuilder hash = new StringBuilder();
            for (byte element : bytes) hash.append(String.format("%02x", element & 0xff));
            return hash.toString();
        } catch (IOException | java.security.NoSuchAlgorithmException error) {
            return "unavailable";
        }
    }

    private static List<Path> directories(Path root, String glob) {
        List<Path> paths = new java.util.ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(root, glob)) {
            for (Path path : entries) paths.add(path);
        } catch (IOException ignored) {
            // The selected kernel or hardware may not export the requested metric.
        }
        return paths;
    }

    private static String read(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.US_ASCII).trim();
        } catch (IOException error) {
            return "unavailable";
        }
    }
}
