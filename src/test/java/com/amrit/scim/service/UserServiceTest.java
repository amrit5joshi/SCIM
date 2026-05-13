package com.amrit.scim.service;

import com.amrit.scim.dto.ScimEmail;
import com.amrit.scim.dto.ScimName;
import com.amrit.scim.dto.ScimUser;
import com.amrit.scim.entity.EmailEntity;
import com.amrit.scim.entity.UserEntity;
import com.amrit.scim.exception.DuplicateUserNameException;
import com.amrit.scim.filter.ScimFilterParser;
import com.amrit.scim.mapper.UserMapper;
import com.amrit.scim.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for {@link UserService}.
 * <p>
 * We use Mockito to stub out the repository and mapper so there is no Spring
 * context, no database, and no HTTP involved — these tests run in milliseconds.
 * The goal is to verify the service's business rules in isolation:
 * duplicate checks, delegation to the mapper, correct method calls on the repo.
 * <p>
 * {@code @ExtendWith(MockitoExtension.class)} replaces the old JUnit 4
 * {@code @RunWith(MockitoJUnitRunner.class)} and wires up {@code @Mock}
 * and {@code @InjectMocks} automatically.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ScimFilterParser filterParser;

    @InjectMocks
    private UserService userService;

    private static final String BASE_URL = "http://localhost:8080";

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ScimUser buildScimUser(String userName) {
        return ScimUser.builder()
                .userName(userName)
                .name(ScimName.builder()
                        .givenName("Alice")
                        .familyName("Smith")
                        .formatted("Alice Smith")
                        .build())
                .emails(List.of(ScimEmail.builder()
                        .value("alice@example.com")
                        .type("work")
                        .primary(true)
                        .build()))
                .active(true)
                .build();
    }

    private UserEntity buildUserEntity(String id, String userName) {
        return UserEntity.builder()
                .id(id)
                .userName(userName)
                .givenName("Alice")
                .familyName("Smith")
                .formattedName("Alice Smith")
                .active(true)
                .created(Instant.now())
                .lastModified(Instant.now())
                .emails(new ArrayList<>(List.of(
                        EmailEntity.builder()
                                .id(1L)
                                .value("alice@example.com")
                                .type("work")
                                .primary(true)
                                .build())))
                .build();
    }

    // -------------------------------------------------------------------------
    // createUser
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createUser — saves entity and returns DTO when userName is unique")
    void createUser_uniqueUserName_savesAndReturnsDto() {
        ScimUser dto = buildScimUser("alice@example.com");
        UserEntity entity = buildUserEntity("uuid-1", "alice@example.com");
        ScimUser expectedResponse = buildScimUser("alice@example.com");
        expectedResponse.setId("uuid-1");

        // No existing user with this userName
        when(userRepository.findByUserName("alice@example.com")).thenReturn(Optional.empty());
        when(userMapper.toNewEntity(dto)).thenReturn(entity);
        when(userRepository.save(entity)).thenReturn(entity);
        when(userMapper.toDto(entity, BASE_URL)).thenReturn(expectedResponse);

        ScimUser result = userService.createUser(dto, BASE_URL);

        assertThat(result.getUserName()).isEqualTo("alice@example.com");
        assertThat(result.getId()).isEqualTo("uuid-1");

        // Verify interactions
        verify(userRepository).findByUserName("alice@example.com");
        verify(userMapper).toNewEntity(dto);
        verify(userRepository).save(entity);
        verify(userMapper).toDto(entity, BASE_URL);
    }

    @Test
    @DisplayName("createUser — throws DuplicateUserNameException when userName already exists")
    void createUser_duplicateUserName_throwsException() {
        ScimUser dto = buildScimUser("duplicate@example.com");
        UserEntity existing = buildUserEntity("uuid-existing", "duplicate@example.com");

        when(userRepository.findByUserName("duplicate@example.com"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.createUser(dto, BASE_URL))
                .isInstanceOf(DuplicateUserNameException.class)
                .hasMessageContaining("duplicate@example.com");

        // Verify we never attempted to save
        verify(userRepository, never()).save(any());
        verify(userMapper, never()).toNewEntity(any());
    }

    // -------------------------------------------------------------------------
    // getUser (findByUserName delegation)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getUser — returns DTO when user exists")
    void getUser_exists_returnsDto() {
        UserEntity entity = buildUserEntity("uuid-2", "bob@example.com");
        ScimUser expectedDto = buildScimUser("bob@example.com");
        expectedDto.setId("uuid-2");

        when(userRepository.findById("uuid-2")).thenReturn(Optional.of(entity));
        when(userMapper.toDto(entity, BASE_URL)).thenReturn(expectedDto);

        ScimUser result = userService.getUser("uuid-2", BASE_URL);

        assertThat(result.getId()).isEqualTo("uuid-2");
        assertThat(result.getUserName()).isEqualTo("bob@example.com");
        verify(userRepository).findById("uuid-2");
        verify(userMapper).toDto(entity, BASE_URL);
    }

    @Test
    @DisplayName("getUser — throws UserNotFoundException when id is unknown")
    void getUser_notFound_throwsException() {
        when(userRepository.findById("unknown-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser("unknown-id", BASE_URL))
                .isInstanceOf(com.amrit.scim.exception.UserNotFoundException.class)
                .hasMessageContaining("unknown-id");
    }

    // -------------------------------------------------------------------------
    // deleteUser
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("deleteUser — calls repository.delete when user exists")
    void deleteUser_exists_callsDelete() {
        UserEntity entity = buildUserEntity("uuid-3", "charlie@example.com");

        when(userRepository.findById("uuid-3")).thenReturn(Optional.of(entity));

        userService.deleteUser("uuid-3");

        verify(userRepository).delete(entity);
    }

    @Test
    @DisplayName("deleteUser — throws UserNotFoundException when user not found")
    void deleteUser_notFound_throwsException() {
        when(userRepository.findById("ghost-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser("ghost-id"))
                .isInstanceOf(com.amrit.scim.exception.UserNotFoundException.class);

        verify(userRepository, never()).delete(any());
    }
}
