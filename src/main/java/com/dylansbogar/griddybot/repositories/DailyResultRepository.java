package com.dylansbogar.griddybot.repositories;

import com.dylansbogar.griddybot.entities.DailyGame;
import com.dylansbogar.griddybot.entities.DailyResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyResultRepository extends JpaRepository<DailyResult, UUID> {
    Optional<DailyResult> findByUserIdAndGameAndPlayedOn(String userId, DailyGame game, LocalDate playedOn);
    List<DailyResult> findByUserIdAndPlayedOn(String userId, LocalDate playedOn);
}
