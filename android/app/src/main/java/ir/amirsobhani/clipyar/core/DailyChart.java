package ir.amirsobhani.clipyar.core;

import java.time.*;
import java.util.*;

public final class DailyChart {
    public final Instant updatedAt;
    public final List<ChartEntry> entries;
    public DailyChart(Instant updatedAt,List<ChartEntry> entries) {
        this.updatedAt=updatedAt;this.entries=Collections.unmodifiableList(new ArrayList<>(entries));
    }
    public boolean isToday(Instant now) {
        return !updatedAt.isAfter(now.plusSeconds(300)) && DailyTime.day(updatedAt).equals(DailyTime.day(now));
    }
}
