package com.dylansbogar.griddybot.repositories;

import com.dylansbogar.griddybot.entities.DailyGame;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DailyGameRepository extends JpaRepository<DailyGame, UUID> {
    Optional<DailyGame> findByNameKey(String nameKey);
}
