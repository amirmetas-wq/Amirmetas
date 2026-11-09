package ir.amirsobhani.clipyar.core;

import java.time.Instant;
import java.util.*;

public final class VideoRanker {
    private VideoRanker() { }
    public static List<VideoCandidate> recent(List<VideoCandidate> input, Instant now, int limit, long maxMs) {
        long end=now.getEpochSecond(), start=end-86_400;
        Map<String,VideoCandidate> unique=new HashMap<>();
        for(VideoCandidate v:input) {
            if(v.live || v.durationMs<=0 || v.uploadedEpoch<start || v.uploadedEpoch>end || v.views<0
                || (maxMs>0 && v.durationMs>maxMs)) continue;
            VideoCandidate previous=unique.get(v.id);
            if(previous==null || v.views>previous.views) unique.put(v.id,v);
        }
        List<VideoCandidate> result=new ArrayList<>(unique.values());
        result.sort(Comparator.comparingLong((VideoCandidate v)->v.views).reversed().thenComparing(v->v.id));
        return new ArrayList<>(result.subList(0,Math.min(Math.max(limit,0),result.size())));
    }
}
