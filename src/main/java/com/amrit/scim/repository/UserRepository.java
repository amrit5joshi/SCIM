package com.amrit.scim.repository;

import com.amrit.scim.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link UserEntity}.
 * Method names are parsed by Spring Data's query-derivation engine; no SQL is
 * written by hand. Pagination is handled via Spring's {@link Pageable} abstraction.
 */
public interface UserRepository extends JpaRepository<UserEntity, String> {

    /** JOIN FETCH overrides the default findById so emails are loaded in one query. */
    @EntityGraph(attributePaths = "emails")
    Optional<UserEntity> findById(String id);

    /**
     * JOIN FETCH lookup by userName for uniqueness checks and the {@code userName eq} filter.
     * EntityGraph ensures emails are loaded in the same query, avoiding a lazy-load outside a transaction.
     */
    @EntityGraph(attributePaths = "emails")
    Optional<UserEntity> findByUserName(String userName);

    Page<UserEntity> findAll(Pageable pageable);

    /**
     * Exact case-sensitive match for the SCIM {@code userName eq "value"} filter.
     * {@code userName} is unique so the page contains at most one result.
     */
    Page<UserEntity> findByUserName(String userName, Pageable pageable);
}
