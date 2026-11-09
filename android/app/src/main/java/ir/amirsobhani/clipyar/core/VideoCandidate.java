package ir.amirsobhani.clipyar.core;

public final class VideoCandidate {
    public final String id, url, title, channel, license;
    public final long uploadedEpoch, views, durationMs;
    public final boolean live;
    public VideoCandidate(String id, String url, String title, String channel, String license,
                          long uploadedEpoch, long views, long durationMs, boolean live) {
        this.id=id; this.url=url; this.title=title; this.channel=channel; this.license=license;
        this.uploadedEpoch=uploadedEpoch; this.views=views; this.durationMs=durationMs; this.live=live;
    }
}
