package com.example.meetings.discover;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

class DiscoveryServiceIntegrationTest {

    @RegisterExtension
    static WireMockExtension wiremock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Test
    void search_shouldAggregateResultsFromMultipleProviders() {
        RestClient http = RestClient.builder().baseUrl(wiremock.baseUrl()).build();
        EventProvider configured = new EventProvider() {
            @Override public String name() { return "MockA"; }
            @Override public boolean isConfigured() { return true; }
            @Override public List<DiscoveredEvent> search(String query) {
                return List.of(
                        new DiscoveredEvent("MockA", "1", "Event A", null,
                                Instant.parse("2026-07-01T10:00:00Z"), null, "https://a.com", null),
                        new DiscoveredEvent("MockA", "2", "Event B", null,
                                Instant.parse("2026-07-02T10:00:00Z"), null, "https://b.com", null));
            }
        };
        EventProvider unconfigured = new EventProvider() {
            @Override public String name() { return "MockB"; }
            @Override public boolean isConfigured() { return false; }
            @Override public List<DiscoveredEvent> search(String query) { return List.of(); }
        };
        EventProvider providerB = new EventProvider() {
            @Override public String name() { return "MockC"; }
            @Override public boolean isConfigured() { return true; }
            @Override public List<DiscoveredEvent> search(String query) {
                return List.of(
                        new DiscoveredEvent("MockC", "3", "Event C", null,
                                Instant.parse("2026-07-01T12:00:00Z"), null, "https://c.com", null));
            }
        };

        DiscoveryService service = new DiscoveryService(List.of(configured, unconfigured, providerB));

        List<DiscoveredEvent> results = service.search("test");

        assertEquals(3, results.size());
        assertTrue(results.stream().anyMatch(e -> e.source().equals("MockA")));
        assertTrue(results.stream().anyMatch(e -> e.source().equals("MockC")));
    }

    @Test
    void search_shouldSortByStartTime() {
        EventProvider provider = new EventProvider() {
            @Override public String name() { return "Test"; }
            @Override public boolean isConfigured() { return true; }
            @Override public List<DiscoveredEvent> search(String query) {
                return List.of(
                        new DiscoveredEvent("Test", "1", "Late", null,
                                Instant.parse("2026-07-03T10:00:00Z"), null, null, null),
                        new DiscoveredEvent("Test", "2", "Early", null,
                                Instant.parse("2026-07-01T10:00:00Z"), null, null, null),
                        new DiscoveredEvent("Test", "3", "Middle", null,
                                Instant.parse("2026-07-02T10:00:00Z"), null, null, null));
            }
        };

        DiscoveryService service = new DiscoveryService(List.of(provider));
        List<DiscoveredEvent> results = service.search("test");

        assertEquals(3, results.size());
        assertEquals("Early", results.get(0).title());
        assertEquals("Middle", results.get(1).title());
        assertEquals("Late", results.get(2).title());
    }

    @Test
    void search_shouldDeduplicateByUrl() {
        EventProvider provider = new EventProvider() {
            @Override public String name() { return "Test"; }
            @Override public boolean isConfigured() { return true; }
            @Override public List<DiscoveredEvent> search(String query) {
                return List.of(
                        new DiscoveredEvent("Test", "1", "Event X", null,
                                Instant.parse("2026-07-01T10:00:00Z"), null, "https://same.url", null),
                        new DiscoveredEvent("Test", "2", "Event X (duplicate)", null,
                                Instant.parse("2026-07-01T10:00:00Z"), null, "https://same.url", null));
            }
        };

        DiscoveryService service = new DiscoveryService(List.of(provider));
        List<DiscoveredEvent> results = service.search("test");

        assertEquals(1, results.size());
    }

    @Test
    void search_shouldReturnEmptyForBlankQuery() {
        DiscoveryService service = new DiscoveryService(List.of());
        assertTrue(service.search("").isEmpty());
        assertTrue(service.search("  ").isEmpty());
        assertTrue(service.search(null).isEmpty());
    }

    @Test
    void search_shouldDeduplicateBySourceAndIdWhenUrlIsNull() {
        EventProvider provider = new EventProvider() {
            @Override public String name() { return "Test"; }
            @Override public boolean isConfigured() { return true; }
            @Override public List<DiscoveredEvent> search(String query) {
                return List.of(
                        new DiscoveredEvent("Test", "same-id", "Event A", null,
                                Instant.parse("2026-07-01T10:00:00Z"), null, null, null),
                        new DiscoveredEvent("Test", "same-id", "Event B (dup)", null,
                                Instant.parse("2026-07-01T10:00:00Z"), null, null, null));
            }
        };

        DiscoveryService service = new DiscoveryService(List.of(provider));
        List<DiscoveredEvent> results = service.search("test");

        assertEquals(1, results.size());
    }
}
