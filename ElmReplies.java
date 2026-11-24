package bg.cromasteering.diag;

import java.util.Locale;

/** Pure Java helpers: no assumption that an ELM adapter reply is a pump ECU reply. */
public final class ElmReplies {
    private ElmReplies() {}

    public static String normalized(String reply) {
        return reply == null ? "" : reply.toUpperCase(Locale.US).replace("\r", "\n").trim();
    }

    public static boolean indicatesNoData(String reply) {
        String s = normalized(reply);
        return s.contains("NO DATA") || s.contains("UNABLE TO CONNECT")
                || s.contains("STOPPED") || s.contains("BUS ERROR") || s.contains("?");
    }

    public static String voltage(String reply) {
        if (reply == null) return "unavailable";
        String s = reply.replace(" ", "").toUpperCase(Locale.US).trim();
        if (s.matches("[0-9]{1,2}(\\.[0-9]{1,2})?V")) return s;
        return "unavailable (" + reply.trim() + ")";
    }
}
