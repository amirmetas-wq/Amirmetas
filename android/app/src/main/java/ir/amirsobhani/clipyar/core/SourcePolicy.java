package ir.amirsobhani.clipyar.core;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

public final class SourcePolicy {
    private static final Pattern ID=Pattern.compile("[A-Za-z0-9_-]{11}");
    private SourcePolicy() { }
    public static String canonical(String input) {
        try {
            URI uri=new URI(input.trim());
            String host=uri.getHost();
            if(!"https".equalsIgnoreCase(uri.getScheme()) || host==null || uri.getUserInfo()!=null
                || (uri.getPort()!=-1 && uri.getPort()!=443)) throw new IllegalArgumentException();
            host=host.toLowerCase(Locale.ROOT);
            String id=null,path=uri.getPath();
            if(host.equals("youtu.be")) id=path.substring(1);
            else if(Arrays.asList("youtube.com","www.youtube.com","m.youtube.com","music.youtube.com").contains(host)) {
                if(path.equals("/watch") && uri.getRawQuery()!=null) {
                    for(String pair:uri.getRawQuery().split("&")) if(pair.startsWith("v="))
                        id=URLDecoder.decode(pair.substring(2),StandardCharsets.UTF_8.name());
                } else if(path.startsWith("/shorts/") || path.startsWith("/live/")) id=path.substring(path.lastIndexOf('/')+1);
            }
            if(id==null || !ID.matcher(id).matches()) throw new IllegalArgumentException();
            return "https://www.youtube.com/watch?v="+id;
        } catch(Exception e) { throw new IllegalArgumentException("لینک مستقیم و معتبر ویدئوی یوتیوب را وارد کن."); }
    }
}
