package ir.amirsobhani.clipyar.core;

import java.util.*;
import java.util.regex.Pattern;

/** Select original captions before server-side translations; never rotate requests after 429. */
public final class SubtitlePolicy {
    public static final long COOLDOWN_MS=60L*60*1000;
    private static final Pattern LIMITED=Pattern.compile("(?i)(?:http(?:\\s+error)?\\s*[:=]?\\s*429\\b|too\\s+many\\s+requests)");
    public static final class Track {
        public final String key,language;
        public final boolean automatic;
        public Track(String key,String language,boolean automatic) {this.key=key;this.language=language;this.automatic=automatic;}
    }
    private SubtitlePolicy() { }
    public static Track choose(Collection<String> manual,Collection<String> automatic) {
        String key=pick(manual,"fa");if(key!=null) return new Track(key,"fa",false);
        key=pick(manual,"en");if(key!=null) return new Track(key,"en",false);
        key=pick(automatic,"en");if(key!=null) return new Track(key,"en",true);
        key=pick(automatic,"fa");return key==null?null:new Track(key,"fa",true);
    }
    private static String pick(Collection<String> keys,String language) {
        for(String preferred:new String[]{language+"-orig",language}) if(keys.contains(preferred)) return preferred;
        List<String> sorted=new ArrayList<>(keys);Collections.sort(sorted);
        // A regional track is accepted; en-fr/fa-en are translated tracks, not originals.
        for(String key:sorted) if(key.matches(language+"-[A-Z]{2}")) return key;
        return null;
    }
    public static boolean isRateLimited(String error) {return error!=null&&LIMITED.matcher(error).find();}
    public static boolean isHumanVerification(String error) {
        if(error==null) return false;String text=error.toLowerCase(Locale.ROOT);
        return text.contains("not a bot")||text.contains("captcha")||text.contains("verify you are human")||text.contains("verify you're human");
    }
    public static long remaining(long now,long blockedUntil) {return Math.max(0,blockedUntil-now);}
}
