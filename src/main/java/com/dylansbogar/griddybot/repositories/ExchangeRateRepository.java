package com.dylansbogar.griddybot.repositories;

import com.dylansbogar.griddybot.entities.ExchangeRate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, String> {
    @Query("SELECT e FROM ExchangeRate e ORDER BY e.dateUnix DESC")
    List<ExchangeRate> findAllOrderByTimestamp(Pageable pageable);

    @Query("SELECT e FROM ExchangeRate e ORDER BY e.dateUnix ASC")
    List<ExchangeRate> findAllOrderByTimestampAsc(Pageable pageable);
}
