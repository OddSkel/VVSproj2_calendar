package com.example.meetings.service;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;

class ICalServiceTest {

    private ICalService service;
    private User owner;
    private Meeting confirmedMeeting;
    private Meeting tentativeMeeting;

    @BeforeEach
    void setUp() {
        service = new ICalService();
        owner = new User("alice", "alice@example.com", "hash");

        User bob = new User("bob", "bob@example.com", "hash");

        confirmedMeeting = new Meeting("Team Sync", "Weekly sync",
                Instant.parse("2026-06-15T10:00:00Z"),
                Instant.parse("2026-06-15T11:00:00Z"), owner);
        confirmedMeeting.addParticipant(new MeetingParticipant(confirmedMeeting, bob, InviteStatus.ACCEPTED));

        tentativeMeeting = new Meeting("Design Review", "Review mockups",
                Instant.parse("2026-06-16T14:00:00Z"),
                Instant.parse("2026-06-16T15:00:00Z"), owner);
        tentativeMeeting.addParticipant(new MeetingParticipant(tentativeMeeting, bob, InviteStatus.PENDING));
    }

    @Test
    void render_shouldProduceValidVCalendar() {
        String result = service.render(owner, List.of(confirmedMeeting));
        assertTrue(result.startsWith("BEGIN:VCALENDAR\r\n"));
        assertTrue(result.contains("VERSION:2.0\r\n"));
        assertTrue(result.contains("PRODID:-//meetings-app//EN\r\n"));
        assertTrue(result.contains("END:VCALENDAR\r\n"));
    }

    @Test
    void render_shouldIncludeCalendarName() {
        String result = service.render(owner, List.of(confirmedMeeting));
        assertTrue(result.contains("X-WR-CALNAME:alice's meetings\r\n"));
    }

    @Test
    void render_shouldOutputConfirmedStatusWhenAllAccepted() {
        String result = service.render(owner, List.of(confirmedMeeting));
        assertTrue(result.contains("STATUS:CONFIRMED\r\n"));
    }

    @Test
    void render_shouldOutputTentativeWhenSomePending() {
        String result = service.render(owner, List.of(tentativeMeeting));
        assertTrue(result.contains("STATUS:TENTATIVE\r\n"));
    }

    @Test
    void render_shouldIncludeEventDetails() {
        String result = service.render(owner, List.of(confirmedMeeting));
        assertTrue(result.contains("DTSTART:20260615T100000Z\r\n"));
        assertTrue(result.contains("DTEND:20260615T110000Z\r\n"));
        assertTrue(result.contains("SUMMARY:Team Sync\r\n"));
        assertTrue(result.contains("DESCRIPTION:Weekly sync\r\n"));
    }

    @Test
    void render_shouldIncludeOrganizer() {
        String result = service.render(owner, List.of(confirmedMeeting));
        assertTrue(result.contains("ORGANIZER;CN=alice:mailto:alice@example.com\r\n"));
    }

    @Test
    void render_shouldIncludeAttendeesWithPartStat() {
        String result = service.render(owner, List.of(confirmedMeeting));
        assertTrue(result.contains("ATTENDEE;CN=bob;PARTSTAT=ACCEPTED:mailto:bob@example.com\r\n"));
    }

    @Test
    void render_shouldEscapeSpecialCharacters() {
        Meeting special = new Meeting("Test;Comma,Newline\nBack\\slash",
                "Desc", Instant.parse("2026-06-15T10:00:00Z"),
                Instant.parse("2026-06-15T11:00:00Z"), owner);
        special.addParticipant(new MeetingParticipant(special, owner, InviteStatus.ACCEPTED));

        String result = service.render(owner, List.of(special));
        assertTrue(result.contains("SUMMARY:Test\\;Comma\\,Newline\\nBack\\\\slash\r\n"));
    }

    @Test
    void render_shouldUseNeedsActionForPendingAttendees() {
        String result = service.render(owner, List.of(tentativeMeeting));
        assertTrue(result.contains("PARTSTAT=NEEDS-ACTION"));
    }
}
