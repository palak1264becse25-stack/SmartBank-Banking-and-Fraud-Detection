package com.smartbank.fraud;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Formats amounts in Indian Rupees using the Indian digit grouping,
 * for example 60000 becomes "₹60,000" and 279900.5 becomes "₹2,79,900.50".
 */
final class Money {

    private Money() {
    }

    static String format(BigDecimal amount) {
        BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP);
        String sign = value.signum() < 0 ? "-" : "";
        String[] parts = value.abs().toPlainString().split("\\.");
        String paise = parts[1].equals("00") ? "" : "." + parts[1];
        return sign + "₹" + groupIndian(parts[0]) + paise;
    }

    /** Last three digits form one group, every group before that has two digits: 12,34,567. */
    private static String groupIndian(String digits) {
        if (digits.length() <= 3) {
            return digits;
        }
        StringBuilder result = new StringBuilder(digits.substring(digits.length() - 3));
        int end = digits.length() - 3;
        while (end > 0) {
            int start = Math.max(0, end - 2);
            result.insert(0, digits.substring(start, end) + ",");
            end = start;
        }
        return result.toString();
    }
}
