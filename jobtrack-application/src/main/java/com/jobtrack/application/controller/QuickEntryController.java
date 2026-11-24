package com.jobtrack.application.controller;

import com.jobtrack.common.api.Result;

import com.jobtrack.application.pojo.vo.QuickEntryDefaultsVO;
import com.jobtrack.common.constant.DomainConstants;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/system/quick-entry")
public class QuickEntryController {

    /**
     * 浏览器扩展在打开弹窗时调用，按服务器时间给出默认投递类型。
     */
    @GetMapping("/defaults")
    public Result<QuickEntryDefaultsVO> defaults() {
        LocalDateTime now = LocalDateTime.now();
        return Result.success(new QuickEntryDefaultsVO(DomainConstants.defaultCategoryForTime(now), now.toString()));
    }
}
