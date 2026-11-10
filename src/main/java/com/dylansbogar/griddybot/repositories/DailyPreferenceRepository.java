package com.dylansbogar.griddybot.repositories;

import com.dylansbogar.griddybot.entities.DailyGame;
import com.dylansbogar.griddybot.entities.DailyPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyPreferenceRepository extends JpaRepository<DailyPreference, UUID> {
    List<DailyPreference> findByUserId(String userId);
    Optional<DailyPreference> findByUserIdAndGame(String userId, DailyGame game);
}
