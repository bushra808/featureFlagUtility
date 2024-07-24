package org.example;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

import java.util.Map;

import static io.restassured.RestAssured.given;

public class SessionManager {

    public static Response login(String email, String password) {
        String requestBody = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
        Response response = null;
        try {
            response = given()
                    .baseUri(Constants.BASE_URL)
                    .contentType(ContentType.JSON)
                    .body(requestBody)
                    .when()
                    .post(Constants.LOGIN_ENDPOINT)
                    .then()
                    .log().all()
                    .extract().response();

            logResponseDetails("Login response", response);
        } catch (Exception e) {
            handleException("Exception during login", e);
        }
        return response;
    }

    public static void logout(String sessionId) {
        try {
            given()
                    .baseUri(Constants.BASE_URL_GET)
                    .cookie(Constants.SESSION_COOKIE_NAME, sessionId)
                    .when()
                    .get(Constants.LOGOUT_ENDPOINT)
                    .then()
                    .statusCode(200)
                    .log().all();

            System.out.println("Logout successful");
        } catch (Exception e) {
            handleException("Exception during logout", e);
        }
    }

    public static String extractSessionId(Map<String, String> cookies) {
        return cookies.get(Constants.SESSION_COOKIE_NAME);
    }

    private static void logResponseDetails(String message, Response response) {
        System.out.println(message);
        System.out.println("Status code: " + response.getStatusCode());
        System.out.println("Response body: " + response.getBody().asString());
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
