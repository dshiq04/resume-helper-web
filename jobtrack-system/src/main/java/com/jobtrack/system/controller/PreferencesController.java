package com.jobtrack.system.controller;

import com.jobtrack.common.api.Result;

import com.jobtrack.system.pojo.dto.PreferencesDTO;
import com.jobtrack.system.pojo.vo.PreferencesVO;
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
    public Result<PreferencesVO> get() {
        return Result.success(settingService.preferences());
    }

    @PutMapping
    public Result<PreferencesVO> update(@RequestBody PreferencesDTO request) {
        return Result.success(settingService.update(request));
    }
}
