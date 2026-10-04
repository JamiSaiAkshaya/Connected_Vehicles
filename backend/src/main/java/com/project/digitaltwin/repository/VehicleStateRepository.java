package com.project.digitaltwin.repository;

import com.project.digitaltwin.vehicle.VehicleState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VehicleStateRepository extends JpaRepository<VehicleState, Long> {

    Optional<VehicleState> findTopByVehicleIdAndValidTrueOrderByMessageTimestampDesc(String vehicleId);

    boolean existsByMessageId(String messageId);
}
