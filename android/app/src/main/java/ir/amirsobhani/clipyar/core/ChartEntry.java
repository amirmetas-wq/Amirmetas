package ir.amirsobhani.clipyar.core;

public final class ChartEntry {
    public final int rank;
    public final long dailyViews,totalViews;
    public final String videoId,title,channel,pageUrl;
    public ChartEntry(int rank,long dailyViews,long totalViews,String videoId,String title,String channel,String pageUrl) {
        this.rank=rank;this.dailyViews=dailyViews;this.totalViews=totalViews;this.videoId=videoId;
        this.title=title;this.channel=channel;this.pageUrl=pageUrl;
    }
}
