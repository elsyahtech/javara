package com.elsyah.tech.javara.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import com.elsyah.tech.javara.entity.UserEntity;
import com.elsyah.tech.javara.projection.UserSelectColumn;

/**
 * UserRepository is a Spring Data JPA repository interface for managing UserEntity objects.
 * It extends JpaRepository, providing CRUD operations and additional query methods.
 * @author Elmansyah
 * @version 1.0.0
 * @since 2026-11-18
 * @contact https://www.linkedin.com/in/elmansyah
 */
public interface UserRepository extends JpaRepository<UserEntity, String> {
    /**
     * findByIdForUpdate retrieves a user entity by its ID with a pessimistic write lock.
     * This method is used to ensure that the user entity is locked for update, preventing other
     * transactions from modifying it until the current transaction is complete.
     *
     * @param email the email to check
     * @param id the ID to exclude from the check
     * @return true if another user uses the email, false otherwise
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserEntity u WHERE u.id = :id")
    Optional<UserEntity> findByIdForUpdate(@Param("id") String id);

    /**
     * findByEmailForUpdate retrieves a user entity by its email with a pessimistic write lock.
     * This method is used to ensure that the user entity is locked for update, preventing other
     * transactions from modifying it until the current transaction is complete.
     *
     * @param email the email of the user to find
     * @return an Optional containing the UserEntity if found, or empty if not found
     */

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserEntity u WHERE u.email = :email")
    Optional<UserEntity> findByEmailForUpdate(@Param("email") String email);

    /**
     * Checks if a user exists with the given email.
     *
     * @param email the email to check for existence
     * @return true if a user with the given email exists, false otherwise
     */
    boolean existsByEmail(String email);

    /**
     * Checks whether another active user already uses the given email.
     *
     * @param email the email to check
     * @param id the ID to exclude from the check
     * @return true if another user uses the email, false otherwise
     */
    boolean existsByEmailAndIdNot(String email, String id);

    /**
     * Finds a user by their email.
     *
     * @param email the email of the user to find
     * @return an Optional containing the UserEntity if found, or empty if not found
     */
    Optional<UserEntity> findByEmail(String email);
    
    /**
     * Retrieves a list of users with selected columns.
     *
     * @return a list of UserSelectColumn projections containing selected user data
     */
    @Query("SELECT u.id AS id, u.email AS email, " +
           "u.isActive AS isActive, u.createdAt AS createdAt, " +
           "u.updatedAt AS updatedAt, u.deletedAt AS deletedAt " +
           "FROM UserEntity u")
    List<UserSelectColumn> findAllUsersWithSelectedColumns();
    
    /**
     * Finds a user by their ID with selected columns.
     *
     * @param id the ID of the user to find
     * @return an Optional containing the UserSelectColumn projection if found, or empty if not found
     */
    @Query("SELECT u.id AS id, u.email AS email, " +
           "u.isActive AS isActive, u.createdAt AS createdAt, " +
           "u.updatedAt AS updatedAt, u.deletedAt AS deletedAt " +
           "FROM UserEntity u WHERE u.id = :id")
    Optional<UserSelectColumn> findUserByIdWithSelectedColumns(@Param("id") String id);

    /**
     * Retrieves a list of users with selected columns, limited and offset.
     *
     * @param pageable the pagination information
     * @return a list of UserSelectColumn projections containing selected user data
     */
    @Query ("SELECT u.id AS id, u.email AS email, " +
           "u.isActive AS isActive, u.createdAt AS createdAt, " +
           "u.updatedAt AS updatedAt, u.deletedAt AS deletedAt " +
           "FROM UserEntity u")
    List<UserSelectColumn> findAllUsersWithLimitAndOffset(Pageable pageable);
}
