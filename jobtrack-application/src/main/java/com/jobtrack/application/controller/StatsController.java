package com.jobtrack.application.controller;

import com.jobtrack.application.dto.DailyStatsResponse;
import com.jobtrack.application.dto.FunnelResponse;
import com.jobtrack.application.dto.StatsResponse;
import com.jobtrack.application.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping
    public StatsResponse stats(@RequestParam(required = false) String from,
                               @RequestParam(required = false) String to) {
        return statsService.stats(from, to);
    }

    @GetMapping("/daily")
    public DailyStatsResponse daily(@RequestParam(required = false) String from,
                                    @RequestParam(required = false) String to) {
        return statsService.daily(from, to);
    }

    @GetMapping("/funnel")
    public FunnelResponse funnel(@RequestParam(required = false) String from,
                                 @RequestParam(required = false) String to) {
        return statsService.funnel(from, to);
    }
}
