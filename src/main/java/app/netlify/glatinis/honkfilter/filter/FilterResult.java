package app.netlify.glatinis.honkfilter.filter;

public record FilterResult(String text, String blocked, String warning, boolean shadow, String violation, String detail) {

    public static FilterResult allow(String text) {
        return new FilterResult(text, null, null, false, null, "");
    }

    public static FilterResult block(String text, String message, String violation, String detail) {
        return new FilterResult(text, message, null, false, violation, detail);
    }

    public boolean isBlocked() {
        return blocked != null;
    }
}
