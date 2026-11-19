package ua.dexipua.deadliner.deadline;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface DeadlineRepository extends JpaRepository<Deadline, Long> {

    List<Deadline> findByUserId(long userId);

    @Query("""
            select d from Deadline d, AppUser u
            where u.id = d.userId and u.active = true
              and d.status = ua.dexipua.deadliner.deadline.DeadlineStatus.OPEN and d.dueAt > :now
            order by d.userId, d.dueAt""")
    List<Deadline> findUpcomingOpenOfActiveUsers(Instant now);

    @Query("""
            select d from Deadline d
            where d.userId = :userId
              and d.status = ua.dexipua.deadliner.deadline.DeadlineStatus.OPEN
              and d.dueAt > :from and d.dueAt <= :to
            order by d.dueAt""")
    List<Deadline> findOpenDueBetween(long userId, Instant from, Instant to);
}
