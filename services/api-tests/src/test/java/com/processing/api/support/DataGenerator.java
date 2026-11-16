package com.processing.api.support;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class DataGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AtomicLong STAN_COUNTER = new AtomicLong(100000);
    private static final AtomicLong PAN_COUNTER = new AtomicLong(100000000L);

    private DataGenerator() {
    }

    
    public static String generatePan(String bin) {
        String baseBin = (bin != null && bin.length() == 6) ? bin : "400000";
        long accountPart = (PAN_COUNTER.incrementAndGet() % 1000000000L);
        String prefix = baseBin + String.format("%09d", accountPart);
        int checksum = calculateLuhnCheckDigit(prefix);
        return prefix + checksum;
    }

    
    public static String generateStan() {
        long value = STAN_COUNTER.incrementAndGet() % 1000000L;
        return String.format("%06d", value);
    }

    
    public static String generateRrn() {
        long timestampPart = System.currentTimeMillis() % 100000000L;
        int randomPart = 1000 + RANDOM.nextInt(9000);
        return String.format("%08d%04d", timestampPart, randomPart);
    }

    
    public static String generateTerminalId() {
        int id = 1000 + RANDOM.nextInt(9000);
        return "TERM" + id;
    }

    
    public static String generateMerchantId() {
        int part = 100000 + RANDOM.nextInt(900000);
        return "MERCH" + part + "001";
    }

    
    public static String generateUuid() {
        return UUID.randomUUID().toString();
    }

    private static int calculateLuhnCheckDigit(String numberWithoutCheck) {
        int sum = 0;
        boolean alternate = true;
        for (int i = numberWithoutCheck.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(numberWithoutCheck.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        int remainder = sum % 10;
        return remainder == 0 ? 0 : 10 - remainder;
    }
}
