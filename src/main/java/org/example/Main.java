package org.example;

import com.google.gson.*;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

import java.util.*;

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
    private static final String LOGOUT_ENDPOINT = "/logout";

    private static final Gson gson = new GsonBuilder().serializeNulls().create();

    public static void main(String[] args) {
        if (args.length < 5) {
            System.err.println("Usage: java org.example.Main <tenantIds> <function> <email> <password> <flagName>");
            System.exit(1);
        }

        String tenantIdsInput = args[0];
        String function = args[1];
        String email = args[2];
        String password = args[3];
        String flagName = args[4];

        List<Integer> tenantIds = parseTenantIds(tenantIdsInput);
        if (tenantIds.isEmpty()) {
            System.err.println("Tenant IDs list is empty or invalid");
            System.exit(1);
        }

        System.out.println("Tenant IDs: " + tenantIds);
        System.out.println("Function: " + function);
        System.out.println("Email: " + email);
        System.out.println("Password: " + password);
        System.out.println("Flag Name: " + flagName);

        Main main = new Main();
        Response response = main.loginAndGetResponse(email, password);
        if (response == null || response.getStatusCode() != 200) {
            logError("Login failed", response);
            System.exit(1); // Exit with failure status
        }

        Map<String, String> cookies = response.getCookies();
        String csrfToken = extractCsrfToken(response);
        String sessionId = extractSessionId(cookies);

        validateSessionData(cookies, csrfToken, sessionId);

        String jsonInput = fetchFeatureFlags(csrfToken, sessionId, flagName);
        if (!"N/A".equals(jsonInput)) {
            String updatedJson = jsonInput;
            if ("add".equalsIgnoreCase(function)) {
                updatedJson = addTenants(updatedJson, tenantIds);
            } else if ("remove".equalsIgnoreCase(function)) {
                updatedJson = removeTenants(updatedJson, tenantIds);
            } else {
                System.err.println("Unknown function: " + function);
                System.exit(1);
            }

            // Check if there are changes in the payload
            if (!jsonInput.equals(updatedJson)) {
                sendFeatureFlagPayload(updatedJson, csrfToken, sessionId, flagName);
            } else {
                System.out.println("No changes in the feature flag payload. Skipping API call.");
            }

            logout(sessionId);
        }
    }

    private static List<Integer> parseTenantIds(String tenantIdsInput) {
        List<Integer> tenantIds = new ArrayList<>();
        try {
            String[] ids = tenantIdsInput.split(",");
            for (String id : ids) {
                tenantIds.add(Integer.parseInt(id.trim()));
            }
        } catch (NumberFormatException e) {
            System.err.println("Error parsing tenant IDs");
            e.printStackTrace();
        }
        return tenantIds;
    }

    private Response loginAndGetResponse(String email, String password) {
        String requestBody = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
        Response response = null;
        try {
            response = given()
                    .baseUri(BASE_URL)
                    .contentType(ContentType.JSON)
                    .body(requestBody)
                    .when()
                    .post(LOGIN_ENDPOINT)
                    .then()
                    .log().all()
                    .extract().response();

            logResponseDetails("Login response", response);
        } catch (Exception e) {
            handleException("Exception during login", e);
        }
        return response;
    }

    private static String fetchFeatureFlags(String csrfToken, String sessionId, String flagName) {
        Response getResponse = null;
        try {
            getResponse = given()
                    .baseUri(BASE_URL_GET)
                    .cookie(SESSION_COOKIE_NAME, sessionId)
                    .header(CSRF_TOKEN_HEADER, csrfToken)
                    .when()
                    .get(FEATURE_FLAG_ENDPOINT)
                    .then()
                    .extract().response();

            if (getResponse.getStatusCode() != 200) {
                logError("Failed to fetch the feature flag details", getResponse);
                System.exit(1); // Exit with failure status
            }
        } catch (Exception e) {
            handleException("Exception during fetching feature flags", e);
        }

        return extractFeatureFlagDetails(getResponse, flagName);
    }

    public static String addTenants(String jsonInput, List<Integer> tenantIds) {
        JsonObject jsonObject = JsonParser.parseString(jsonInput).getAsJsonObject();
        JsonArray tenants = extractTenants(jsonObject);

        for (Integer tenantId : tenantIds) {
            JsonElement tenantIdElement = gson.toJsonTree(tenantId);
            if (!tenants.contains(tenantIdElement)) {
                tenants.add(tenantIdElement);
            }
        }

        jsonObject.add("tenants", tenants);
        return gson.toJson(jsonObject);
    }

    public static String removeTenants(String jsonInput, List<Integer> tenantIds) {
        JsonObject jsonObject = JsonParser.parseString(jsonInput).getAsJsonObject();
        JsonArray tenants = extractTenants(jsonObject);

        JsonArray updatedTenants = new JsonArray();
        for (JsonElement element : tenants) {
            if (!tenantIds.contains(element.getAsInt())) {
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

    public static void sendFeatureFlagPayload(String jsonPayload, String csrfToken, String sessionId, String flagName) {
        try {
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
                System.exit(1); // Exit with failure status
            } else {
                System.out.println("Successfully sent the feature flag payload for flag: " + flagName);
                System.out.println(jsonPayload);
            }
        } catch (Exception e) {
            handleException("Exception during sending feature flag payload", e);
        }
    }

    private static String extractCsrfToken(Response response) {
        String token = response.getHeader(CSRF_TOKEN_HEADER);
        if (token == null) {
            try {
                token = response.jsonPath().getString("csrfToken");
            } catch (Exception e) {
                handleException("Failed to parse response body as JSON", e);
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

    private static String extractFeatureFlagDetails(Response response, String flagName) {
        JsonPath jsonPath = response.jsonPath();
        String name = jsonPath.getString("find { it.name == '" + flagName + "' }.name");
        String comments = jsonPath.getString("find { it.name == '" + flagName + "' }.comments");
        List<Object> tenants = jsonPath.getList("find { it.name == '" + flagName + "' }.tenants");
        String gaStatus = jsonPath.getString("find { it.name == '" + flagName + "' }.gaStatus");
        String section = jsonPath.getString("find { it.name == '" + flagName + "' }.section");
        String status = jsonPath.getString("find { it.name == '" + flagName + "' }.status");
        String ticketLink = jsonPath.getString("find { it.name == '" + flagName + "' }.ticketLink");

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

    private static void logResponseDetails(String message, Response response) {
        System.out.println(message);
        System.out.println("Status code: " + response.getStatusCode());
        System.out.println("Response body: " + response.getBody().asString());
    }

    private static void logError(String message, Response response) {
        if (response != null) {
            System.err.println(message);
            System.err.println("Status code: " + response.getStatusCode());
            System.err.println("Response body: " + response.getBody().asString());
        } else {
            System.err.println(message + ": Response is null");
        }
    }

    private static void logout(String sessionId) {
        try {
            given()
                    .baseUri(BASE_URL_GET)
                    .cookie(SESSION_COOKIE_NAME, sessionId)
                    .when()
                    .get(LOGOUT_ENDPOINT)
                    .then()
                    .statusCode(200)
                    .log().all();

            System.out.println("Logout successful");
        } catch (Exception e) {
            handleException("Exception during logout", e);
        }
    }

    private static void handleException(String message, Exception e) {
        System.err.println(message);
        if (e != null) {
            e.printStackTrace();
        } else {
            System.err.println("Unknown exception occurred");
        }
        System.exit(1); // Exit with failure status
    }
}
