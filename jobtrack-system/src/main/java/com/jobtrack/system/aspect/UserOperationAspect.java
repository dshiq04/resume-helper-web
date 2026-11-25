package com.jobtrack.system.aspect;

import com.jobtrack.common.api.Result;
import com.jobtrack.system.messaging.UserOperationMessage;
import com.jobtrack.system.pojo.vo.AuthVO;
import com.jobtrack.system.security.AuthPrincipal;
import com.jobtrack.system.service.UserOperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 使用 Spring AOP 捕获控制器操作，并交由 RabbitMQ 异步写入 user_operation_log。
 *
 * <p>只记录有实际写操作的请求（POST / PUT / PATCH / DELETE）以及登录、注册、退出等
 * 身份操作，避免查询列表产生大量噪声记录。</p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class UserOperationAspect {

    private static final int MAX_USER_AGENT = 255;
    private static final int MAX_PATH = 255;

    private final UserOperationLogService operationLogService;

    @Around("execution(* com.jobtrack..controller..*(..))")
    public Object capture(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        try {
            record(result);
        } catch (Exception exception) {
            // 审计记录不能影响正常业务。
            log.warn("捕获用户操作失败: {}", exception.getMessage());
        }
        return result;
    }

    private void record(Object result) {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return;
        }
        String method = request.getMethod() == null
                ? "" : request.getMethod().toUpperCase(Locale.ROOT);
        if (!"POST".equals(method) && !"PUT".equals(method)
                && !"PATCH".equals(method) && !"DELETE".equals(method)) {
            return;
        }
        String path = request.getRequestURI();
        if (path == null || skipped(path)) {
            return;
        }

        Actor actor = actor(result);
        if (actor == null || actor.userId() == null) {
            return;
        }
        Descriptor descriptor = descriptor(method, path);
        UserOperationMessage message = new UserOperationMessage(
                actor.tenantId(), actor.userId(), actor.username(), actor.displayName(),
                descriptor.type(), descriptor.action(), descriptor.description(),
                method, limit(path, MAX_PATH), clientIp(request),
                limit(request.getHeader("User-Agent"), MAX_USER_AGENT),
                LocalDateTime.now());
        operationLogService.record(message);
    }

    private Actor actor(Object result) {
        // 控制器统一返回 Result<T>，登录 / 注册需要先取出 data 再判断；
        // 登录阶段 SecurityContext 仍为空，只能依赖返回值识别操作人。
        Object payload = result instanceof Result<?> wrapper ? wrapper.getData() : result;
        if (payload instanceof AuthVO auth && auth.getUser() != null) {
            return new Actor(auth.getUser().getTenantId(), auth.getUser().getId(),
                    auth.getUser().getUsername(), auth.getUser().getDisplayName());
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
            return new Actor(principal.tenantId(), principal.id(),
                    principal.username(), principal.displayName());
        }
        return null;
    }

    /** 不记录登录页初始化、验证码、忘记密码、授权码校验与公开资源读取。 */
    private boolean skipped(String path) {
        return path.startsWith("/api/auth/status")
                || path.startsWith("/api/auth/captcha")
                || path.startsWith("/api/auth/forgot-password")
                || path.startsWith("/api/tokens/validate")
                || path.startsWith("/api/avatar/")
                || path.startsWith("/api/health")
                || path.startsWith("/api/config");
    }

    private Descriptor descriptor(String method, String path) {
        if (path.equals("/api/auth/login")) {
            return new Descriptor("LOGIN", "LOGIN", "登录账号");
        }
        if (path.equals("/api/auth/register")) {
            return new Descriptor("REGISTER", "REGISTER", "注册账号");
        }
        if (path.equals("/api/auth/logout")) {
            return new Descriptor("LOGOUT", "LOGOUT", "退出登录");
        }
        if (path.equals("/api/auth/agreement")) {
            return new Descriptor("AGREEMENT", "SIGN", "签署用户协议");
        }
        if (path.equals("/api/auth/change-password")) {
            return new Descriptor("SECURITY", "UPDATE", "修改密码");
        }
        if (path.startsWith("/api/account/cancel")) {
            return new Descriptor("ACCOUNT", action(method), "账号注销操作");
        }
        if (path.startsWith("/api/account/profile")) {
            return new Descriptor("PROFILE", "UPDATE", "保存个人资料");
        }
        if (path.startsWith("/api/account/contact")) {
            return new Descriptor("PROFILE", "UPDATE", "保存联系方式");
        }
        if (path.startsWith("/api/account/avatar")) {
            return new Descriptor("PROFILE", "UPDATE", "更换头像");
        }
        if (path.startsWith("/api/account/tokens") || path.startsWith("/api/tokens")) {
            return new Descriptor("TOKEN", action(method), "授权码" + actionLabel(method));
        }
        if (path.startsWith("/api/account")) {
            return new Descriptor("ACCOUNT", action(method), "账号设置" + actionLabel(method));
        }
        if (path.startsWith("/api/preferences")) {
            return new Descriptor("PROFILE", "UPDATE", "更新偏好设置");
        }
        if (path.startsWith("/api/admin/users/")
                && path.endsWith("/status")) {
            return new Descriptor("ADMIN", action(method), "修改用户状态");
        }
        if (path.startsWith("/api/admin/users/")
                && path.endsWith("/password")) {
            return new Descriptor("ADMIN", "UPDATE", "重置用户密码");
        }
        if (path.startsWith("/api/admin/users/")
                && path.contains("/permissions")) {
            return new Descriptor("ADMIN", action(method), "调整用户权限");
        }
        if (path.startsWith("/api/admin/users/")
                && path.endsWith("/tokens")) {
            return new Descriptor("ADMIN", action(method), "管理用户授权码");
        }
        if (path.startsWith("/api/admin/")) {
            return new Descriptor("ADMIN", action(method), "管理员操作");
        }
        if (path.startsWith("/api/trash")) {
            return new Descriptor("APPLICATION", action(method), "回收站" + actionLabel(method));
        }
        if (path.startsWith("/api/applications") || path.startsWith("/api/quick-entry")) {
            return new Descriptor("APPLICATION", action(method), "投递记录" + actionLabel(method));
        }
        if (path.startsWith("/api/resumes")) {
            return new Descriptor("RESUME", action(method), "简历" + actionLabel(method));
        }
        return new Descriptor("OTHER", action(method), method + " " + path);
    }

    private String action(String method) {
        if ("DELETE".equals(method)) {
            return "DELETE";
        }
        if ("PUT".equals(method) || "PATCH".equals(method)) {
            return "UPDATE";
        }
        return "CREATE";
    }

    private String actionLabel(String method) {
        if ("DELETE".equals(method)) {
            return "删除";
        }
        if ("PUT".equals(method) || "PATCH".equals(method)) {
            return "更新";
        }
        return "创建";
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String limit(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private record Actor(Long tenantId, Long userId, String username, String displayName) {
    }

    private record Descriptor(String type, String action, String description) {
    }
}
