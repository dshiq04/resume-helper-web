package com.jobtrack.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * React Router 使用 history 模式，刷新页面时把前端路由回退到 index.html。
 * /api 与 /assets 不在此处拦截，分别由后端与静态资源处理。
 */
@Controller
public class SpaController {

    @GetMapping({
            "/",
            "/login",
            "/register",
            "/spring",
            "/autumn",
            "/internship",
            "/social",
            "/resumes",
            "/statistics",
            "/trash",
            "/settings",
            "/applications/{id}"
    })
    public String index() {
        return "forward:/index.html";
    }
}
