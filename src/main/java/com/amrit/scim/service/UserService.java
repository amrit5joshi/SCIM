package com.amrit.scim.service;

import com.amrit.scim.dto.ScimListResponse;
import com.amrit.scim.dto.ScimUser;
import com.amrit.scim.entity.UserEntity;
import com.amrit.scim.exception.DuplicateUserNameException;
import com.amrit.scim.exception.UserNotFoundException;
import com.amrit.scim.filter.FilterCriteria;
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
 * Owns transaction boundaries, uniqueness checks, and delegation to the
 * mapper and repository. HTTP concerns stay in the controller; persistence
 * concerns stay in the repository.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final ScimFilterParser filterParser;

    /**
     * Creates a new SCIM User, checking for duplicate {@code userName} at
     * the application layer to return a 409 rather than a raw DB constraint error.
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

    /** Retrieves a single user by server-generated UUID. */
    @Transactional(readOnly = true)
    public ScimUser getUser(String id, String baseUrl) {
        log.debug("Fetching user id={}", id);
        UserEntity entity = findOrThrow(id);
        return userMapper.toDto(entity, baseUrl);
    }

    /**
     * Lists users with optional filtering and pagination.
     * SCIM {@code startIndex} is 1-based; converted to 0-based Spring Data page number.
     */
    @Transactional(readOnly = true)
    public ScimListResponse listUsers(String filter, int startIndex, int count, String baseUrl) {
        log.debug("Listing users filter='{}' startIndex={} count={}", filter, startIndex, count);

        int clampedCount = Math.min(Math.max(count, 1), 100);
        int pageNumber = (startIndex - 1) / clampedCount;

        Pageable pageable = PageRequest.of(pageNumber, clampedCount);

        Optional<FilterCriteria> criteria = filterParser.parse(filter);

        Page<UserEntity> page;
        if (criteria.isEmpty()) {
            page = userRepository.findAll(pageable);
        } else if ("username".equals(criteria.get().attribute())) {
            page = userRepository.findByUserName(criteria.get().value(), pageable);
        } else {
            page = userRepository.findByExternalId(criteria.get().value(), pageable);
        }

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
     * Full replacement of a user resource (RFC 7644 §3.5.1).
     * Fields absent from the body are cleared; {@code id} and {@code created} are preserved.
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

    /** Hard-deletes a user; cascade removes child email rows automatically. */
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
