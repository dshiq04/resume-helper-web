package com.jobtrack.system.controller;

import com.jobtrack.system.pojo.PreferencesRequest;
import com.jobtrack.system.pojo.PreferencesResponse;
import com.jobtrack.system.service.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/preferences")
@RequiredArgsConstructor
public class PreferencesController {

    private final SettingService settingService;

    @GetMapping
    public PreferencesResponse get() {
        return settingService.preferences();
    }

    @PutMapping
    public PreferencesResponse update(@RequestBody PreferencesRequest request) {
        return settingService.update(request);
    }
}
