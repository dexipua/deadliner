package ua.dexipua.deadliner.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByTelegramId(long telegramId);

    List<AppUser> findByActiveTrueAndDigestEnabledTrue();

    /** Only flips the flag: a job must not overwrite settings the student is saving at the same moment. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("update AppUser u set u.active = false where u.id = :id")
    void deactivate(long id);

    /** Serialises concurrent changes to one user's settings (fast clicks in the dashboard). */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> lockById(long id);
}
