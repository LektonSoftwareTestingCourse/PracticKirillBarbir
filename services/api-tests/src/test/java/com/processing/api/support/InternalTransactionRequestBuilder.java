package com.processing.api.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class InternalTransactionRequestBuilder {

    private String id = UUID.randomUUID().toString();
    private String mti = "0100";
    private String stan = DataGenerator.generateStan();
    private String rrn = DataGenerator.generateRrn();
    private String pan = DataGenerator.generatePan("400000");
    private String processingCode = "000000";
    private BigDecimal amount = new BigDecimal("150000");
    private String currencyCode = "643";
    private String transmissionDateTime = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
    private String terminalId = "TERM0001";
    private String terminalType = "POS";
    private String merchantId = "MERCH1234567890";
    private String mcc = "5411";
    private String acquirerId = "ACQ001";
    private String issuerId = "ISS001";
    private String authCode = "AUTH01";
    private String responseCode = "00";
    private String status = "APPROVED";
    private String declineReason = null;
    private Integer processingTimeMs = 30;
    private BigDecimal acquiringFee = new BigDecimal("150");
    private String createdAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();

    public static InternalTransactionRequestBuilder aTransaction() {
        return new InternalTransactionRequestBuilder();
    }

    public InternalTransactionRequestBuilder withId(String id) {
        this.id = id;
        return this;
    }

    public InternalTransactionRequestBuilder withStan(String stan) {
        this.stan = stan;
        return this;
    }

    public InternalTransactionRequestBuilder withRrn(String rrn) {
        this.rrn = rrn;
        return this;
    }

    public InternalTransactionRequestBuilder withPan(String pan) {
        this.pan = pan;
        return this;
    }

    public InternalTransactionRequestBuilder withAmount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    public InternalTransactionRequestBuilder withStatus(String status) {
        this.status = status;
        return this;
    }

    public InternalTransactionRequestBuilder withResponseCode(String responseCode) {
        this.responseCode = responseCode;
        return this;
    }

    public InternalTransactionRequestBuilder withDeclineReason(String declineReason) {
        this.declineReason = declineReason;
        return this;
    }

    public Map<String, Object> build() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("mti", mti);
        map.put("stan", stan);
        map.put("rrn", rrn);
        map.put("pan", pan);
        map.put("processingCode", processingCode);
        map.put("amount", amount);
        map.put("currencyCode", currencyCode);
        map.put("transmissionDateTime", transmissionDateTime);
        map.put("terminalId", terminalId);
        map.put("terminalType", terminalType);
        map.put("merchantId", merchantId);
        map.put("mcc", mcc);
        map.put("acquirerId", acquirerId);
        map.put("issuerId", issuerId);
        map.put("authCode", authCode);
        map.put("responseCode", responseCode);
        map.put("status", status);
        map.put("declineReason", declineReason);
        map.put("processingTimeMs", processingTimeMs);
        map.put("acquiringFee", acquiringFee);
        map.put("createdAt", createdAt);
        return map;
    }
}
