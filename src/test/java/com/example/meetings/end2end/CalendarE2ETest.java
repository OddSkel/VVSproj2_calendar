package com.example.meetings.end2end;

import com.example.meetings.MeetingsApplication;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = MeetingsApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
@ActiveProfiles("test")
class CalendarE2ETest {

    @LocalServerPort
    private int port;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
    }

    private WebDriver startDriver() {
        return new HtmlUnitDriver();
    }

    private void register(WebDriver driver, String username, String email, String password) {
        driver.get(baseUrl + "/register");
        driver.findElement(By.id("username")).sendKeys(username);
        driver.findElement(By.id("email")).sendKeys(email);
        driver.findElement(By.id("password")).sendKeys(password);
        driver.findElement(By.cssSelector("button[type='submit']")).click();
    }

    private void login(WebDriver driver, String username, String password) {
        driver.get(baseUrl + "/login");
        driver.findElement(By.id("username")).sendKeys(username);
        driver.findElement(By.id("password")).sendKeys(password);
        driver.findElement(By.cssSelector("button[type='submit']")).click();
    }

    @Test @Order(1)
    void registerAndLogin_shouldRedirectToCalendar() {
        WebDriver driver = startDriver();
        try {
            register(driver, "alice", "alice@test.com", "secret");
            assertTrue(driver.getCurrentUrl().contains("/login?registered"));

            login(driver, "alice", "secret");
            assertTrue(driver.getCurrentUrl().contains("/calendar"));
            assertTrue(driver.getPageSource().contains("No meetings yet"));
        } finally {
            driver.quit();
        }
    }

    @Test @Order(2)
    void proposeMeeting_shouldAppearOnCalendar() {
        WebDriver driver = startDriver();
        try {
            register(driver, "bob", "bob@test.com", "secret");
            login(driver, "bob", "secret");

            driver.get(baseUrl + "/meetings/new");
            driver.findElement(By.id("title")).sendKeys("Sprint Review");
            driver.findElement(By.id("description")).sendKeys("Review sprint goals");
            driver.findElement(By.id("start")).sendKeys("2026-07-20T10:00");
            driver.findElement(By.id("end")).sendKeys("2026-07-20T11:00");
            driver.findElement(By.id("invitees")).sendKeys("alice");
            driver.findElement(By.cssSelector("button[type='submit']")).click();

            assertTrue(driver.getCurrentUrl().contains("/calendar"));
            assertTrue(driver.getPageSource().contains("Sprint Review"));
        } finally {
            driver.quit();
        }
    }

    @Test @Order(3)
    void pendingInvite_shouldAppearForInvitee() {
        WebDriver driver = startDriver();
        try {
            login(driver, "alice", "secret");
            assertTrue(driver.getCurrentUrl().contains("/calendar"));
            assertTrue(driver.getPageSource().contains("Pending invites"));
            assertTrue(driver.getPageSource().contains("Sprint Review"));
        } finally {
            driver.quit();
        }
    }

    @Test @Order(4)
    void acceptInvite_shouldMarkMeetingConfirmed() {
        WebDriver driver = startDriver();
        try {
            login(driver, "alice", "secret");
            driver.findElement(By.cssSelector("form[action*='/respond'] button[type='submit']")).click();

            assertTrue(driver.getPageSource().contains("confirmed"));
            assertTrue(driver.getPageSource().contains("Sprint Review"));
        } finally {
            driver.quit();
        }
    }

    @Test @Order(5)
    void iCalLink_shouldBePresentOnCalendar() {
        WebDriver driver = startDriver();
        try {
            login(driver, "alice", "secret");
            String source = driver.getPageSource();
            assertTrue(source.contains("webcal://") || source.contains("ical"));
            assertTrue(driver.findElement(By.cssSelector("code.url")) != null);
        } finally {
            driver.quit();
        }
    }
}
