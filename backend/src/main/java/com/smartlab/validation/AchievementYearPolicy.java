package com.smartlab.validation;

import java.time.Year;

public final class AchievementYearPolicy {
    public static final int MIN_YEAR = 2023;
    public static final String ERROR_MESSAGE = "Achievement year must be between 2023 and the current year";

    private AchievementYearPolicy() {
    }

    public static boolean isValid(Integer year) {
        return year != null && year >= MIN_YEAR && year <= Year.now().getValue();
    }
}
