package com.example.meetings.repository;

import com.example.meetings.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    private User savedUser;

    @BeforeEach
    void setUp() {
        savedUser = userRepository.save(new User("alice", "alice@test.com", "hash"));
    }

    @Test
    void findById_shouldReturnUser() {
        Optional<User> found = userRepository.findById(savedUser.getId());
        assertTrue(found.isPresent());
        assertEquals("alice", found.get().getUsername());
    }

    @Test
    void findByUsername_shouldReturnUser_whenExists() {
        Optional<User> found = userRepository.findByUsername("alice");
        assertTrue(found.isPresent());
        assertEquals("alice", found.get().getUsername());
        assertEquals("alice@test.com", found.get().getEmail());
        assertNotNull(found.get().getIcalToken());
    }

    @Test
    void findByUsername_shouldReturnEmpty_whenNotExists() {
        Optional<User> found = userRepository.findByUsername("unknown");
        assertTrue(found.isEmpty());
    }

    @Test
    void findByIcalToken_shouldReturnUser_whenExists() {
        Optional<User> found = userRepository.findByIcalToken(savedUser.getIcalToken());
        assertTrue(found.isPresent());
        assertEquals("alice", found.get().getUsername());
    }

    @Test
    void findByIcalToken_shouldReturnEmpty_whenNotExists() {
        Optional<User> found = userRepository.findByIcalToken("nonexistent-token");
        assertTrue(found.isEmpty());
    }

    @Test
    void existsByUsername_shouldReturnTrue_whenExists() {
        assertTrue(userRepository.existsByUsername("alice"));
    }

    @Test
    void existsByUsername_shouldReturnFalse_whenNotExists() {
        assertFalse(userRepository.existsByUsername("unknown"));
    }

    @Test
    void save_shouldGenerateUniqueIcalToken() {
        User another = userRepository.save(new User("bob", "bob@test.com", "hash"));
        assertNotNull(another.getIcalToken());
        assertNotEquals(savedUser.getIcalToken(), another.getIcalToken());
    }
}
