package org.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.restassured.response.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class Constants {

    public static final String BASE_URL = "https://qa-automation.armorcode.ai/public";
    public static final String BASE_URL_GET = "https://qa-automation.armorcode.ai";
    public static final String LOGIN_ENDPOINT = "/login";
    public static final String FEATURE_FLAG_ENDPOINT = "/api/super-admin/feature-flag";
    public static final String SESSION_COOKIE_NAME = "SESSION";
    public static final String CSRF_TOKEN_HEADER = "X-CSRF-Token";
    public static final String LOGOUT_ENDPOINT = "/logout";

    public static final Gson gson = new GsonBuilder().serializeNulls().create();

    public static List<Integer> parseTenantIds(String tenantIdsInput) {
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

    public static void validateSessionData(Map<String, String> cookies, String csrfToken, String sessionId) {
        assertThat("Cookies should not be null", cookies, is(notNullValue()));
        assertThat("Cookies should contain entries", cookies.size(), greaterThan(0));
        assertThat("CSRF Token should not be null", csrfToken, is(notNullValue()));
        assertThat("CSRF Token should not be empty", csrfToken, not(isEmptyString()));
        assertThat("Session ID should not be null", sessionId, is(notNullValue()));
        assertThat("Session ID should not be empty", sessionId, not(isEmptyString()));
    }

    public static void logError(String message, Response response) {
        if (response != null) {
            System.err.println(message);
            System.err.println("Status code: " + response.getStatusCode());
            System.err.println("Response body: " + response.getBody().asString());
        } else {
            System.err.println(message + ": Response is null");
        }
    }
    public static void handleException(String message, Exception e) {
        System.err.println(message);
        if (e != null) {
            e.printStackTrace();
        } else {
            System.err.println("Unknown exception occurred");
        }
        System.exit(1); // Exit with failure status
    }
}
