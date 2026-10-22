#!/usr/bin/env python3
"""Run the real scheduling method on the host JVM, without an Android emulator."""
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / "android/app/src/main/java/ir/hamrah/agent/AgentScheduler.java").read_text()
start = source.index("    public static long nextDailyMillis(")
end = source.index("\n    public static void scheduleMeeting", start)
method = source[start:end]
harness = r'''import java.time.*;
public final class ScheduleTest {
METHOD
    static void check(String now, String zone, int hour, int minute, String expected) {
        long got = nextDailyMillis(hour, minute, Instant.parse(now).toEpochMilli(), ZoneId.of(zone));
        if (!Instant.ofEpochMilli(got).equals(Instant.parse(expected))) {
            throw new AssertionError(now + " " + zone + " => " + Instant.ofEpochMilli(got) + " != " + expected);
        }
    }
    public static void main(String[] args) {
        check("2026-10-22T04:00:00Z", "Asia/Tehran", 8, 0, "2026-10-22T04:30:00Z");
        check("2026-10-22T05:00:00Z", "Asia/Tehran", 8, 0, "2026-10-23T04:30:00Z");
        check("2026-10-22T04:30:00Z", "Asia/Tehran", 8, 0, "2026-10-23T04:30:00Z");
        check("2026-03-08T05:00:00Z", "America/New_York", 2, 30, "2026-03-08T07:30:00Z");
        check("2026-11-01T04:00:00Z", "America/New_York", 1, 30, "2026-11-01T05:30:00Z");
        check("2026-11-01T05:45:00Z", "America/New_York", 1, 30, "2026-11-02T06:30:00Z");
        check("2026-10-22T23:59:00Z", "UTC", 0, 0, "2026-10-23T00:00:00Z");
        System.out.println("PASS: seven local-time, rollover, equality and DST scheduling cases");
    }
}
'''.replace("METHOD", method)
with tempfile.TemporaryDirectory(prefix="hamrah-schedule-") as directory:
    file = Path(directory) / "ScheduleTest.java"
    file.write_text(harness)
    compiler = [shutil.which("javac")] if shutil.which("javac") else ["java", "com.sun.tools.javac.Main"]
    subprocess.run([*compiler, str(file)], check=True)
    subprocess.run(["java", "-cp", directory, "ScheduleTest"], check=True)
