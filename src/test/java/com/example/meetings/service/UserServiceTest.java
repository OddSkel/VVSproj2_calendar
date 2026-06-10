package com.example.meetings.service;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    //@Captor private ArgumentCaptor<User> userCaptor;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void register_shouldCreateUserWithEncodedPassword() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("rawPassword")).thenReturn("encoded");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        User result = service.register("alice", "alice@example.com", "rawPassword");

        assertEquals("alice", result.getUsername());
        assertEquals("alice@example.com", result.getEmail());
        assertEquals("encoded", result.getPasswordHash());
        assertNotNull(result.getIcalToken());
        verify(userRepository).save(any());
    }

    @Test
    void register_shouldThrowWhenUsernameTaken() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
                () -> service.register("alice", "a@a.com", "pw"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void requireByUsername_shouldReturnUserWhenFound() {
        User user = new User("alice", "a@a.com", "hash");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        assertSame(user, service.requireByUsername("alice"));
    }

    @Test
    void requireByUsername_shouldThrowWhenNotFound() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> service.requireByUsername("unknown"));
    }
}
