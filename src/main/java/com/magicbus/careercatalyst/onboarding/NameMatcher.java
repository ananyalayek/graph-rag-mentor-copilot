package com.magicbus.careercatalyst.onboarding;

public class NameMatcher {
    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase().replaceAll("[^a-z]", "");
    }

    public static boolean allMatch(String profileName, String a, String p, String i) {
        String base = normalize(profileName);
        return base.equals(normalize(a)) && base.equals(normalize(p)) && base.equals(normalize(i));
    }
}
