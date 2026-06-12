package com.example.meetings.repository;

import com.example.meetings.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class MeetingParticipantRepositoryIntegrationTest {

    @Autowired
    private MeetingParticipantRepository participantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    private User organizer;
    private User invitee;
    private Meeting meeting;

    @BeforeEach
    void setUp() {
        organizer = userRepository.save(new User("alice", "alice@test.com", "hash"));
        invitee = userRepository.save(new User("bob", "bob@test.com", "hash"));

        meeting = new Meeting("Test", "Desc",
                Instant.parse("2026-07-15T10:00:00Z"),
                Instant.parse("2026-07-15T11:00:00Z"), organizer);
        meeting.addParticipant(new MeetingParticipant(meeting, organizer, InviteStatus.ACCEPTED));
        meeting.addParticipant(new MeetingParticipant(meeting, invitee, InviteStatus.PENDING));
        meeting = meetingRepository.save(meeting);
    }

    @Test
    void findByUserAndStatus_shouldReturnMatchingParticipants() {
        List<MeetingParticipant> result = participantRepository.findByUserAndStatus(invitee, InviteStatus.PENDING);
        assertEquals(1, result.size());
        assertEquals(InviteStatus.PENDING, result.get(0).getStatus());
        assertEquals(invitee.getId(), result.get(0).getUser().getId());
    }

    @Test
    void findByUserAndStatus_shouldReturnEmpty_whenNoMatch() {
        List<MeetingParticipant> result = participantRepository.findByUserAndStatus(invitee, InviteStatus.DECLINED);
        assertTrue(result.isEmpty());
    }

    @Test
    void findByMeetingIdAndUserId_shouldReturnParticipant() {
        Optional<MeetingParticipant> result = participantRepository.findByMeetingIdAndUserId(
                meeting.getId(), invitee.getId());
        assertTrue(result.isPresent());
        assertEquals(InviteStatus.PENDING, result.get().getStatus());
    }

    @Test
    void findByMeetingIdAndUserId_shouldReturnEmpty_whenNoMatch() {
        Optional<MeetingParticipant> result = participantRepository.findByMeetingIdAndUserId(
                999L, invitee.getId());
        assertTrue(result.isEmpty());
    }

    @Test
    void findByMeetingIdAndUserId_shouldReturnEmpty_forWrongUser() {
        Optional<MeetingParticipant> result = participantRepository.findByMeetingIdAndUserId(
                meeting.getId(), 999L);
        assertTrue(result.isEmpty());
    }
}
