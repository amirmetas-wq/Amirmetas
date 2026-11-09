package ir.amirsobhani.clipyar.core;

public final class VideoCandidate {
    public final String id, url, title, channel, license;
    public final long uploadedEpoch, views, durationMs;
    public final boolean live;
    public final int chartRank;
    public final long dailyViews,chartTotalViews,chartEpoch;
    public final String chartUrl;
    public VideoCandidate(String id, String url, String title, String channel, String license,
                          long uploadedEpoch, long views, long durationMs, boolean live) {
        this(id,url,title,channel,license,uploadedEpoch,views,durationMs,live,0,-1,-1,0,"");
    }
    private VideoCandidate(String id,String url,String title,String channel,String license,long uploadedEpoch,long views,long durationMs,
                           boolean live,int rank,long daily,long chartTotal,long chartEpoch,String chartUrl) {
        this.id=id;this.url=url;this.title=title;this.channel=channel;this.license=license;this.uploadedEpoch=uploadedEpoch;
        this.views=views;this.durationMs=durationMs;this.live=live;this.chartRank=rank;this.dailyViews=daily;
        this.chartTotalViews=chartTotal;this.chartEpoch=chartEpoch;this.chartUrl=chartUrl;
    }
    public VideoCandidate withChart(int rank,long daily,long total,long epoch,String url) {
        return new VideoCandidate(id,this.url,title,channel,license,uploadedEpoch,views,durationMs,live,rank,daily,total,epoch,url);
    }
}
