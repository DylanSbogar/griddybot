package com.dylansbogar.griddybot.repositories;

import com.dylansbogar.griddybot.entities.Daylist;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DaylistRepository extends JpaRepository<Daylist, UUID> {
    @Query("SELECT d from Daylist d WHERE d.userId = :userId ORDER BY d.timestamp DESC")
    List<Daylist> findLastByUserIdOrderByTimestampDesc(@Param("userId") String userId, Pageable pageable);
}
