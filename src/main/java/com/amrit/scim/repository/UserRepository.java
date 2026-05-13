package com.amrit.scim.repository;

import com.amrit.scim.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link UserEntity}.
 * <p>
 * Spring auto-generates the implementation at startup — we never write SQL
 * ourselves. The {@code JpaRepository} base interface provides standard CRUD
 * methods ({@code save}, {@code findById}, {@code delete}, etc.) plus
 * pagination support via {@link Pageable}.
 * <p>
 * Interview talking-point: method names like {@code findByUserName} are parsed
 * by Spring Data's query-derivation engine — it reads the method signature and
 * generates {@code SELECT * FROM users WHERE user_name = ?} automatically.
 */
public interface UserRepository extends JpaRepository<UserEntity, String> {

    /**
     * Looks up a user by their unique userName.
     * Used by the service layer to enforce uniqueness on create and to
     * support the {@code userName eq "..."} SCIM filter on list requests.
     *
     * @param userName the SCIM userName to search for
     * @return an Optional containing the user, or empty if not found
     */
    Optional<UserEntity> findByUserName(String userName);

    /**
     * Returns a paginated slice of all users — used by the list endpoint
     * when no filter is supplied.
     *
     * @param pageable encapsulates offset/limit derived from startIndex + count
     * @return a Page containing the current slice and total element count
     */
    Page<UserEntity> findAll(Pageable pageable);

    /**
     * Returns a paginated slice filtered by userName.
     * Used when the client supplies a {@code userName eq "..."} filter.
     *
     * @param userName the userName to match (exact, case-sensitive per SCIM spec)
     * @param pageable encapsulates offset/limit
     * @return a Page of matching users (0 or 1 in practice since userName is unique)
     */
    Page<UserEntity> findByUserNameContainingIgnoreCase(String userName, Pageable pageable);
}
