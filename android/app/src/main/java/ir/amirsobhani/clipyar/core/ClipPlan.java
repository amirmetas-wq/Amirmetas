package ir.amirsobhani.clipyar.core;

import java.util.*;

public final class ClipPlan {
    public static final long PART_MS = 60_000;
    public static final class Part {
        public final long startMs, durationMs;
        public Part(long startMs, long durationMs) { this.startMs = startMs; this.durationMs = durationMs; }
    }
    private ClipPlan() { }
    public static List<Part> split(long durationMs) {
        if (durationMs <= 0 || durationMs > 7L * 24 * 60 * 60 * 1000) throw new IllegalArgumentException("Invalid duration");
        List<Part> parts = new ArrayList<>();
        for (long start = 0; start < durationMs; start += PART_MS)
            parts.add(new Part(start, Math.min(PART_MS, durationMs - start)));
        return parts;
    }
    public static String seconds(long ms) { return String.format(Locale.ROOT, "%.3f", ms / 1000.0); }
    public static List<String> ffmpeg(String binary, String source, String output, Part part, int width) {
        int height = width * 16 / 9;
        String filter = "[0:v]split=2[bg][fg];[bg]scale=" + width + ":" + height
            + ":force_original_aspect_ratio=increase,crop=" + width + ":" + height
            + ",boxblur=20:1[back];[fg]scale=" + width + ":" + height
            + ":force_original_aspect_ratio=decrease[front];[back][front]overlay=(W-w)/2:(H-h)/2,setsar=1,format=yuv420p[v]";
        return new ArrayList<>(Arrays.asList(binary, "-hide_banner", "-nostdin", "-y", "-ss", seconds(part.startMs),
            "-i", source, "-t", seconds(part.durationMs), "-filter_complex_threads", "1", "-filter_complex", filter,
            "-map", "[v]", "-map", "0:a?", "-c:v", "libx264", "-preset", "veryfast", "-crf", "24",
            "-threads", "2", "-r", "30", "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", output));
    }
}
