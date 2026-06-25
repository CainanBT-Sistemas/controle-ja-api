package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.entities.ClosedTestTester;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClosedTestTesterRepository extends JpaRepository<ClosedTestTester, UUID> {

    boolean existsByNormalizedEmailAndEnabledTrue(String normalizedEmail);

    Optional<ClosedTestTester> findByNormalizedEmail(String normalizedEmail);
}
