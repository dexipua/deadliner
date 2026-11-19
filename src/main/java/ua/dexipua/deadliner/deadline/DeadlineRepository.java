package ua.dexipua.deadliner.deadline;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeadlineRepository extends JpaRepository<Deadline, Long> {

    List<Deadline> findByUserId(long userId);
}
