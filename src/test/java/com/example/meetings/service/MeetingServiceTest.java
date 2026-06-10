package com.example.meetings.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Captor;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import com.example.meetings.repository.MeetingParticipantRepository;
import com.example.meetings.repository.MeetingRepository;
import com.example.meetings.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingParticipantRepository participantRepository;
    @Mock private UserRepository userRepository;

    @Captor private ArgumentCaptor<Meeting> meetingCaptor;

    private MeetingService service;
    private User organizer;
    private User invitee;
    private Meeting meeting;

    @BeforeEach
    void setUp() {
        service = new MeetingService(meetingRepository, participantRepository, userRepository);
        organizer = new User("alice", "alice@example.com", "hash");
        invitee = new User("bob", "bob@example.com", "hash");
        meeting = new Meeting("Test", "Desc", Instant.now(), Instant.now().plusSeconds(3600), organizer);
        meeting.addParticipant(new MeetingParticipant(meeting, organizer, InviteStatus.ACCEPTED));
    }

    @Test
    void propose_shouldCreateMeetingWithOrganizerAcceptedAndInviteesPending() {
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(invitee));
        when(meetingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Meeting result = service.propose(organizer, "Team Sync", "Discuss sprint",
                Instant.now(), Instant.now().plusSeconds(3600), List.of("bob"));

        verify(meetingRepository).save(meetingCaptor.capture());
        Meeting saved = meetingCaptor.getValue();
        assertEquals("Team Sync", saved.getTitle());
        assertEquals(2, saved.getParticipants().size());
        assertTrue(saved.getParticipants().stream()
                .allMatch(p -> p.getUser().equals(organizer)
                        ? p.getStatus() == InviteStatus.ACCEPTED
                        : p.getStatus() == InviteStatus.PENDING));
    }

    @Test
    void propose_shouldThrowWhenEndNotAfterStart() {
        Instant start = Instant.now();
        Instant end = start.minusSeconds(1);
        assertThrows(IllegalArgumentException.class,
                () -> service.propose(organizer, "Bad", "", start, end, List.of()));
    }

    @Test
    void propose_shouldThrowWhenInviteeNotFound() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> service.propose(organizer, "Test", "", Instant.now(),
                        Instant.now().plusSeconds(3600), List.of("unknown")));
    }

    @Test
    void propose_shouldSkipDuplicateAndEmptyInvitees() {
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(invitee));
        when(meetingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Meeting result = service.propose(organizer, "Test", "", Instant.now(),
                Instant.now().plusSeconds(3600), List.of("bob", "bob", "", "  "));

        verify(meetingRepository).save(meetingCaptor.capture());
        assertEquals(2, meetingCaptor.getValue().getParticipants().size());
        verify(userRepository, times(1)).findByUsername("bob");
    }

    @Test
    void respond_shouldUpdateStatusToAccepted() {
        MeetingParticipant participant = new MeetingParticipant(meeting, invitee, InviteStatus.PENDING);
        when(participantRepository.findByMeetingIdAndUserId(1L, invitee.getId()))
                .thenReturn(Optional.of(participant));

        service.respond(1L, invitee, InviteStatus.ACCEPTED);

        assertEquals(InviteStatus.ACCEPTED, participant.getStatus());
        verify(participantRepository).findByMeetingIdAndUserId(1L, invitee.getId());
    }

    @Test
    void respond_shouldUpdateStatusToDeclined() {
        MeetingParticipant participant = new MeetingParticipant(meeting, invitee, InviteStatus.PENDING);
        when(participantRepository.findByMeetingIdAndUserId(1L, invitee.getId()))
                .thenReturn(Optional.of(participant));

        service.respond(1L, invitee, InviteStatus.DECLINED);

        assertEquals(InviteStatus.DECLINED, participant.getStatus());
    }

    @Test
    void respond_shouldThrowWhenNoInviteFound() {
        when(participantRepository.findByMeetingIdAndUserId(1L, invitee.getId()))
                .thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> service.respond(1L, invitee, InviteStatus.ACCEPTED));
    }

    @Test
    void respond_shouldThrowWhenStatusIsPending() {
        assertThrows(IllegalArgumentException.class,
                () -> service.respond(1L, invitee, InviteStatus.PENDING));
    }

    @Test
    void calendarFor_shouldReturnMeetingsFromRepository() {
        when(meetingRepository.findCalendarMeetings(organizer)).thenReturn(List.of(meeting));
        List<Meeting> result = service.calendarFor(organizer);
        assertEquals(1, result.size());
        assertSame(meeting, result.get(0));
    }

    @Test
    void pendingInvitesFor_shouldReturnPendingParticipants() {
        MeetingParticipant mp = new MeetingParticipant(meeting, invitee, InviteStatus.PENDING);
        when(participantRepository.findByUserAndStatus(invitee, InviteStatus.PENDING))
                .thenReturn(List.of(mp));
        List<MeetingParticipant> result = service.pendingInvitesFor(invitee);
        assertEquals(1, result.size());
        assertSame(mp, result.get(0));
    }

    @Test
    void copyFromDiscovered_shouldCreateMeetingWithAutoAcceptedParticipant() {
        DiscoveredEvent event = new DiscoveredEvent("Ticketmaster", "evt1", "Concert",
                "Great show", Instant.parse("2026-06-15T20:00:00Z"),
                Instant.parse("2026-06-15T23:00:00Z"), "https://example.com", "Arena");
        when(meetingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Meeting result = service.copyFromDiscovered(organizer, event);

        assertEquals("Concert", result.getTitle());
        assertEquals(Instant.parse("2026-06-15T20:00:00Z"), result.getStartTime());
        assertEquals(Instant.parse("2026-06-15T23:00:00Z"), result.getEndTime());
        assertEquals(1, result.getParticipants().size());
        MeetingParticipant p = result.getParticipants().iterator().next();
        assertEquals(InviteStatus.ACCEPTED, p.getStatus());
        assertEquals(organizer, p.getUser());
    }

    @Test
    void copyFromDiscovered_shouldDefaultEndToStartPlusTwoHours() {
        DiscoveredEvent event = new DiscoveredEvent("SeatGeek", "evt2", "Game",
                null, Instant.parse("2026-06-15T20:00:00Z"),
                null, null, null);
        when(meetingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Meeting result = service.copyFromDiscovered(organizer, event);

        assertEquals(Instant.parse("2026-06-15T20:00:00Z"), result.getStartTime());
        assertEquals(Instant.parse("2026-06-15T22:00:00Z"), result.getEndTime());
    }

    @Test
    void copyFromDiscovered_shouldIncludeDescriptionWithVenueAndSource() {
        DiscoveredEvent event = new DiscoveredEvent("TM", "evt3", "Show",
                "Fun event", Instant.parse("2026-06-15T20:00:00Z"),
                Instant.parse("2026-06-15T22:00:00Z"), "https://tm.com/evt3", "Stadium");
        when(meetingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Meeting result = service.copyFromDiscovered(organizer, event);
        String desc = result.getDescription();
        assertTrue(desc.contains("Fun event"));
        assertTrue(desc.contains("Venue: Stadium"));
        assertTrue(desc.contains("Source: TM"));
        assertTrue(desc.contains("https://tm.com/evt3"));
    }

    @Test
    void calendarForIcalToken_shouldReturnMeetingsForValidToken() {
        when(userRepository.findByIcalToken("valid-token")).thenReturn(Optional.of(organizer));
        when(meetingRepository.findCalendarMeetings(organizer)).thenReturn(List.of(meeting));
        List<Meeting> result = service.calendarForIcalToken("valid-token");
        assertEquals(1, result.size());
        assertSame(meeting, result.get(0));
    }

    @Test
    void calendarForIcalToken_shouldThrowForInvalidToken() {
        when(userRepository.findByIcalToken("bad-token")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> service.calendarForIcalToken("bad-token"));
    }
}
