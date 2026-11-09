package ir.amirsobhani.clipyar.core;

import java.time.*;

public final class DailyTime {
    public static final ZoneId TEHRAN = ZoneId.of("Asia/Tehran");
    private DailyTime() { }
    public static ZonedDateTime next(Instant now, int hour, int minute) {
        ZonedDateTime local = now.atZone(TEHRAN);
        ZonedDateTime next = local.toLocalDate().atTime(hour, minute).atZone(TEHRAN);
        return next.toInstant().isAfter(now) ? next : next.plusDays(1);
    }
    public static String day(Instant now) { return now.atZone(TEHRAN).toLocalDate().toString(); }
}
