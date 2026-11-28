package com.magdi.auto;

/** Only fixed, verified MM APKs on this phone; no general-purpose shell input. */
final class InternalAdbPolicy {
    static int port(String text) {
        text = digits(text);
        if (text == null || !text.matches("[0-9]{1,5}")) throw new IllegalArgumentException();
        int value = Integer.parseInt(text);
        if (value < 1 || value > 65535) throw new IllegalArgumentException();
        return value;
    }
    static String code(String text) {
        text = digits(text);
        if (text == null || !text.matches("[0-9]{6}")) throw new IllegalArgumentException();
        return text;
    }
    static int optionalPort(String text) { return text != null && text.isEmpty() ? 0 : port(text); }
    private static String digits(String text) {
        if (text == null) return null;
        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            int digit = Character.digit(c, 10);
            result.append(digit >= 0 ? (char) ('0' + digit) : c);
        }
        return result.toString();
    }
    static Releases.App target(String text) {
        Releases.App app = Releases.App.valueOf(text == null ? "" : text);
        if (app == Releases.App.AUTO) throw new IllegalArgumentException("Use the normal MM Auto updater");
        return app;
    }
    static String installCommand(Releases.App app, int user, long size) {
        target(app.name());
        if (user < 0 || user > 999 || size < 1 || size > 100L * 1024 * 1024) throw new IllegalArgumentException();
        // Raw exec transports exactly -S bytes; -r preserves existing data, no -d or uninstall.
        return "exec:cmd package install -r --user " + user + " -i com.android.vending -S " + size;
    }
    static boolean success(String output) { return output != null && "Success".equals(output.trim()); }
    static String failureCode(String output) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(INSTALL_(?:FAILED|PARSE_FAILED)_[A-Z0-9_]+)\\b").matcher(output == null ? "" : output);
        return m.find() ? m.group(1) : "NO_CONFIRMED_RESULT";
    }
    private InternalAdbPolicy() {}
}
