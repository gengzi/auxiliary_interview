package com.gengzi.desktop.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

public final class I18n {
    private static final String BUNDLE_BASE = "i18n.messages";
    private static Locale locale = Locale.SIMPLIFIED_CHINESE;
    private static ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_BASE, locale);

    private I18n() {
    }

    public static Locale getLocale() {
        return locale;
    }

    public static void setLocale(Locale newLocale) {
        if (newLocale == null || newLocale.equals(locale)) {
            return;
        }
        locale = newLocale;
        bundle = ResourceBundle.getBundle(BUNDLE_BASE, locale);
    }

    public static String tr(String key, Object... args) {
        String pattern = bundle.getString(key);
        if (args == null || args.length == 0) {
            return pattern;
        }
        MessageFormat format = new MessageFormat(pattern, locale);
        return format.format(args);
    }
}
