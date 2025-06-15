package com.dylansbogar.griddybot.repositories;

import com.dylansbogar.griddybot.entities.PostedDeal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DealHistoryRepository extends JpaRepository<PostedDeal, String> {}
