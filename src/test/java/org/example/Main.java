package org.example;

import com.google.gson.*;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class Main {

    private static final String BASE_URL = "https://qa-automation.armorcode.ai/public";
    private static final String BASE_URL_GET = "https://qa-automation.armorcode.ai";
    private static final String LOGIN_ENDPOINT = "/login";
    private static final String FEATURE_FLAG_ENDPOINT = "/api/super-admin/feature-flag";
    private static final String SESSION_COOKIE_NAME = "SESSION";
    private static final String CSRF_TOKEN_HEADER = "X-CSRF-Token";
    private static final String FLAG_NAME = "FREEMIUM_FEATURES";

    private static final Gson gson = new GsonBuilder().serializeNulls().create();

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java org.example.Main <tenantId> <function>");
            System.exit(1);
        }

        int tenantId = Integer.parseInt(args[0]);
        String function = args[1];

        // Print or use these arguments as needed
        System.out.println("Tenant ID: " + tenantId);
        System.out.println("Function: " + function);

        // Update email and password as needed
        String email = args[2]; // Update as needed
        String password = args[3]; // Update as needed

        System.out.println("Email: " + email);
        System.out.println("Password: " + password);

        Main main = new Main();
        Response response = main.loginAndGetResponse(email, password);
        if (response.getStatusCode() != 200) {
            logError("Login failed", response);
            return;
        }

        Map<String, String> cookies = response.getCookies();
        String csrfToken = extractCsrfToken(response);
        String sessionId = extractSessionId(cookies);

        validateSessionData(cookies, csrfToken, sessionId);

        String jsonInput = fetchFeatureFlags(csrfToken, sessionId);
        if (!"N/A".equals(jsonInput)) {
            // Add or remove tenant based on function argument
            String updatedJson = jsonInput;
            if ("add".equalsIgnoreCase(function)) {
                updatedJson = addTenant(updatedJson, tenantId);
            } else if ("remove".equalsIgnoreCase(function)) {
                updatedJson = removeTenant(updatedJson, tenantId);
            } else {
                System.err.println("Unknown function: " + function);
                System.exit(1);
            }

            sendFeatureFlagPayload(updatedJson, csrfToken, sessionId);
        }
    }

    private Response loginAndGetResponse(String email, String password) {
        String requestBody = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
        System.out.println("Req:"+requestBody);
        Response response = given()
                .baseUri(BASE_URL)
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .post(LOGIN_ENDPOINT)
                .then()
                .log().all()
                .extract().response();

        logResponseDetails("Login response", response);
        return response;
    }

    private static String fetchFeatureFlags(String csrfToken, String sessionId) {
        Response getResponse = given()
                .baseUri(BASE_URL_GET)
                .cookie(SESSION_COOKIE_NAME, sessionId)
                .header(CSRF_TOKEN_HEADER, csrfToken)
                .when()
                .get(FEATURE_FLAG_ENDPOINT)
                .then()
                .extract().response();

        if (getResponse.getStatusCode() != 200) {
            logError("Failed to fetch the feature flag details", getResponse);
            return "N/A";
        }

        return extractFeatureFlagDetails(getResponse);
    }

    public static String addTenant(String jsonInput, int tenantId) {
        JsonObject jsonObject = JsonParser.parseString(jsonInput).getAsJsonObject();
        JsonArray tenants = extractTenants(jsonObject);

        JsonElement tenantIdElement = gson.toJsonTree(tenantId);
        if (!tenants.contains(tenantIdElement)) {
            tenants.add(tenantIdElement);
        }

        jsonObject.add("tenants", tenants);
        return gson.toJson(jsonObject);
    }

    public static String removeTenant(String jsonInput, int tenantId) {
        JsonObject jsonObject = JsonParser.parseString(jsonInput).getAsJsonObject();
        JsonArray tenants = extractTenants(jsonObject);

        JsonArray updatedTenants = new JsonArray();
        JsonElement tenantIdElement = gson.toJsonTree(tenantId);

        for (JsonElement element : tenants) {
            if (!element.equals(tenantIdElement)) {
                updatedTenants.add(element);
            }
        }

        jsonObject.add("tenants", updatedTenants);
        return gson.toJson(jsonObject);
    }

    private static JsonArray extractTenants(JsonObject jsonObject) {
        JsonElement tenantsElement = jsonObject.get("tenants");
        return tenantsElement != null && tenantsElement.isJsonArray() ? tenantsElement.getAsJsonArray() : new JsonArray();
    }

    public static void sendFeatureFlagPayload(String jsonPayload, String csrfToken, String sessionId) {
        Response response = given()
                .baseUri(BASE_URL_GET)
                .contentType(ContentType.JSON)
                .cookie(SESSION_COOKIE_NAME, sessionId)
                .header(CSRF_TOKEN_HEADER, csrfToken)
                .body(jsonPayload)
                .when()
                .put(FEATURE_FLAG_ENDPOINT)
                .then()
                .extract().response();

        if (response.getStatusCode() != 200) {
            logError("Failed to send the feature flag payload", response);
        } else {
            System.out.println("Successfully sent the feature flag payload");
            System.out.println(jsonPayload);
        }
    }

    private static String extractCsrfToken(Response response) {
        String token = response.getHeader(CSRF_TOKEN_HEADER);
        if (token == null) {
            try {
                token = response.jsonPath().getString("csrfToken");
            } catch (Exception e) {
                System.err.println("Failed to parse response body as JSON: " + e.getMessage());
            }
        }
        return token;
    }

    private static String extractSessionId(Map<String, String> cookies) {
        return cookies.get(SESSION_COOKIE_NAME);
    }

    private static void validateSessionData(Map<String, String> cookies, String csrfToken, String sessionId) {
        assertThat("Cookies should not be null", cookies, is(notNullValue()));
        assertThat("Cookies should contain entries", cookies.size(), greaterThan(0));
        assertThat("CSRF Token should not be null", csrfToken, is(notNullValue()));
        assertThat("CSRF Token should not be empty", csrfToken, not(isEmptyString()));
        assertThat("Session ID should not be null", sessionId, is(notNullValue()));
        assertThat("Session ID should not be empty", sessionId, not(isEmptyString()));
    }

    private static String extractFeatureFlagDetails(Response response) {
        JsonPath jsonPath = response.jsonPath();
        String name = jsonPath.getString("find { it.name == '" + FLAG_NAME + "' }.name");
        String comments = jsonPath.getString("find { it.name == '" + FLAG_NAME + "' }.comments");
        List<Object> tenants = jsonPath.getList("find { it.name == '" + FLAG_NAME + "' }.tenants");
        String gaStatus = jsonPath.getString("find { it.name == '" + FLAG_NAME + "' }.gaStatus");
        String section = jsonPath.getString("find { it.name == '" + FLAG_NAME + "' }.section");
        String status = jsonPath.getString("find { it.name == '" + FLAG_NAME + "' }.status");
        String ticketLink = jsonPath.getString("find { it.name == '" + FLAG_NAME + "' }.ticketLink");

        Map<String, Object> featureFlagDetails = new HashMap<>();
        featureFlagDetails.put("name", name);
        featureFlagDetails.put("tenants", tenants);
        featureFlagDetails.put("comments", comments);
        featureFlagDetails.put("gaStatus", gaStatus);
        featureFlagDetails.put("section", section);
        featureFlagDetails.put("status", status);
        featureFlagDetails.put("ticketLink", ticketLink);

        return gson.toJson(featureFlagDetails);
    }

    private void logResponseDetails(String message, Response response) {
        System.out.println(message);
        System.out.println("Status code: " + response.getStatusCode());
        System.out.println("Response body: " + response.getBody().asString());
    }

    private static void logError(String message, Response response) {
        System.err.println(message);
        System.err.println("Status code: " + response.getStatusCode());
        System.err.println("Response body: " + response.getBody().asString());
    }
}
