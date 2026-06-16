package com.honortech.dataplatform.user.enums;

import java.util.Arrays;
import java.util.Locale;

public enum UserRoleCode {
    ADMIN,
    COLLECTOR,
    ANNOTATOR,
    VIEWER;

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isValid(String value) {
        String normalized = normalize(value);
        return Arrays.stream(values()).anyMatch(item -> item.name().equals(normalized));
    }
}
