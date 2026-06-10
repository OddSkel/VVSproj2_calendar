package com.example.meetings.discover;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

class SeatGeekProviderIntegrationTest {

    @RegisterExtension
    static WireMockExtension wiremock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private SeatGeekProvider provider;

    private String fixture(String path) throws IOException {
        return Files.readString(Paths.get("src/test/resources/fixtures/seatgeek/" + path));
    }

    @BeforeEach
    void setUp() {
        RestClient http = RestClient.builder()
                .baseUrl(wiremock.baseUrl())
                .build();
        provider = new SeatGeekProvider("test-client-id", http);
    }

    @Test
    void search_shouldReturnParsedEvents() throws Exception {
        wiremock.stubFor(get(urlPathEqualTo("/events"))
                .withQueryParam("q", equalTo("football"))
                .withQueryParam("per_page", equalTo("20"))
                .withQueryParam("client_id", equalTo("test-client-id"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(fixture("events-success.json"))));

        List<DiscoveredEvent> results = provider.search("football");

        assertEquals(2, results.size());

        DiscoveredEvent first = results.get(0);
        assertEquals("SeatGeek", first.source());
        assertEquals("45678", first.externalId());
        assertEquals("FC Porto vs Benfica", first.title());
        assertEquals("Primeira Liga match", first.description());
        assertEquals("2026-08-20T19:45:00Z", first.start().toString());
        assertNull(first.end());
        assertEquals("https://seatgeek.com/45678", first.url());
        assertEquals("Estádio do Dragão", first.venue());

        DiscoveredEvent second = results.get(1);
        assertEquals("Music Festival", second.title());
        assertNull(second.venue());
    }

    @Test
    void search_shouldReturnEmptyWhenNotConfigured() {
        SeatGeekProvider unconfigured = new SeatGeekProvider("",
                RestClient.builder().baseUrl(wiremock.baseUrl()).build());
        assertTrue(unconfigured.search("test").isEmpty());
    }

    @Test
    void search_shouldReturnEmptyOnHttpError() {
        wiremock.stubFor(get(urlPathEqualTo("/events"))
                .withQueryParam("q", equalTo("fail"))
                .willReturn(aResponse().withStatus(500)));

        assertTrue(provider.search("fail").isEmpty());
    }

    @Test
    void search_shouldReturnEmptyOnNullBody() {
        wiremock.stubFor(get(urlPathEqualTo("/events"))
                .withQueryParam("q", equalTo("null"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("null")));

        assertTrue(provider.search("null").isEmpty());
    }

    @Test
    void search_shouldReturnEmptyOnMalformedJson() {
        wiremock.stubFor(get(urlPathEqualTo("/events"))
                .withQueryParam("q", equalTo("bad"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{bad}")));

        assertTrue(provider.search("bad").isEmpty());
    }

    @Test
    void search_shouldReturnEmptyForEmptyResponse() throws Exception {
        wiremock.stubFor(get(urlPathEqualTo("/events"))
                .withQueryParam("q", equalTo("none"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(fixture("events-empty.json"))));

        assertTrue(provider.search("none").isEmpty());
    }
}
