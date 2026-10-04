package com.project.digitaltwin.api;

import com.project.digitaltwin.twin.DigitalTwinService;
import com.project.digitaltwin.twin.DigitalTwinState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
public class TwinController {

    private final DigitalTwinService digitalTwinService;

    public TwinController(DigitalTwinService digitalTwinService) {
        this.digitalTwinService = digitalTwinService;
    }

    @GetMapping("/api/twins/buses")
    public List<DigitalTwinState> getAllBuses() {
        return digitalTwinService.getAllTwins();
    }

    @GetMapping("/api/twins/bus/{id}")
    public ResponseEntity<DigitalTwinState> getBus(@PathVariable String id) {
        try {
            return ResponseEntity.ok(digitalTwinService.getTwin(id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/api/health")
    public String health() {
        return "OK";
    }
}
