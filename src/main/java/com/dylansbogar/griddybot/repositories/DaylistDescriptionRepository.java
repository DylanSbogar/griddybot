package com.dylansbogar.griddybot.repositories;

import com.dylansbogar.griddybot.entities.DaylistDescription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DaylistDescriptionRepository extends JpaRepository<DaylistDescription, UUID> {
    @Query("SELECT d.description FROM DaylistDescription d")
    List<String> findAllDescriptions();

    @Query("SELECT d.description FROM DaylistDescription d WHERE d.userId = :userId")
    List<String> findAllDescriptionsFromUser(@Param("userId") String userId);
}
