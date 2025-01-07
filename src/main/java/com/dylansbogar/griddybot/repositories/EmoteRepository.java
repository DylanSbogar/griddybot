package com.dylansbogar.griddybot.repositories;

import com.dylansbogar.griddybot.entities.Emote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmoteRepository extends JpaRepository<Emote, UUID> {
    Optional<Emote> findByName(String name);
}
