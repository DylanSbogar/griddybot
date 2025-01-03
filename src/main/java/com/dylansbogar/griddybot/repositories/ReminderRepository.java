package com.dylansbogar.griddybot.repositories;

import com.dylansbogar.griddybot.entities.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, UUID> {
}
