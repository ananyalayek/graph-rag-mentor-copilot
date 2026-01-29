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
        if (base.isEmpty()) {
            return false;
        }
        boolean aadhaarOk = base.equals(normalize(a));
        boolean panOk = p == null || p.isBlank() || base.equals(normalize(p));
        boolean incomeOk = base.equals(normalize(i));
        return aadhaarOk && panOk && incomeOk;
    }
}
