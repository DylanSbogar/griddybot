package com.dylansbogar.griddybot.repositories;

import com.dylansbogar.griddybot.entities.SocialCredit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SocialCreditRepository extends JpaRepository<SocialCredit, String> {
    List<SocialCredit> findAllByOrderByTotalPointsDesc();
}
