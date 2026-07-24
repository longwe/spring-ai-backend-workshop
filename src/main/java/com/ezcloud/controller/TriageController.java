package com.ezcloud.controller;

import com.ezcloud.dto.TriageDtos.TicketTriage;
import com.ezcloud.dto.TriageDtos.TriageRequest;
import com.ezcloud.service.TriageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/triage")
public class TriageController {

    private final TriageService triageService;

    public TriageController(TriageService triageService) {
        this.triageService = triageService;
    }

    @PostMapping
    public ResponseEntity<TicketTriage> triage(@Valid @RequestBody TriageRequest request) {
        return ResponseEntity.ok(triageService.triage(request));
    }
}
