package com.processing.api.support;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;

public final class TestConfig {

    private static final String DEFAULT_GATEWAY_URL = "http://localhost:8080";
    private static final String DEFAULT_CARD_MGMT_URL = "http://localhost:8081";
    private static final String DEFAULT_SWITCH_URL = "http://localhost:8082";
    private static final String DEFAULT_AUTH_URL = "http://localhost:8083";
    private static final String DEFAULT_LOGGER_URL = "http://localhost:8088";
    private static final String DEFAULT_TERMINAL_URL = "http://localhost:8085";
    private static final String DEFAULT_MERCHANT_URL = "http://localhost:8084";
    private static final String DEFAULT_BIN_LOOKUP_URL = "http://localhost:8096";
    private static final String DEFAULT_NOTIFICATION_URL = "http://localhost:8097";

    private static Boolean liveStackDetected;

    private TestConfig() {
    }

    public static String getGatewayUrl() {
        return getPropertyOrEnv("gateway.url", "GATEWAY_URL", DEFAULT_GATEWAY_URL);
    }

    public static String getCardMgmtUrl() {
        return getPropertyOrEnv("card.mgmt.url", "CARD_MGMT_URL", DEFAULT_CARD_MGMT_URL);
    }

    public static String getSwitchUrl() {
        return getPropertyOrEnv("switch.url", "SWITCH_URL", DEFAULT_SWITCH_URL);
    }

    public static String getAuthUrl() {
        return getPropertyOrEnv("auth.url", "AUTH_URL", DEFAULT_AUTH_URL);
    }

    public static String getLoggerUrl() {
        return getPropertyOrEnv("logger.url", "LOGGER_URL", DEFAULT_LOGGER_URL);
    }

    public static String getTerminalUrl() {
        return getPropertyOrEnv("terminal.url", "TERMINAL_URL", DEFAULT_TERMINAL_URL);
    }

    public static String getMerchantUrl() {
        return getPropertyOrEnv("merchant.url", "MERCHANT_URL", DEFAULT_MERCHANT_URL);
    }

    public static String getBinLookupUrl() {
        return getPropertyOrEnv("bin.lookup.url", "BIN_LOOKUP_URL", DEFAULT_BIN_LOOKUP_URL);
    }

    public static String getNotificationUrl() {
        return getPropertyOrEnv("notification.url", "NOTIFICATION_URL", DEFAULT_NOTIFICATION_URL);
    }

    
    public static synchronized boolean isLiveStackRunning() {
        if (liveStackDetected != null) {
            return liveStackDetected;
        }
        try {
            URI uri = URI.create(getGatewayUrl() + "/health");
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            liveStackDetected = (code == 200);
        } catch (IOException e) {
            liveStackDetected = false;
        }
        return liveStackDetected;
    }

    private static String getPropertyOrEnv(String propKey, String envKey, String defaultValue) {
        String propValue = System.getProperty(propKey);
        if (propValue != null && !propValue.isBlank()) {
            return propValue;
        }
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return defaultValue;
    }
}
