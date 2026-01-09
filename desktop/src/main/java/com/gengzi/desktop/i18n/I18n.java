package com.gengzi.desktop.i18n;

import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

public final class I18n {
    private static final String BUNDLE_BASE = "i18n.messages";
    private static Locale locale = Locale.SIMPLIFIED_CHINESE;
    private static ResourceBundle bundle = loadBundle(locale);

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
        bundle = loadBundle(locale);
    }

    public static String tr(String key, Object... args) {
        String pattern = bundle.getString(key);
        if (args == null || args.length == 0) {
            return pattern;
        }
        MessageFormat format = new MessageFormat(pattern, locale);
        return format.format(args);
    }

    private static ResourceBundle loadBundle(Locale locale) {
        try {
            String resourceName = BUNDLE_BASE + "_" + locale.toString() + ".properties";
            URL resource = I18n.class.getClassLoader().getResource(resourceName);
            if (resource == null) {
                // Fallback to default bundle
                resource = I18n.class.getClassLoader().getResource(BUNDLE_BASE + ".properties");
            }
            if (resource != null) {
                return new PropertyResourceBundle(
                    new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8)
                );
            }
        } catch (Exception e) {
            System.err.println("Failed to load resource bundle: " + e.getMessage());
        }
        // Fallback to default loading mechanism
        return ResourceBundle.getBundle(BUNDLE_BASE, locale);
    }
}
