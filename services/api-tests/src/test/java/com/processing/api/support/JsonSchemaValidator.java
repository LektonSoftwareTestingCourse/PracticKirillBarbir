package com.processing.api.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public final class JsonSchemaValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonSchemaValidator() {
    }


    public static void assertCardSchema(String jsonResponse) {
        JsonNode root = parse(jsonResponse);
        assertTrue(root.isObject(), "Root must be an object");
        assertField(root, "id", JsonNode::isTextual);
        assertField(root, "pan", n -> n.isTextual() && n.asText().matches("\\d{16}"));
        assertField(root, "bin", n -> n.isTextual() && n.asText().matches("\\d{6}"));
        assertField(root, "cardholderName", JsonNode::isTextual);
        assertField(root, "expiryDate", n -> n.isTextual() && n.asText().matches("^(\\d{4}|\\d{4}-\\d{2})$"));
        assertField(root, "status", JsonNode::isTextual);
        assertField(root, "currencyCode", n -> n.isTextual() && n.asText().matches("\\d{3}"));
        assertField(root, "dailyLimit", JsonNode::isNumber);
        assertField(root, "monthlyLimit", JsonNode::isNumber);
        assertField(root, "availableBalance", JsonNode::isNumber);
        assertField(root, "issuerId", JsonNode::isTextual);
        assertField(root, "createdAt", JsonNode::isTextual);
    }


    public static void assertAuthorizationResponseSchema(String jsonResponse) {
        JsonNode root = parse(jsonResponse);
        assertTrue(root.isObject(), "Root must be an object");
        assertField(root, "mti", n -> n.isTextual() && ("0110".equals(n.asText()) || "0100".equals(n.asText())));
        assertField(root, "stan", n -> n.isTextual() && n.asText().matches("\\d{6}"));
        assertField(root, "responseCode", n -> n.isTextual() && n.asText().matches("\\d{2}"));
        assertField(root, "status", n -> n.isTextual() && ("APPROVED".equals(n.asText())
                || "DECLINED".equals(n.asText())));
        assertField(root, "processingTimeMs", JsonNode::isNumber);
    }


    public static void assertHealthResponseSchema(String jsonResponse) {
        JsonNode root = parse(jsonResponse);
        assertTrue(root.isObject(), "Root must be an object");
        assertField(root, "status", JsonNode::isTextual);
        assertField(root, "service", JsonNode::isTextual);
        assertField(root, "version", JsonNode::isTextual);
        assertField(root, "services", JsonNode::isObject);
        JsonNode services = root.get("services");
        assertField(services, "switch", JsonNode::isTextual);
        assertField(services, "authorization", JsonNode::isTextual);
        assertField(services, "cardManagement", JsonNode::isTextual);
        assertField(services, "logger", JsonNode::isTextual);
    }


    public static void assertErrorResponseSchema(String jsonResponse) {
        JsonNode root = parse(jsonResponse);
        assertTrue(root.isObject(), "Root must be an object");
        assertField(root, "error", JsonNode::isTextual);
        assertField(root, "message", JsonNode::isTextual);
    }

    private static void assertField(JsonNode parent, String fieldName, java.util.function.Predicate<JsonNode> check) {
        JsonNode node = parent.get(fieldName);
        assertNotNull(node, "Field '" + fieldName + "' must be present");
        assertFalse(node.isNull(), "Field '" + fieldName + "' must not be null");
        assertTrue(check.test(node), "Field '" + fieldName + "' violates contract type/format");
    }

    private static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (IOException e) {
            throw new AssertionError("Invalid JSON response payload: " + json, e);
        }
    }
}
