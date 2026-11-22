package com.jobtrack.application.controller;

import com.jobtrack.application.pojo.vo.DailyStatsVO;
import com.jobtrack.application.pojo.vo.FunnelVO;
import com.jobtrack.application.pojo.vo.StatsVO;
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
    public StatsVO stats(@RequestParam(required = false) String from,
                               @RequestParam(required = false) String to,
                               @RequestParam(required = false) Integer year) {
        return statsService.stats(from, to, year);
    }

    @GetMapping("/daily")
    public DailyStatsVO daily(@RequestParam(required = false) String from,
                                    @RequestParam(required = false) String to,
                                    @RequestParam(required = false) Integer year) {
        return statsService.daily(from, to, year);
    }

    @GetMapping("/funnel")
    public FunnelVO funnel(@RequestParam(required = false) String from,
                                 @RequestParam(required = false) String to,
                                 @RequestParam(required = false) Integer year) {
        return statsService.funnel(from, to, year);
    }
}
