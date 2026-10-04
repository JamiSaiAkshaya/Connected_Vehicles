package com.project.digitaltwin.repository;

import com.project.digitaltwin.twin.DigitalTwinState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DigitalTwinStateRepository extends JpaRepository<DigitalTwinState, String> {
}
