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

class TicketmasterProviderIntegrationTest {

    @RegisterExtension
    static WireMockExtension wiremock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private TicketmasterProvider provider;

    private String fixture(String path) throws IOException {
        return Files.readString(Paths.get("src/test/resources/fixtures/ticketmaster/" + path));
    }

    @BeforeEach
    void setUp() {
        RestClient http = RestClient.builder()
                .baseUrl(wiremock.baseUrl())
                .build();
        provider = new TicketmasterProvider("test-api-key", "PT", http);
    }

    @Test
    void search_shouldReturnParsedEvents() throws Exception {
        wiremock.stubFor(get(urlPathEqualTo("/events.json"))
                .withQueryParam("keyword", equalTo("rock"))
                .withQueryParam("size", equalTo("20"))
                .withQueryParam("apikey", equalTo("test-api-key"))
                .withQueryParam("countryCode", equalTo("PT"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(fixture("events-success.json"))));

        List<DiscoveredEvent> results = provider.search("rock");

        assertEquals(2, results.size());

        DiscoveredEvent first = results.get(0);
        assertEquals("Ticketmaster", first.source());
        assertEquals("Z7r9jZ1AdOk9q", first.externalId());
        assertEquals("Rock Concert Lisbon", first.title());
        assertEquals("An amazing rock concert in Lisbon", first.description());
        assertEquals("2026-07-15T21:00:00Z", first.start().toString());
        assertNull(first.end());
        assertEquals("https://www.ticketmaster.pt/rock-concert-lisbon/event/Z7r9jZ1AdOk9q", first.url());
        assertEquals("Altice Arena", first.venue());

        DiscoveredEvent second = results.get(1);
        assertEquals("Jazz Night", second.title());
        assertNull(second.description());
        assertEquals("Teatro São Luiz", second.venue());
    }

    @Test
    void search_shouldReturnEmptyWhenNotConfigured() {
        TicketmasterProvider unconfigured = new TicketmasterProvider("", "PT",
                RestClient.builder().baseUrl(wiremock.baseUrl()).build());
        assertTrue(unconfigured.search("rock").isEmpty());
    }

    @Test
    void search_shouldReturnEmptyOnHttpError() {
        wiremock.stubFor(get(urlPathEqualTo("/events.json"))
                .withQueryParam("keyword", equalTo("fail"))
                .willReturn(aResponse().withStatus(500)));

        List<DiscoveredEvent> results = provider.search("fail");
        assertTrue(results.isEmpty());
    }

    @Test
    void search_shouldReturnEmptyOnNullBody() {
        wiremock.stubFor(get(urlPathEqualTo("/events.json"))
                .withQueryParam("keyword", equalTo("null"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("null")));

        assertTrue(provider.search("null").isEmpty());
    }

    @Test
    void search_shouldReturnEmptyOnMalformedJson() {
        wiremock.stubFor(get(urlPathEqualTo("/events.json"))
                .withQueryParam("keyword", equalTo("bad"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{broken json}")));

        assertTrue(provider.search("bad").isEmpty());
    }

    @Test
    void search_shouldSkipEventsWithoutStartDate() throws Exception {
        wiremock.stubFor(get(urlPathEqualTo("/events.json"))
                .withQueryParam("keyword", equalTo("noshow"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(fixture("events-no-date.json"))));

        List<DiscoveredEvent> results = provider.search("noshow");

        assertEquals(1, results.size());
        assertEquals("Valid Show", results.get(0).title());
        assertNull(results.get(0).venue());
    }

    @Test
    void search_shouldReturnEmptyForEmptyResponse() throws Exception {
        wiremock.stubFor(get(urlPathEqualTo("/events.json"))
                .withQueryParam("keyword", equalTo("none"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(fixture("events-empty.json"))));

        assertTrue(provider.search("none").isEmpty());
    }
}
