package com.processing.api.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class TransactionRequestBuilder {

    private String mti = "0100";
    private String stan = DataGenerator.generateStan();
    private String pan;
    private String processingCode = "000000";
    private BigDecimal amount = new BigDecimal("150000");
    private String currencyCode = "643";
    private String transmissionDateTime = Instant.now().toString();
    private String terminalId = "TERM0001";
    private String terminalType = "POS";
    private String merchantId = "MERCH1234567890";
    private String mcc = "5411";
    private String acquirerId = "ACQ001";
    private String issuerId;

    public TransactionRequestBuilder withIssuerId(String issuerId) {
        this.issuerId = issuerId;
        return this;
    }

    public static TransactionRequestBuilder aTransaction() {
        return new TransactionRequestBuilder();
    }

    public TransactionRequestBuilder withMti(String mti) {
        this.mti = mti;
        return this;
    }

    public TransactionRequestBuilder withStan(String stan) {
        this.stan = stan;
        return this;
    }

    public TransactionRequestBuilder withPan(String pan) {
        this.pan = pan;
        return this;
    }

    public TransactionRequestBuilder withProcessingCode(String processingCode) {
        this.processingCode = processingCode;
        return this;
    }

    public TransactionRequestBuilder withAmount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    public TransactionRequestBuilder withAmount(long kopecks) {
        this.amount = BigDecimal.valueOf(kopecks);
        return this;
    }

    public TransactionRequestBuilder withCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
        return this;
    }

    public TransactionRequestBuilder withTransmissionDateTime(String transmissionDateTime) {
        this.transmissionDateTime = transmissionDateTime;
        return this;
    }

    public TransactionRequestBuilder withTerminalId(String terminalId) {
        this.terminalId = terminalId;
        return this;
    }

    public TransactionRequestBuilder withTerminalType(String terminalType) {
        this.terminalType = terminalType;
        return this;
    }

    public TransactionRequestBuilder withMerchantId(String merchantId) {
        this.merchantId = merchantId;
        return this;
    }

    public TransactionRequestBuilder withMcc(String mcc) {
        this.mcc = mcc;
        return this;
    }

    public TransactionRequestBuilder withAcquirerId(String acquirerId) {
        this.acquirerId = acquirerId;
        return this;
    }

    public Map<String, Object> build() {
        Map<String, Object> map = new HashMap<>();
        if (mti != null) {
            map.put("mti", mti);
        }
        if (stan != null) {
            map.put("stan", stan);
        }
        if (pan != null) {
            map.put("pan", pan);
        }
        if (processingCode != null) {
            map.put("processingCode", processingCode);
        }
        if (amount != null) {
            map.put("amount", amount);
        }
        if (currencyCode != null) {
            map.put("currencyCode", currencyCode);
        }
        if (transmissionDateTime != null) {
            map.put("transmissionDateTime", transmissionDateTime);
        }
        if (terminalId != null) {
            map.put("terminalId", terminalId);
        }
        if (terminalType != null) {
            map.put("terminalType", terminalType);
        }
        if (merchantId != null) {
            map.put("merchantId", merchantId);
        }
        if (mcc != null) {
            map.put("mcc", mcc);
        }
        if (acquirerId != null) {
            map.put("acquirerId", acquirerId);
        }
        if (issuerId != null) {
            map.put("issuerId", issuerId);
        }
        return map;
    }
}
