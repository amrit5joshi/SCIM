package com.amrit.scim.service;

import com.amrit.scim.dto.ScimListResponse;
import com.amrit.scim.dto.ScimUser;
import com.amrit.scim.entity.UserEntity;
import com.amrit.scim.exception.DuplicateUserNameException;
import com.amrit.scim.exception.UserNotFoundException;
import com.amrit.scim.filter.ScimFilterParser;
import com.amrit.scim.mapper.UserMapper;
import com.amrit.scim.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Business-logic layer for SCIM User operations.
 * <p>
 * This class sits between the controller (HTTP concerns) and the repository
 * (persistence concerns). It is responsible for: validating business rules
 * (e.g. uniqueness), delegating to the mapper, managing transactions, and
 * logging meaningful audit events.
 * <p>
 * {@code @RequiredArgsConstructor} generates a constructor for the two
 * {@code final} fields — this is constructor injection, the Spring-recommended
 * style (no {@code @Autowired} on fields).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final ScimFilterParser filterParser;

    /**
     * Creates a new SCIM User.
     * <p>
     * Checks for duplicate {@code userName} at the application level before
     * hitting the DB, so we can return a clean SCIM 409 error instead of a
     * raw {@code DataIntegrityViolationException}.
     *
     * @param dto     the validated inbound ScimUser body
     * @param baseUrl request base URL used to build the meta.location field
     * @return the persisted user as a ScimUser DTO
     * @throws DuplicateUserNameException if userName is already taken
     */
    @Transactional
    public ScimUser createUser(ScimUser dto, String baseUrl) {
        log.info("Creating user with userName={}", dto.getUserName());

        if (userRepository.findByUserName(dto.getUserName()).isPresent()) {
            throw new DuplicateUserNameException(dto.getUserName());
        }

        UserEntity entity = userMapper.toNewEntity(dto);
        UserEntity saved = userRepository.save(entity);
        log.info("Created user id={}", saved.getId());
        return userMapper.toDto(saved, baseUrl);
    }

    /**
     * Retrieves a single user by their server-generated UUID.
     *
     * @param id      the user's UUID
     * @param baseUrl request base URL for meta.location
     * @return the user as a ScimUser DTO
     * @throws UserNotFoundException if no user exists with that id
     */
    @Transactional(readOnly = true)
    public ScimUser getUser(String id, String baseUrl) {
        log.debug("Fetching user id={}", id);
        UserEntity entity = findOrThrow(id);
        return userMapper.toDto(entity, baseUrl);
    }

    /**
     * Lists users with optional filtering and pagination.
     * <p>
     * Pagination is 1-based (SCIM spec §3.4.2.4): {@code startIndex=1} means
     * the first record. We convert to 0-based for Spring Data's {@link Pageable}.
     *
     * @param filter     raw SCIM filter string (may be null/blank)
     * @param startIndex 1-based start index (default 1)
     * @param count      page size (default 10, max 100)
     * @param baseUrl    request base URL for meta.location
     * @return a ScimListResponse envelope with pagination metadata
     */
    @Transactional(readOnly = true)
    public ScimListResponse listUsers(String filter, int startIndex, int count, String baseUrl) {
        log.debug("Listing users filter='{}' startIndex={} count={}", filter, startIndex, count);

        // Clamp count to valid range
        int clampedCount = Math.min(Math.max(count, 1), 100);
        // Convert 1-based SCIM startIndex to 0-based Spring Data page number
        int pageNumber = (startIndex - 1) / clampedCount;

        Pageable pageable = PageRequest.of(pageNumber, clampedCount);

        Optional<String> userNameFilter = filterParser.parseUserNameFilter(filter);

        Page<UserEntity> page = userNameFilter.isPresent()
                ? userRepository.findByUserNameContainingIgnoreCase(userNameFilter.get(), pageable)
                : userRepository.findAll(pageable);

        List<ScimUser> resources = page.getContent().stream()
                .map(e -> userMapper.toDto(e, baseUrl))
                .collect(Collectors.toList());

        return ScimListResponse.builder()
                .totalResults((int) page.getTotalElements())
                .startIndex(startIndex)
                .itemsPerPage(resources.size())
                .resources(resources)
                .build();
    }

    /**
     * Replaces all mutable fields of an existing user (full PUT replacement).
     * <p>
     * Per SCIM RFC 7644 §3.5.1, PUT replaces the resource entirely — any
     * field not included in the body is cleared. The {@code id}, {@code created},
     * and schema fields are preserved by the mapper.
     *
     * @param id      the user's UUID
     * @param dto     the replacement body
     * @param baseUrl request base URL for meta.location
     * @return the updated user as a ScimUser DTO
     * @throws UserNotFoundException      if no user exists with that id
     * @throws DuplicateUserNameException if the new userName is already used by another user
     */
    @Transactional
    public ScimUser replaceUser(String id, ScimUser dto, String baseUrl) {
        log.info("Replacing user id={} with userName={}", id, dto.getUserName());

        UserEntity existing = findOrThrow(id);

        // If the userName is changing, make sure the new one isn't taken
        if (!existing.getUserName().equals(dto.getUserName())) {
            if (userRepository.findByUserName(dto.getUserName()).isPresent()) {
                throw new DuplicateUserNameException(dto.getUserName());
            }
        }

        UserEntity updated = userMapper.applyUpdate(existing, dto);
        UserEntity saved = userRepository.save(updated);
        log.info("Replaced user id={}", saved.getId());
        return userMapper.toDto(saved, baseUrl);
    }

    /**
     * Hard-deletes a user by their UUID.
     * <p>
     * CascadeType.ALL on the emails relationship means Hibernate automatically
     * deletes the related {@code user_emails} rows too.
     *
     * @param id the user's UUID
     * @throws UserNotFoundException if no user exists with that id
     */
    @Transactional
    public void deleteUser(String id) {
        log.info("Deleting user id={}", id);
        UserEntity entity = findOrThrow(id);
        userRepository.delete(entity);
        log.info("Deleted user id={}", id);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private UserEntity findOrThrow(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }
}
