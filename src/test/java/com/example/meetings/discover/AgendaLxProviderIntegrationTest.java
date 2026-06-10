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

class AgendaLxProviderIntegrationTest {

    @RegisterExtension
    static WireMockExtension wiremock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private AgendaLxProvider provider;

    private String fixture(String path) throws IOException {
        return Files.readString(Paths.get("src/test/resources/fixtures/agendalx/" + path));
    }

    @BeforeEach
    void setUp() {
        RestClient http = RestClient.builder()
                .baseUrl(wiremock.baseUrl())
                .defaultHeader("User-Agent", "test-agent")
                .build();
        provider = new AgendaLxProvider(http);
    }

    @Test
    void search_shouldReturnParsedEvents() throws Exception {
        wiremock.stubFor(get(urlPathEqualTo("/events"))
                .withQueryParam("search", equalTo("jazz"))
                .withQueryParam("per_page", equalTo("20"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(fixture("events-success.json"))));

        List<DiscoveredEvent> results = provider.search("jazz");

        assertEquals(2, results.size());

        DiscoveredEvent first = results.get(0);
        assertEquals("Agenda Cultural de Lisboa", first.source());
        assertEquals("1001", first.externalId());
        assertEquals("Concerto de Jazz no CCB", first.title());
        assertTrue(first.description().contains("concerto imperdível"));
        assertEquals("2026-07-22T20:30:00Z", first.start().toString());
        assertNull(first.end());
        assertEquals("https://www.agendalx.pt/events/1001", first.url());
        assertEquals("Centro Cultural de Belém", first.venue());

        DiscoveredEvent second = results.get(1);
        assertEquals("Teatro: A Peça", second.title());
        assertEquals("2026-07-23T19:00:00Z", second.start().toString());
        assertEquals("Teatro Nacional D. Maria II", second.venue());
    }

    @Test
    void search_shouldReturnEmptyOnHttpError() {
        wiremock.stubFor(get(urlPathEqualTo("/events"))
                .withQueryParam("search", equalTo("fail"))
                .willReturn(aResponse().withStatus(500)));

        assertTrue(provider.search("fail").isEmpty());
    }

    @Test
    void search_shouldReturnEmptyOnNullBody() {
        wiremock.stubFor(get(urlPathEqualTo("/events"))
                .withQueryParam("search", equalTo("null"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("null")));

        assertTrue(provider.search("null").isEmpty());
    }

    @Test
    void search_shouldReturnEmptyOnMalformedJson() {
        wiremock.stubFor(get(urlPathEqualTo("/events"))
                .withQueryParam("search", equalTo("bad"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{bad}")));

        assertTrue(provider.search("bad").isEmpty());
    }

    @Test
    void search_shouldReturnEmptyForEmptyArray() throws Exception {
        wiremock.stubFor(get(urlPathEqualTo("/events"))
                .withQueryParam("search", equalTo("none"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(fixture("events-empty.json"))));

        assertTrue(provider.search("none").isEmpty());
    }
}
