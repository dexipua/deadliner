package ua.dexipua.deadliner.feed;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CalendarFeedRepository extends JpaRepository<CalendarFeed, Long> {

    Optional<CalendarFeed> findByUserId(long userId);

    @Query("select f from CalendarFeed f, AppUser u where u.id = f.userId and u.active = true order by f.id")
    List<CalendarFeed> findAllOfActiveUsers();
}
