package com.example.consumer.domain;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ProcessingService {
    public void process(Map<String, Object> order) {
        // Phase 1: simulated domain work, in-memory only.
        // Phase 3 wires up the deterministic 10% failure path (APP-04).
    }
}
