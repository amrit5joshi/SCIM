package com.amrit.scim.repository;

import com.amrit.scim.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link UserEntity}.
 * Method names are parsed by Spring Data's query-derivation engine; no SQL is
 * written by hand. Pagination is handled via Spring's {@link Pageable} abstraction.
 */
public interface UserRepository extends JpaRepository<UserEntity, String> {

    /** Exact-match lookup by userName; used for uniqueness checks and the {@code userName eq} filter. */
    Optional<UserEntity> findByUserName(String userName);

    Page<UserEntity> findAll(Pageable pageable);

    /** Exact case-sensitive match; {@code userName} is unique so the page contains 0 or 1 results. */
    Page<UserEntity> findByUserNameContainingIgnoreCase(String userName, Pageable pageable);
}
