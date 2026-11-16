package com.processing.api.support;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public final class CardRequestBuilder {

    private String bin = "400000";
    private String cardholderName = "IVAN IVANOV";
    private String currencyCode = "643";
    private BigDecimal dailyLimit = new BigDecimal("15000000");
    private BigDecimal monthlyLimit = new BigDecimal("300000000");
    private BigDecimal initialBalance = new BigDecimal("100000000");

    public static CardRequestBuilder aCard() {
        return new CardRequestBuilder();
    }

    public CardRequestBuilder withBin(String bin) {
        this.bin = bin;
        return this;
    }

    public CardRequestBuilder withCardholderName(String cardholderName) {
        this.cardholderName = cardholderName;
        return this;
    }

    public CardRequestBuilder withCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
        return this;
    }

    public CardRequestBuilder withDailyLimit(BigDecimal dailyLimit) {
        this.dailyLimit = dailyLimit;
        return this;
    }

    public CardRequestBuilder withMonthlyLimit(BigDecimal monthlyLimit) {
        this.monthlyLimit = monthlyLimit;
        return this;
    }

    public CardRequestBuilder withInitialBalance(BigDecimal initialBalance) {
        this.initialBalance = initialBalance;
        return this;
    }

    public Map<String, Object> build() {
        Map<String, Object> map = new HashMap<>();
        if (bin != null) {
            map.put("bin", bin);
        }
        if (cardholderName != null) {
            map.put("cardholderName", cardholderName);
        }
        if (currencyCode != null) {
            map.put("currencyCode", currencyCode);
        }
        if (dailyLimit != null) {
            map.put("dailyLimit", dailyLimit);
        }
        if (monthlyLimit != null) {
            map.put("monthlyLimit", monthlyLimit);
        }
        if (initialBalance != null) {
            map.put("initialBalance", initialBalance);
        }
        return map;
    }
}
