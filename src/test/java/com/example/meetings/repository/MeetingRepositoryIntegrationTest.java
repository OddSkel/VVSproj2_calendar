package com.example.meetings.repository;

import com.example.meetings.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class MeetingRepositoryIntegrationTest {

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private UserRepository userRepository;

    private User organizer;
    private User invitee;
    private Meeting meeting;

    @BeforeEach
    void setUp() {
        organizer = userRepository.save(new User("alice", "alice@test.com", "hash"));
        invitee = userRepository.save(new User("bob", "bob@test.com", "hash"));

        meeting = new Meeting("Sprint Review", "Review sprint results",
                Instant.parse("2026-07-15T10:00:00Z"),
                Instant.parse("2026-07-15T11:00:00Z"), organizer);
        meeting.addParticipant(new MeetingParticipant(meeting, organizer, InviteStatus.ACCEPTED));
        meeting.addParticipant(new MeetingParticipant(meeting, invitee, InviteStatus.PENDING));
        meeting = meetingRepository.save(meeting);
    }

    @Test
    void findCalendarMeetings_shouldReturnMeetingsWhereUserIsOrganizer() {
        List<Meeting> result = meetingRepository.findCalendarMeetings(organizer);
        assertEquals(1, result.size());
        assertEquals("Sprint Review", result.get(0).getTitle());
    }

    @Test
    void findCalendarMeetings_shouldReturnMeetingsWhereUserIsNonDeclinedParticipant() {
        List<Meeting> result = meetingRepository.findCalendarMeetings(invitee);
        assertEquals(1, result.size());
        assertEquals("Sprint Review", result.get(0).getTitle());
    }

    @Test
    void findCalendarMeetings_shouldExcludeMeetingsWhereUserDeclined() {
        User decliner = userRepository.save(new User("charlie", "charlie@test.com", "hash"));
        meeting.addParticipant(new MeetingParticipant(meeting, decliner, InviteStatus.DECLINED));
        meetingRepository.save(meeting);

        List<Meeting> result = meetingRepository.findCalendarMeetings(decliner);
        assertTrue(result.isEmpty());
    }

    @Test
    void findCalendarMeetings_shouldOrderByStartTime() {
        Meeting earlier = new Meeting("Earlier", "Desc",
                Instant.parse("2026-07-14T10:00:00Z"),
                Instant.parse("2026-07-14T11:00:00Z"), organizer);
        earlier.addParticipant(new MeetingParticipant(earlier, organizer, InviteStatus.ACCEPTED));
        meetingRepository.save(earlier);

        List<Meeting> result = meetingRepository.findCalendarMeetings(organizer);
        assertEquals(2, result.size());
        assertTrue(result.get(0).getStartTime().isBefore(result.get(1).getStartTime()));
    }

    @Test
    void findCalendarMeetings_shouldReturnEmpty_whenNoMeetings() {
        User lonely = userRepository.save(new User("lonely", "lonely@test.com", "hash"));
        List<Meeting> result = meetingRepository.findCalendarMeetings(lonely);
        assertTrue(result.isEmpty());
    }

    @Test
    void findOverlapping_shouldDetectOverlap() {
        Instant searchStart = Instant.parse("2026-07-15T09:00:00Z");
        Instant searchEnd = Instant.parse("2026-07-15T12:00:00Z");

        List<Meeting> result = meetingRepository.findOverlapping(organizer, searchStart, searchEnd);
        assertEquals(1, result.size());
    }

    @Test
    void findOverlapping_shouldNotReturnNonOverlappingMeetings() {
        Instant searchStart = Instant.parse("2026-07-16T10:00:00Z");
        Instant searchEnd = Instant.parse("2026-07-16T11:00:00Z");

        List<Meeting> result = meetingRepository.findOverlapping(organizer, searchStart, searchEnd);
        assertTrue(result.isEmpty());
    }

    @Test
    void findOverlapping_shouldExcludeDeclinedParticipantMeetings() {
        User decliner = userRepository.save(new User("dave", "dave@test.com", "hash"));
        meeting.addParticipant(new MeetingParticipant(meeting, decliner, InviteStatus.DECLINED));
        meetingRepository.save(meeting);

        Instant searchStart = Instant.parse("2026-07-15T09:00:00Z");
        Instant searchEnd = Instant.parse("2026-07-15T12:00:00Z");

        List<Meeting> result = meetingRepository.findOverlapping(decliner, searchStart, searchEnd);
        assertTrue(result.isEmpty());
    }

    @Test
    void findOverlapping_shouldDetectExactBoundary() {
        Instant searchStart = Instant.parse("2026-07-15T10:00:00Z");
        Instant searchEnd = Instant.parse("2026-07-15T11:00:00Z");

        List<Meeting> result = meetingRepository.findOverlapping(organizer, searchStart, searchEnd);
        assertEquals(1, result.size());
    }
}
