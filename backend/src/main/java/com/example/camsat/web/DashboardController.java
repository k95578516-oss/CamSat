package com.example.camsat.web;

import com.example.camsat.dto.AnalyticsResponse;
import com.example.camsat.dto.DashboardResponse;
import com.example.camsat.security.AuthUser;
import com.example.camsat.service.DashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class DashboardController {
    private final DashboardService dashboard;
    public DashboardController(DashboardService dashboard) { this.dashboard = dashboard; }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard(@AuthenticationPrincipal AuthUser u) { return dashboard.dashboard(u.id()); }

    @GetMapping("/analytics")
    public AnalyticsResponse analytics(@AuthenticationPrincipal AuthUser u) { return dashboard.analytics(u.id()); }
}
