package com.dylansbogar.griddybot.repositories;

import com.dylansbogar.griddybot.entities.Daylist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DaylistRepository extends JpaRepository<Daylist, UUID> {
}
