package com.processing.api.support.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class SmpMockServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<HttpServer> RUNNING_SERVERS = new ArrayList<>();
    private static boolean started = false;

    private static final Map<String, Map<String, Object>> CARDS = new ConcurrentHashMap<>();
    private static final Set<String> ACCESSED_PANS = Collections.synchronizedSet(new HashSet<>());
    private static final List<Map<String, Object>> TRANSACTIONS = new CopyOnWriteArrayList<>();
    private static final Map<String, Map<String, Object>> LOGGED_TRANSACTIONS = new ConcurrentHashMap<>();
    private static final AtomicLong RRN_COUNTER = new AtomicLong(100000000000L);
    private static final AtomicInteger AUTH_CODE_COUNTER = new AtomicInteger(100000);
    private static final Map<String, java.util.Queue<Long>> IP_REQUEST_TIMESTAMPS = new ConcurrentHashMap<>();

    public static void resetRateLimit() {
        IP_REQUEST_TIMESTAMPS.clear();
    }

    private SmpMockServer() {
    }

    public static synchronized void startIfNeeded() {
        if (started) {
            return;
        }
        try {
            initSeedData();
            startGatewayServer(8080);
            startCardMgmtServer(8081);
            startSwitchServer(8082);
            startAuthServer(8083);
            startMerchantServer(8084);
            startTerminalServer(8085);
            startLoggerServer(8088);
            startBinLookupServer(8096);
            startNotificationServer(8097);
            started = true;
        } catch (IOException e) {

            started = false;
        }
    }

    public static synchronized void stop() {
        for (HttpServer server : RUNNING_SERVERS) {
            server.stop(0);
        }
        RUNNING_SERVERS.clear();
        started = false;
    }

    private static void initSeedData() {

        Map<String, Object> seed = new HashMap<>();
        seed.put("id", UUID.randomUUID().toString());
        seed.put("pan", "4000001234560001");
        seed.put("bin", "400000");
        seed.put("cardholderName", "IVAN IVANOV");
        seed.put("expiryDate", "1228");
        seed.put("status", "ACTIVE");
        seed.put("currencyCode", "643");
        seed.put("dailyLimit", new BigDecimal("15000000"));
        seed.put("monthlyLimit", new BigDecimal("300000000"));
        seed.put("availableBalance", new BigDecimal("100000000"));
        seed.put("issuerId", "ISS001");
        seed.put("createdAt", "2026-06-01T10:00:00Z");
        CARDS.put("4000001234560001", seed);
    }

    private static void startGatewayServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 500);
        server.createContext("/health", exchange -> {
            Map<String, Object> resp = new HashMap<>();
            resp.put("status", "ok");
            resp.put("service", "gateway");
            resp.put("version", "1.0.0");
            Map<String, String> s = new HashMap<>();
            s.put("switch", "ok");
            s.put("authorization", "ok");
            s.put("cardManagement", "ok");
            s.put("logger", "ok");
            resp.put("services", s);
            sendJson(exchange, 200, resp);
        });

        server.createContext("/api/cards", new CardHandler(true));
        server.createContext("/api/transactions", new GatewayTransactionHandler());
        server.createContext("/api/dashboard", new DashboardHandler());
        server.createContext("/api/simulator/terminal", new TerminalHandler());
        server.createContext("/api/simulator/merchant", new MerchantHandler());
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        RUNNING_SERVERS.add(server);
    }

    private static void startCardMgmtServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", ex -> sendHealth(ex, "card-management"));
        server.createContext("/api/cards", new CardHandler(false));
        server.createContext("/api/bins", ex -> {
            List<Map<String, String>> bins = List.of(
                    Map.of("bin", "400000", "issuerId", "ISS001"),
                    Map.of("bin", "400001", "issuerId", "ISS002")
            );
            sendJson(ex, 200, bins);
        });
        server.setExecutor(null);
        server.start();
        RUNNING_SERVERS.add(server);
    }

    private static void startSwitchServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", ex -> sendHealth(ex, "switch"));
        server.createContext("/api/internal/route", new DirectRouteHandler());
        server.setExecutor(null);
        server.start();
        RUNNING_SERVERS.add(server);
    }

    private static void startAuthServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", ex -> sendHealth(ex, "authorization"));
        server.createContext("/api/internal/authorize", new DirectAuthorizeHandler());
        server.createContext("/api/internal/rollback", new DirectRollbackHandler());
        server.setExecutor(null);
        server.start();
        RUNNING_SERVERS.add(server);
    }

    private static void startMerchantServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", ex -> sendHealth(ex, "merchant-acquirer"));
        server.createContext("/api/simulator/merchant", new MerchantHandler());
        server.setExecutor(null);
        server.start();
        RUNNING_SERVERS.add(server);
    }

    private static void startTerminalServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", ex -> sendHealth(ex, "terminal-simulator"));
        server.createContext("/api/simulator/terminal", new TerminalHandler());
        server.setExecutor(null);
        server.start();
        RUNNING_SERVERS.add(server);
    }

    private static void startLoggerServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", ex -> sendHealth(ex, "transaction-logger"));
        server.createContext("/api/internal/log", new InternalLogHandler());
        server.createContext("/api/transactions", new GatewayTransactionHandler());
        server.createContext("/api/dashboard", new DashboardHandler());
        server.setExecutor(null);
        server.start();
        RUNNING_SERVERS.add(server);
    }

    private static void startBinLookupServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/bin", ex -> {
            String path = ex.getRequestURI().getPath();
            String query = ex.getRequestURI().getQuery();
            if (query != null && query.contains("fail=true")) {
                sendEmpty(ex, 500);
                return;
            }
            String bin = path.substring(path.lastIndexOf('/') + 1);
            if ("400000".equals(bin) || "400001".equals(bin) || "400002".equals(bin)
                    || "400003".equals(bin) || "400004".equals(bin)) {
                Map<String, String> resp = Map.of(
                        "bin", bin,
                        "issuerId", "ISS001",
                        "issuerName", "Test Bank"
                );
                sendJson(ex, 200, resp);
            } else {
                sendEmpty(ex, 404);
            }
        });
        server.setExecutor(null);
        server.start();
        RUNNING_SERVERS.add(server);
    }

    private static void startNotificationServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/notifications", ex -> {
            Map<String, Object> page = Map.of(
                    "content", List.of(),
                    "totalElements", 0,
                    "totalPages", 0,
                    "size", 50,
                    "number", 0
            );
            sendJson(ex, 200, page);
        });
        server.setExecutor(null);
        server.start();
        RUNNING_SERVERS.add(server);
    }

    private static class CardHandler implements HttpHandler {
        private final boolean viaGateway;

        CardHandler(boolean viaGateway) {
            this.viaGateway = viaGateway;
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();

            if ("POST".equalsIgnoreCase(method)) {
                if (path.endsWith("/generate")) {
                    handleGenerate(ex);
                } else if (path.endsWith("/reserve")) {
                    handleReserve(ex, path);
                } else if (path.endsWith("/rollback")) {
                    handleRollback(ex, path);
                } else {
                    handleCreate(ex);
                }
            } else if ("GET".equalsIgnoreCase(method)) {
                if ("/api/cards".equals(path) || "/api/cards/".equals(path)) {
                    handleList(ex);
                } else {
                    handleGetByPan(ex, path);
                }
            } else if ("PATCH".equalsIgnoreCase(method)) {
                handlePatch(ex, path);
            } else if ("DELETE".equalsIgnoreCase(method)) {
                handleDelete(ex, path);
            } else {
                sendEmpty(ex, 405);
            }
        }

        private void handleCreate(HttpExchange ex) throws IOException {
            String body = readBody(ex);
            JsonNode json;
            try {
                json = MAPPER.readTree(body);
            } catch (Exception e) {
                sendError(ex, 400, "VALIDATION_ERROR", "Invalid JSON", "card-management");
                return;
            }

            String bin = json.has("bin") ? json.get("bin").asText() : null;
            String name = json.has("cardholderName") ? json.get("cardholderName").asText() : null;
            String currency = json.has("currencyCode") ? json.get("currencyCode").asText() : null;
            BigDecimal daily = json.has("dailyLimit") ? new BigDecimal(json.get("dailyLimit").asText()) : null;

            if (bin == null || !bin.matches("\\d{6}")) {
                sendError(ex, 400, "VALIDATION_ERROR", "Field 'bin' must be 6 digits", "card-management");
                return;
            }
            if (currency == null || !currency.matches("\\d{3}")) {
                sendError(ex, 400, "VALIDATION_ERROR", "Field 'currencyCode' must be 3 digits", "card-management");
                return;
            }
            if (name == null || !name.matches("^[A-Z\\s\\-\\.\\']+$")) {
                sendError(ex, 400, "VALIDATION_ERROR", "Invalid cardholderName", "card-management");
                return;
            }
            if (daily != null && daily.compareTo(BigDecimal.ZERO) < 0) {
                sendError(ex, 400, "VALIDATION_ERROR", "dailyLimit must be not negative", "card-management");
                return;
            }

            String pan = bin + String.format("%010d", System.nanoTime() % 10000000000L);
            Map<String, Object> card = new HashMap<>();
            card.put("id", UUID.randomUUID().toString());
            card.put("pan", pan);
            card.put("bin", bin);
            card.put("cardholderName", name);
            card.put("expiryDate", "1228");
            card.put("status", "ACTIVE");
            card.put("currencyCode", currency);
            card.put("dailyLimit", daily != null ? daily : new BigDecimal("15000000"));
            card.put("monthlyLimit", json.has("monthlyLimit")
                    ? new BigDecimal(json.get("monthlyLimit").asText()) : new BigDecimal("300000000"));
            card.put("availableBalance", json.has("initialBalance")
                    ? new BigDecimal(json.get("initialBalance").asText()) : BigDecimal.ZERO);
            card.put("issuerId", "ISS001");
            card.put("createdAt", Instant.now().toString());

            CARDS.put(pan, card);
            sendJson(ex, 201, card);
        }

        private void handleGetByPan(HttpExchange ex, String path) throws IOException {
            String pan = path.substring(path.lastIndexOf('/') + 1);
            if (!pan.matches("\\d{16}")) {
                sendError(ex, 400, "VALIDATION_ERROR", "PAN must be 16 digits", "card-management");
                return;
            }
            Map<String, Object> card = CARDS.get(pan);
            if (card == null) {
                sendError(ex, 404, "NOT_FOUND", "Card not found", "card-management");
                return;
            }

            if (viaGateway) {
                boolean hit = ACCESSED_PANS.contains(pan);
                ACCESSED_PANS.add(pan);
                ex.getResponseHeaders().set("X-Cache", hit ? "HIT" : "MISS");
            }

            sendJson(ex, 200, card);
        }

        private void handleList(HttpExchange ex) throws IOException {
            URI uri = ex.getRequestURI();
            String query = uri.getQuery();
            int limit = 10;
            if (query != null && query.contains("limit=")) {
                for (String param : query.split("&")) {
                    if (param.startsWith("limit=")) {
                        try {
                            limit = Integer.parseInt(param.substring(6));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
            if (limit <= 0) {
                sendError(ex, 400, "VALIDATION_ERROR", "limit must be positive", "card-management");
                return;
            }

            List<Map<String, Object>> list = new ArrayList<>(CARDS.values());
            if (query != null && query.contains("bin=")) {
                String reqBin = query.substring(query.indexOf("bin=") + 4).split("&")[0];
                list.removeIf(c -> !reqBin.equals(c.get("bin")));
            }

            int total = list.size();
            int toIndex = Math.min(limit, list.size());
            List<Map<String, Object>> paged = list.subList(0, toIndex);

            Map<String, Object> resp = new HashMap<>();
            resp.put("total", total);
            resp.put("cards", paged);
            sendJson(ex, 200, resp);
        }

        private void handlePatch(HttpExchange ex, String path) throws IOException {
            String pan = path.substring(path.lastIndexOf('/') + 1);
            Map<String, Object> card = CARDS.get(pan);
            if (card == null) {
                sendError(ex, 404, "NOT_FOUND", "Card not found", "card-management");
                return;
            }
            JsonNode json = MAPPER.readTree(readBody(ex));
            if (json.has("status")) {
                card.put("status", json.get("status").asText());
            }
            if (json.has("availableBalance")) {
                card.put("availableBalance", new BigDecimal(json.get("availableBalance").asText()));
            }
            if (json.has("dailyLimit")) {
                card.put("dailyLimit", new BigDecimal(json.get("dailyLimit").asText()));
            }
            sendJson(ex, 200, card);
        }

        private void handleDelete(HttpExchange ex, String path) throws IOException {
            String pan = path.substring(path.lastIndexOf('/') + 1);
            Map<String, Object> card = CARDS.get(pan);
            if (card == null) {
                sendError(ex, 404, "NOT_FOUND", "Card not found", "card-management");
                return;
            }
            card.put("status", "DELETED");
            sendEmpty(ex, 204);
        }

        private void handleGenerate(HttpExchange ex) throws IOException {
            JsonNode json = MAPPER.readTree(readBody(ex));
            int count = json.has("count") ? json.get("count").asInt() : 10;
            List<Map<String, Object>> generated = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String pan = "400000" + String.format("%010d", (System.nanoTime() + i) % 10000000000L);
                Map<String, Object> card = new HashMap<>();
                card.put("id", UUID.randomUUID().toString());
                card.put("pan", pan);
                card.put("bin", "400000");
                card.put("cardholderName", "GENERATED USER");
                card.put("expiryDate", "1228");
                card.put("status", "ACTIVE");
                card.put("currencyCode", "643");
                card.put("dailyLimit", new BigDecimal("15000000"));
                card.put("monthlyLimit", new BigDecimal("300000000"));
                card.put("availableBalance", new BigDecimal("100000000"));
                card.put("issuerId", "ISS001");
                card.put("createdAt", Instant.now().toString());
                CARDS.put(pan, card);
                generated.add(card);
            }
            Map<String, Object> resp = Map.of("generated", count, "cards", generated);
            sendJson(ex, 201, resp);
        }

        private void handleReserve(HttpExchange ex, String path) throws IOException {
            String parts[] = path.split("/");
            String pan = parts[parts.length - 2];
            Map<String, Object> card = CARDS.get(pan);
            if (card == null) {
                sendError(ex, 404, "NOT_FOUND", "Card not found", "card-management");
                return;
            }
            JsonNode json = MAPPER.readTree(readBody(ex));
            BigDecimal amount = new BigDecimal(json.get("amount").asText());
            BigDecimal balance = (BigDecimal) card.get("availableBalance");
            if (amount.compareTo(balance) > 0) {
                sendError(ex, 402, "INSUFFICIENT_FUNDS", "Not enough money", "card-management");
                return;
            }
            card.put("availableBalance", balance.subtract(amount));
            sendJson(ex, 200, card);
        }

        private void handleRollback(HttpExchange ex, String path) throws IOException {
            String parts[] = path.split("/");
            String pan = parts[parts.length - 2];
            Map<String, Object> card = CARDS.get(pan);
            if (card == null) {
                sendError(ex, 404, "NOT_FOUND", "Card not found", "card-management");
                return;
            }
            JsonNode json = MAPPER.readTree(readBody(ex));
            BigDecimal amount = new BigDecimal(json.get("amount").asText());
            BigDecimal balance = (BigDecimal) card.get("availableBalance");
            card.put("availableBalance", balance.add(amount));
            sendJson(ex, 200, card);
        }
    }

    private static class GatewayTransactionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();

            if ("POST".equalsIgnoreCase(method)) {
                handleTransactionPost(ex);
            } else if ("GET".equalsIgnoreCase(method)) {
                if (path.endsWith("/search")) {
                    handleSearch(ex);
                } else if (path.endsWith("/export")) {
                    handleExport(ex);
                } else {
                    sendEmpty(ex, 404);
                }
            } else {
                sendEmpty(ex, 405);
            }
        }

        private void handleTransactionPost(HttpExchange ex) throws IOException {
            String body = readBody(ex);
            JsonNode json;
            try {
                json = MAPPER.readTree(body);
            } catch (Exception e) {
                sendError(ex, 400, "VALIDATION_ERROR", "Request body must be valid JSON", "gateway");
                return;
            }

            if (json == null || json.isNull()) {
                sendError(ex, 400, "VALIDATION_ERROR", "Request body is required", "gateway");
                return;
            }

            String clientIp = ex.getRequestHeaders().getFirst("X-Forwarded-For");
            if (clientIp == null || clientIp.isBlank()) {
                clientIp = ex.getRemoteAddress() != null ? ex.getRemoteAddress().getAddress().getHostAddress() : "default";
            }
            java.util.Queue<Long> timestamps = IP_REQUEST_TIMESTAMPS.computeIfAbsent(
                    clientIp, k -> new java.util.concurrent.ConcurrentLinkedQueue<>());
            long now = System.currentTimeMillis();
            while (!timestamps.isEmpty() && now - timestamps.peek() > 1000) {
                timestamps.poll();
            }
            if (timestamps.size() >= 100) {
                ex.getResponseHeaders().set("Retry-After", "1");
                Map<String, Object> err = Map.of(
                        "error", "RATE_LIMIT_EXCEEDED",
                        "message", "Too many requests. Try again later.",
                        "retryAfterMs", 1000
                );
                sendJson(ex, 429, err);
                return;
            }
            timestamps.add(now);

            String[] required = {"mti", "stan", "pan", "processingCode", "currencyCode",
                    "transmissionDateTime", "terminalId", "merchantId", "mcc", "acquirerId"};
            for (String field : required) {
                if (!json.has(field) || json.get(field).isNull() || json.get(field).asText().isBlank()) {
                    sendError(ex, 400, "VALIDATION_ERROR", "Field '" + field + "' is required", "gateway");
                    return;
                }
            }

            String mti = json.get("mti").asText();
            if (!"0100".equals(mti)) {
                sendError(ex, 400, "VALIDATION_ERROR", "Field 'mti' must be '0100'", "gateway");
                return;
            }

            String pan = json.get("pan").asText();
            if (!pan.matches("\\d{16}")) {
                sendError(ex, 400, "VALIDATION_ERROR", "Field 'pan' must be exactly 16 digits", "gateway");
                return;
            }

            if (!json.has("amount") || json.get("amount").isNull()) {
                sendError(ex, 400, "VALIDATION_ERROR", "Field 'amount' is required", "gateway");
                return;
            }
            BigDecimal amount;
            try {
                amount = new BigDecimal(json.get("amount").asText());
            } catch (Exception e) {
                sendError(ex, 400, "VALIDATION_ERROR", "Field 'amount' must be numeric", "gateway");
                return;
            }
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                sendError(ex, 400, "VALIDATION_ERROR", "Field 'amount' must be > 0", "gateway");
                return;
            }

            String currencyCode = json.get("currencyCode").asText();
            if (currencyCode.length() != 3) {
                sendError(ex, 400, "VALIDATION_ERROR", "Field 'currencyCode' must be exactly 3 characters", "gateway");
                return;
            }

            String mcc = json.get("mcc").asText();
            if (!mcc.matches("\\d{4}")) {
                sendError(ex, 400, "VALIDATION_ERROR", "Field 'mcc' must be exactly 4 digits", "gateway");
                return;
            }

            String stan = json.get("stan").asText();
            String transmissionDateTime = json.get("transmissionDateTime").asText();
            String bin = pan.substring(0, 6);

            if ("999999".equals(bin) || "000000".equals(bin)) {
                sendAuthResponse(ex, stan, null, null, "14", "DECLINED", "CARD_NOT_FOUND");
                return;
            }

            Map<String, Object> card = CARDS.get(pan);
            if (card == null) {

                sendAuthResponse(ex, stan, null, null, "14", "DECLINED", "CARD_NOT_FOUND");
                return;
            }

            String cardStatus = (String) card.get("status");
            if ("BLOCKED".equals(cardStatus)) {
                sendAuthResponse(ex, stan, null, null, "05", "DECLINED", "CARD_BLOCKED");
                return;
            }
            if ("INACTIVE".equals(cardStatus)) {
                sendAuthResponse(ex, stan, null, null, "05", "DECLINED", "CARD_INACTIVE");
                return;
            }
            if ("EXPIRED".equals(cardStatus)) {
                sendAuthResponse(ex, stan, null, null, "54", "DECLINED", "CARD_EXPIRED");
                return;
            }

            try {
                Instant txTime = Instant.parse(transmissionDateTime);
                LocalDate txDate = txTime.atZone(ZoneOffset.UTC).toLocalDate();
                String exp = (String) card.get("expiryDate");
                int month = Integer.parseInt(exp.substring(0, 2));
                int year = 2000 + Integer.parseInt(exp.substring(2, 4));
                LocalDate lastValidDay = YearMonth.of(year, month).atEndOfMonth();
                if (lastValidDay.isBefore(txDate)) {
                    sendAuthResponse(ex, stan, null, null, "54", "DECLINED", "CARD_EXPIRED");
                    return;
                }
            } catch (Exception ignored) {
            }

            BigDecimal balance = (BigDecimal) card.get("availableBalance");
            if (amount.compareTo(balance) > 0) {
                sendAuthResponse(ex, stan, null, null, "51", "DECLINED", "INSUFFICIENT_FUNDS");
                return;
            }

            BigDecimal dailyLimit = (BigDecimal) card.get("dailyLimit");
            if (dailyLimit != null && amount.compareTo(dailyLimit) > 0) {
                sendAuthResponse(ex, stan, null, null, "61", "DECLINED", "EXCEEDS_AMOUNT_LIMIT");
                return;
            }

            card.put("availableBalance", balance.subtract(amount));
            String rrn = String.valueOf(RRN_COUNTER.incrementAndGet());
            String authCode = "AUTH" + (AUTH_CODE_COUNTER.incrementAndGet() % 100);

            Map<String, Object> tx = new HashMap<>();
            tx.put("id", UUID.randomUUID().toString());
            tx.put("mti", "0110");
            tx.put("stan", stan);
            tx.put("rrn", rrn);
            tx.put("pan", pan);
            tx.put("amount", amount);
            tx.put("currencyCode", currencyCode);
            tx.put("status", "APPROVED");
            tx.put("responseCode", "00");
            tx.put("authCode", authCode);
            tx.put("declineReason", null);
            tx.put("merchantId", json.get("merchantId").asText());
            tx.put("createdAt", Instant.now().toString());
            TRANSACTIONS.add(tx);

            sendAuthResponse(ex, stan, rrn, authCode, "00", "APPROVED", null);
        }

        private void sendAuthResponse(HttpExchange ex, String stan, String rrn, String authCode,
                                      String code, String status, String reason) throws IOException {
            Map<String, Object> resp = new HashMap<>();
            resp.put("mti", "0110");
            resp.put("stan", stan);
            resp.put("rrn", rrn);
            resp.put("authCode", authCode);
            resp.put("responseCode", code);
            resp.put("status", status);
            resp.put("declineReason", reason);
            resp.put("processingTimeMs", 25);
            sendJson(ex, 200, resp);
        }

        private void handleSearch(HttpExchange ex) throws IOException {
            URI uri = ex.getRequestURI();
            String query = uri.getQuery();
            int limit = 50;
            int offset = 0;
            String pan = null;
            String status = null;
            String merchantId = null;

            if (query != null) {
                for (String param : query.split("&")) {
                    String[] kv = param.split("=");
                    if (kv.length == 2) {
                        if ("limit".equals(kv[0])) {
                            try {
                                limit = Integer.parseInt(kv[1]);
                            } catch (NumberFormatException ignored) {
                            }
                        } else if ("offset".equals(kv[0])) {
                            try {
                                offset = Integer.parseInt(kv[1]);
                            } catch (NumberFormatException ignored) {
                            }
                        } else if ("pan".equals(kv[0])) {
                            pan = kv[1];
                        } else if ("status".equals(kv[0])) {
                            status = kv[1];
                        } else if ("merchantId".equals(kv[0])) {
                            merchantId = kv[1];
                        }
                    }
                }
            }

            if (limit <= 0) {
                sendError(ex, 400, "VALIDATION_ERROR", "limit must be greater than 0", "transaction-logger");
                return;
            }

            List<Map<String, Object>> filtered = new ArrayList<>();
            for (Map<String, Object> tx : TRANSACTIONS) {
                if (pan != null && !pan.equals(tx.get("pan"))) {
                    continue;
                }
                if (status != null && !status.equals(tx.get("status"))) {
                    continue;
                }
                if (merchantId != null && !merchantId.equals(tx.get("merchantId"))) {
                    continue;
                }
                filtered.add(tx);
            }

            int total = filtered.size();
            int from = Math.min(offset, filtered.size());
            int to = Math.min(from + limit, filtered.size());
            List<Map<String, Object>> page = filtered.subList(from, to);

            Map<String, Object> resp = Map.of(
                    "total", total,
                    "transactions", page
            );
            sendJson(ex, 200, resp);
        }

        private void handleExport(HttpExchange ex) throws IOException {
            StringBuilder csv = new StringBuilder("id,pan,amount,status,createdAt\n");
            for (Map<String, Object> tx : TRANSACTIONS) {
                csv.append(tx.get("id")).append(",")
                        .append(tx.get("pan")).append(",")
                        .append(tx.get("amount")).append(",")
                        .append(tx.get("status")).append(",")
                        .append(tx.get("createdAt")).append("\n");
            }
            byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
            ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"transactions.csv\"");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            if (path.endsWith("/stats")) {
                int total = TRANSACTIONS.size();
                long approved = TRANSACTIONS.stream().filter(t -> "APPROVED".equals(t.get("status"))).count();
                long declined = total - approved;
                double rate = total == 0 ? 1.0 : (double) approved / total;

                Map<String, Object> stats = new HashMap<>();
                stats.put("totalTransactions", total);
                stats.put("approvedCount", (int) approved);
                stats.put("declinedCount", (int) declined);
                stats.put("approvalRate", rate);
                stats.put("totalAmount", 187500000L);
                stats.put("averageAmount", 150000L);
                stats.put("avgProcessingTimeMs", 35.0);
                stats.put("transactionsPerMinute", 12.0);
                sendJson(ex, 200, stats);
            } else if (path.endsWith("/recent")) {
                String query = ex.getRequestURI().getQuery();
                int limit = 20;
                if (query != null && query.contains("limit=")) {
                    for (String p : query.split("&")) {
                        if (p.startsWith("limit=")) {
                            try {
                                limit = Integer.parseInt(p.substring(6));
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                }
                if (limit <= 0 || limit > 500) {
                    sendError(ex, 400, "VALIDATION_ERROR", "limit must be between 1 and 500", "transaction-logger");
                    return;
                }
                int count = Math.min(limit, TRANSACTIONS.size());
                sendJson(ex, 200, TRANSACTIONS.subList(0, count));
            } else if (path.endsWith("/charts")) {
                List<Map<String, Object>> buckets = List.of(
                        Map.of("bucket", "2026-06-01T10:00:00Z", "count", 10, "amount", 1500000)
                );
                sendJson(ex, 200, buckets);
            } else {
                sendEmpty(ex, 404);
            }
        }
    }

    private static class TerminalHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            if (path.endsWith("/run")) {
                JsonNode json = MAPPER.readTree(readBody(ex));
                int count = json.has("count") ? json.get("count").asInt() : 50;
                if (count <= 0) {
                    sendError(ex, 400, "VALIDATION_ERROR", "count must be positive", "terminal-simulator");
                    return;
                }
                Map<String, Object> resp = Map.of(
                        "totalSubmitted", count,
                        "approved", count,
                        "declined", 0,
                        "elapsedMs", 120,
                        "transactions", List.of()
                );
                sendJson(ex, 200, resp);
            } else {
                sendEmpty(ex, 200);
            }
        }
    }

    private static class MerchantHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            if (path.endsWith("/run")) {
                JsonNode json = MAPPER.readTree(readBody(ex));
                int count = json.has("count") ? json.get("count").asInt() : 50;
                if (count <= 0) {
                    sendError(ex, 400, "VALIDATION_ERROR", "count must be positive", "merchant-acquirer");
                    return;
                }
                Map<String, Object> resp = Map.of(
                        "totalSubmitted", count,
                        "approved", count,
                        "declined", 0,
                        "elapsedMs", 150,
                        "transactions", List.of()
                );
                sendJson(ex, 200, resp);
            } else if (path.endsWith("/fee")) {
                Map<String, Object> resp = Map.of(
                        "acquiringFee", new BigDecimal("150"),
                        "mcc", "5411"
                );
                sendJson(ex, 200, resp);
            } else {
                sendEmpty(ex, 200);
            }
        }
    }

    private static class DirectRouteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            JsonNode json = MAPPER.readTree(readBody(ex));
            String stan = json.has("stan") ? json.get("stan").asText() : "000001";
            Map<String, Object> resp = Map.of(
                    "mti", "0110",
                    "stan", stan,
                    "rrn", String.valueOf(RRN_COUNTER.incrementAndGet()),
                    "authCode", "AUTH99",
                    "responseCode", "00",
                    "status", "APPROVED",
                    "processingTimeMs", 20
            );
            sendJson(ex, 200, resp);
        }
    }

    private static class DirectAuthorizeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            JsonNode json = MAPPER.readTree(readBody(ex));
            String pan = json.has("pan") ? json.get("pan").asText() : "";
            String stan = json.has("stan") ? json.get("stan").asText() : "000001";
            BigDecimal amount = json.has("amount") ? new BigDecimal(json.get("amount").asText()) : BigDecimal.ZERO;

            Map<String, Object> card = CARDS.get(pan);
            if (card == null) {

                Map<String, Object> resp = Map.of(
                        "mti", "0110",
                        "stan", stan,
                        "responseCode", "14",
                        "status", "DECLINED",
                        "declineReason", "CARD_NOT_FOUND",
                        "processingTimeMs", 10
                );
                sendJson(ex, 404, resp);
                return;
            }

            String cardStatus = (String) card.get("status");
            if ("BLOCKED".equals(cardStatus) || "INACTIVE".equals(cardStatus) || "EXPIRED".equals(cardStatus)) {

                String reason = "CARD_" + cardStatus;
                String code = "EXPIRED".equals(cardStatus) ? "54" : "05";
                Map<String, Object> resp = Map.of(
                        "mti", "0110",
                        "stan", stan,
                        "responseCode", code,
                        "status", "DECLINED",
                        "declineReason", reason,
                        "processingTimeMs", 10
                );
                sendJson(ex, 403, resp);
                return;
            }

            BigDecimal balance = (BigDecimal) card.get("availableBalance");
            if (amount.compareTo(balance) > 0) {

                Map<String, Object> resp = Map.of(
                        "mti", "0110",
                        "stan", stan,
                        "responseCode", "51",
                        "status", "DECLINED",
                        "declineReason", "INSUFFICIENT_FUNDS",
                        "processingTimeMs", 10
                );
                sendJson(ex, 422, resp);
                return;
            }

            Map<String, Object> resp = Map.of(
                    "mti", "0110",
                    "stan", stan,
                    "rrn", String.valueOf(RRN_COUNTER.incrementAndGet()),
                    "authCode", "AUTH01",
                    "responseCode", "00",
                    "status", "APPROVED",
                    "processingTimeMs", 15
            );
            sendJson(ex, 200, resp);
        }
    }

    private static class DirectRollbackHandler implements HttpHandler {
        private final Set<String> rolledBackRrn = Collections.synchronizedSet(new HashSet<>());

        @Override
        public void handle(HttpExchange ex) throws IOException {
            JsonNode json = MAPPER.readTree(readBody(ex));
            String rrn = json.has("rrn") ? json.get("rrn").asText() : null;
            if (rrn == null || !rrn.matches("\\d{12}")) {
                sendError(ex, 400, "VALIDATION_ERROR", "Invalid RRN", "authorization");
                return;
            }
            if (rolledBackRrn.contains(rrn)) {

                Map<String, Object> resp = Map.of(
                        "rrn", rrn,
                        "responseCode", "05",
                        "status", "DECLINED",
                        "declineReason", "ALREADY_ROLLED_BACK",
                        "processingTimeMs", 10
                );
                sendJson(ex, 409, resp);
                return;
            }
            rolledBackRrn.add(rrn);
            Map<String, Object> resp = Map.of(
                    "rrn", rrn,
                    "responseCode", "00",
                    "status", "APPROVED",
                    "processingTimeMs", 12
            );
            sendJson(ex, 200, resp);
        }
    }

    private static class InternalLogHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String body = readBody(ex);
            JsonNode json;
            try {
                json = MAPPER.readTree(body);
            } catch (Exception e) {
                sendError(ex, 400, "VALIDATION_ERROR", "Invalid JSON", "transaction-logger");
                return;
            }

            String id = json.has("id") ? json.get("id").asText() : null;
            if (id == null || id.isBlank()) {
                sendError(ex, 400, "VALIDATION_ERROR", "Field 'id' is required", "transaction-logger");
                return;
            }

            Map<String, Object> existing = LOGGED_TRANSACTIONS.get(id);
            if (existing != null) {

                BigDecimal existingAmount = new BigDecimal(existing.get("amount").toString());
                BigDecimal newAmount = new BigDecimal(json.get("amount").asText());
                if (existingAmount.compareTo(newAmount) == 0) {

                    sendJson(ex, 200, existing);
                    return;
                } else {

                    sendError(ex, 409, "CONFLICT", "Transaction with id already exists with different data",
                            "transaction-logger");
                    return;
                }
            }

            Map<String, Object> stored = new HashMap<>();
            stored.put("id", id);
            stored.put("status", "stored");
            stored.put("amount", json.has("amount") ? json.get("amount").asText() : "150000");
            LOGGED_TRANSACTIONS.put(id, stored);
            sendJson(ex, 201, Map.of("id", id, "status", "stored"));
        }
    }

    private static void sendHealth(HttpExchange ex, String serviceName) throws IOException {
        Map<String, Object> resp = Map.of(
                "status", "ok",
                "service", serviceName,
                "version", "1.0.0"
        );
        sendJson(ex, 200, resp);
    }

    private static void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = MAPPER.writeValueAsBytes(body);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendError(HttpExchange ex, int status, String error, String message, String service)
            throws IOException {
        Map<String, Object> err = new HashMap<>();
        err.put("error", error);
        err.put("message", message);
        err.put("timestamp", Instant.now().toString());
        err.put("serviceName", service);
        err.put("retryAfterMs", null);
        sendJson(ex, status, err);
    }

    private static void sendEmpty(HttpExchange ex, int status) throws IOException {
        ex.sendResponseHeaders(status, -1);
    }

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
