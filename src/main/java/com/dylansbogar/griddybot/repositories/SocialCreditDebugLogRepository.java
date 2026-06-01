package com.dylansbogar.griddybot.repositories;

import com.dylansbogar.griddybot.entities.SocialCreditDebugLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SocialCreditDebugLogRepository extends JpaRepository<SocialCreditDebugLog, UUID> {
}
