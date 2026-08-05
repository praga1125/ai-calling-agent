package com.flowzo.callingagent.controller;

import com.flowzo.callingagent.dto.DashboardResponse;
import com.flowzo.callingagent.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @Operation(summary = "Lead pipeline and call totals: qualified, converted and every other stage")
    public DashboardResponse summary() {
        return dashboardService.summary();
    }
}
