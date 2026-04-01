package com.dylansbogar.griddybot.repositories;

import com.dylansbogar.griddybot.entities.LastServer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LastServerRepository extends JpaRepository<LastServer, Long> {
}
