package com.example.meetings.controller;

import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import com.example.meetings.MeetingsApplication;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = MeetingsApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
@ActiveProfiles("test")
class RestApiIntegrationTest {

    @LocalServerPort
    private int port;

    private RestTemplate rest;
    private String sessionCookie;

    @BeforeEach
    void setUp() {
        rest = new RestTemplate(new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().followRedirects(Redirect.NEVER).build()));
        // Don't throw on 4xx/5xx — we assert on status codes manually
        rest.setErrorHandler(new ResponseErrorHandler() {
            @Override public boolean hasError(ClientHttpResponse response) { return false; }
            @Override public void handleError(ClientHttpResponse response) { }
        });
    }

    private String base() {
        return "http://localhost:" + port;
    }

    private void saveSession(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get("Set-Cookie");
        if (cookies == null || cookies.isEmpty()) return;
        Map<String, String> map = new LinkedHashMap<>();
        if (sessionCookie != null) {
            for (String part : sessionCookie.split(";\\s*")) {
                int eq = part.indexOf('=');
                if (eq > 0) map.put(part.substring(0, eq), part.substring(eq + 1));
            }
        }
        for (String c : cookies) {
            String[] parts = c.split(";")[0].split("=", 2);
            if (parts.length == 2) map.put(parts[0], parts[1]);
        }
        StringBuilder sb = new StringBuilder();
        for (var entry : map.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        sessionCookie = sb.toString();
    }

    private HttpHeaders headersWithCookie() {
        HttpHeaders headers = new HttpHeaders();
        if (sessionCookie != null) {
            headers.add("Cookie", sessionCookie);
        }
        return headers;
    }

    private ResponseEntity<String> get(String path) {
        ResponseEntity<String> res = rest.exchange(
                base() + path, HttpMethod.GET,
                new HttpEntity<>(headersWithCookie()), String.class);
        saveSession(res);
        return res;
    }

    private ResponseEntity<String> post(String path, MultiValueMap<String, String> body) {
        HttpHeaders headers = headersWithCookie();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<String> res = rest.exchange(
                base() + path, HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        saveSession(res);
        return res;
    }

    private String extractCsrfToken(String html) {
        Matcher m = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(html);
        assertTrue(m.find(), "CSRF token not found in page response");
        return m.group(1);
    }

    private String extractIcalToken(String html) {
        String url = "/ical/";
        int idx = html.indexOf(url);
        if (idx < 0) return null;
        int start = idx + url.length();
        int end = html.indexOf(".ics", start);
        return (end < 0) ? null : html.substring(start, end);
    }

    // ─── Auth endpoints ──────────────────────────────────────────────

    @Test @Order(1)
    void loginPage_shouldReturnOk() {
        ResponseEntity<String> res = get("/login");
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertTrue(res.getBody().contains("login"));
    }

    @Test @Order(2)
    void registerPage_shouldReturnOk() {
        ResponseEntity<String> res = get("/register");
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertTrue(res.getBody().contains("register"));
    }

    @Test @Order(3)
    void registerUser_shouldSucceedAndRedirectToLogin() {
        ResponseEntity<String> regPage = get("/register");
        String csrf = extractCsrfToken(regPage.getBody());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("_csrf", csrf);
        body.add("username", "alice");
        body.add("email", "alice@test.com");
        body.add("password", "secret");

        ResponseEntity<String> res = post("/register", body);

        assertEquals(HttpStatus.FOUND, res.getStatusCode(),
                "POST /register returned 200. Body:\n" + res.getBody());
        assertTrue(res.getHeaders().getLocation().toString().contains("/login?registered"),
                "Should redirect to /login?registered");
    }

    @Test @Order(4)
    void registerUser_shouldRejectDuplicateUsername() {
        ResponseEntity<String> regPage = get("/register");
        String csrf = extractCsrfToken(regPage.getBody());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("_csrf", csrf);
        body.add("username", "alice");
        body.add("email", "alice2@test.com");
        body.add("password", "secret");

        ResponseEntity<String> res = post("/register", body);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertTrue(res.getBody().contains("Username already taken"),
                "Should show error for duplicate username");
    }

    @Test @Order(5)
    void root_shouldRedirectToCalendar() {
        ResponseEntity<String> res = get("/");
        assertEquals(HttpStatus.FOUND, res.getStatusCode());
        assertTrue(res.getHeaders().getLocation().toString().contains("/calendar"),
                "Should redirect to /calendar");
    }

    // ─── Login and Calendar ──────────────────────────────────────────

    @Test @Order(6)
    void login_shouldSucceedAndRedirectToCalendar() {
        ResponseEntity<String> loginPage = get("/login");
        String csrf = extractCsrfToken(loginPage.getBody());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("_csrf", csrf);
        body.add("username", "alice");
        body.add("password", "secret");

        ResponseEntity<String> res = post("/login", body);

        assertEquals(HttpStatus.FOUND, res.getStatusCode());
        assertTrue(res.getHeaders().getLocation().toString().contains("/calendar"),
                "Should redirect to /calendar after login");
        assertNotNull(sessionCookie, "Session cookie should be set after login");
    }

    @Test @Order(7)
    void calendar_shouldReturnOk_whenAuthenticated() {
        ResponseEntity<String> res = get("/calendar");
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertTrue(res.getBody().contains("alice"),
                "Calendar page should display authenticated user");
    }

    @Test @Order(8)
    void calendar_shouldRedirect_whenUnauthenticated() {
        // Fresh request without any session cookie
        ResponseEntity<String> res = rest.getForEntity(base() + "/calendar", String.class);
        assertEquals(HttpStatus.FOUND, res.getStatusCode());
        assertTrue(res.getHeaders().getLocation().toString().contains("login"),
                "Should redirect to login when not authenticated");
    }

    // ─── Meeting endpoints ───────────────────────────────────────────

    @Test @Order(9)
    void proposeForm_shouldReturnOk_whenAuthenticated() {
        ResponseEntity<String> res = get("/meetings/new");
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertTrue(res.getBody().contains("propose"),
                "Propose form should be accessible when authenticated");
    }

    @Test @Order(10)
    void proposeMeeting_shouldSucceedAndRedirect() {
        ResponseEntity<String> formPage = get("/meetings/new");
        String csrf = extractCsrfToken(formPage.getBody());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("_csrf", csrf);
        body.add("title", "Sprint Review");
        body.add("description", "Review sprint results");
        body.add("start", "2026-07-15T10:00");
        body.add("end", "2026-07-15T11:00");

        ResponseEntity<String> res = post("/meetings/new", body);

        assertEquals(HttpStatus.FOUND, res.getStatusCode());
        assertTrue(res.getHeaders().getLocation().toString().contains("/calendar"),
                "Should redirect to /calendar after proposing");
    }

    @Test @Order(11)
    void proposeMeeting_shouldRejectInvalidDates() {
        ResponseEntity<String> formPage = get("/meetings/new");
        String csrf = extractCsrfToken(formPage.getBody());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("_csrf", csrf);
        body.add("title", "Bad Meeting");
        body.add("start", "2026-07-15T11:00");
        body.add("end", "2026-07-15T10:00");

        ResponseEntity<String> res = post("/meetings/new", body);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertTrue(res.getBody().contains("End time must be after start time"),
                "Should show validation error for invalid dates");
    }

    @Test @Order(12)
    void respondToInvite_shouldSucceed() {
        // Fetch the calendar page to obtain a CSRF token for the current session
        ResponseEntity<String> calPage = get("/calendar");
        String csrf = extractCsrfToken(calPage.getBody());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("_csrf", csrf);
        body.add("action", "accept");

        ResponseEntity<String> res = post("/meetings/1/respond", body);

        assertEquals(HttpStatus.FOUND, res.getStatusCode());
        assertTrue(res.getHeaders().getLocation().toString().contains("/calendar"),
                "Should redirect to /calendar after responding");
    }

    @Test @Order(13)
    void respondToInvite_shouldHandleUnknownMeeting() {
        ResponseEntity<String> calPage = get("/calendar");
        String csrf = extractCsrfToken(calPage.getBody());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("_csrf", csrf);
        body.add("action", "accept");

        ResponseEntity<String> res = post("/meetings/999/respond", body);

        assertEquals(HttpStatus.FOUND, res.getStatusCode());
        assertTrue(res.getHeaders().getLocation().toString().contains("/calendar"),
                "Should redirect to /calendar even when meeting doesn't exist");
    }

    // ─── iCal endpoint ───────────────────────────────────────────────

    @Test @Order(14)
    void icalFeed_shouldReturnCalendar_whenTokenIsValid() {
        ResponseEntity<String> calRes = get("/calendar");
        String icalToken = extractIcalToken(calRes.getBody());

        assertNotNull(icalToken, "Should find iCal token in calendar page");

        ResponseEntity<String> res = get("/ical/" + icalToken + ".ics");
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertTrue(res.getHeaders().getContentType().toString().contains("text/calendar"),
                "Content-Type should be text/calendar");
        assertTrue(res.getBody().contains("BEGIN:VCALENDAR"),
                "iCal response should be a valid calendar");
        assertTrue(res.getBody().contains("Sprint Review"),
                "iCal feed should contain the Sprint Review meeting");
    }

    @Test @Order(15)
    void icalFeed_shouldReturn404_whenTokenIsInvalid() {
        ResponseEntity<String> res = get("/ical/invalid-token.ics");
        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
    }

    // ─── Discovery endpoint ──────────────────────────────────────────

    @Test @Order(16)
    void discoverPage_shouldReturnOk_whenAuthenticated() {
        ResponseEntity<String> res = get("/discover");
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertTrue(res.getBody().contains("discover"),
                "Discover page should be accessible when authenticated");
    }
}
