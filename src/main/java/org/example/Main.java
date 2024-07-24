package org.example;

import com.google.gson.*;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

import java.util.*;

import static io.restassured.RestAssured.given;

public class Main {

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

        List<Integer> tenantIds = Constants.parseTenantIds(tenantIdsInput);
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
            Constants.logError("Login failed", response);
            System.exit(1); // Exit with failure status
        }

        Map<String, String> cookies = response.getCookies();
        String csrfToken = main.extractCsrfToken(response);
        String sessionId = SessionManager.extractSessionId(cookies);

        Constants.validateSessionData(cookies, csrfToken, sessionId);

        String jsonInput = main.fetchFeatureFlags(csrfToken, sessionId, flagName);
        if (!"N/A".equals(jsonInput)) {
            String updatedJson = jsonInput;
            if ("add".equalsIgnoreCase(function)) {
                updatedJson = main.addTenants(updatedJson, tenantIds);
            } else if ("remove".equalsIgnoreCase(function)) {
                updatedJson = main.removeTenants(updatedJson, tenantIds);
            } else {
                System.err.println("Unknown function: " + function);
                System.exit(1);
            }

            // Check if there are changes in the payload
            if (!jsonInput.equals(updatedJson)) {
                main.sendFeatureFlagPayload(updatedJson, csrfToken, sessionId, flagName);
            } else {
                System.out.println("No changes in the feature flag payload. Skipping API call.");
            }

            SessionManager.logout(sessionId);
        }
    }

    private Response loginAndGetResponse(String email, String password) {
        return SessionManager.login(email, password);
    }

    private String fetchFeatureFlags(String csrfToken, String sessionId, String flagName) {
        Response getResponse = null;
        try {
            getResponse = given()
                    .baseUri(Constants.BASE_URL_GET)
                    .cookie(Constants.SESSION_COOKIE_NAME, sessionId)
                    .header(Constants.CSRF_TOKEN_HEADER, csrfToken)
                    .when()
                    .get(Constants.FEATURE_FLAG_ENDPOINT)
                    .then()
                    .extract().response();

            if (getResponse.getStatusCode() != 200) {
                Constants.logError("Failed to fetch the feature flag details", getResponse);
                System.exit(1); // Exit with failure status
            }
        } catch (Exception e) {
            Constants.handleException("Exception during fetching feature flags", e);
        }

        return extractFeatureFlagDetails(getResponse, flagName);
    }

    public String addTenants(String jsonInput, List<Integer> tenantIds) {
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

    public String removeTenants(String jsonInput, List<Integer> tenantIds) {
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

    private JsonArray extractTenants(JsonObject jsonObject) {
        JsonElement tenantsElement = jsonObject.get("tenants");
        return tenantsElement != null && tenantsElement.isJsonArray() ? tenantsElement.getAsJsonArray() : new JsonArray();
    }

    public void sendFeatureFlagPayload(String jsonPayload, String csrfToken, String sessionId, String flagName) {
        try {
            Response response = given()
                    .baseUri(Constants.BASE_URL_GET)
                    .contentType(ContentType.JSON)
                    .cookie(Constants.SESSION_COOKIE_NAME, sessionId)
                    .header(Constants.CSRF_TOKEN_HEADER, csrfToken)
                    .body(jsonPayload)
                    .when()
                    .put(Constants.FEATURE_FLAG_ENDPOINT)
                    .then()
                    .extract().response();

            if (response.getStatusCode() != 200) {
                Constants.logError("Failed to send the feature flag payload", response);
                System.exit(1); // Exit with failure status
            } else {
                System.out.println("Successfully sent the feature flag payload for flag: " + flagName);
                System.out.println(jsonPayload);
            }
        } catch (Exception e) {
            Constants.handleException("Exception during sending feature flag payload", e);
        }
    }

    private String extractCsrfToken(Response response) {
        String token = response.getHeader(Constants.CSRF_TOKEN_HEADER);
        if (token == null) {
            try {
                token = response.jsonPath().getString("csrfToken");
            } catch (Exception e) {
                Constants.handleException("Failed to parse response body as JSON", e);
            }
        }
        return token;
    }

    private String extractFeatureFlagDetails(Response response, String flagName) {
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
}
