package com.jobtrack.application.controller;

import com.jobtrack.application.pojo.po.ApplicationRecord;
import com.jobtrack.application.pojo.po.Resume;
import com.jobtrack.application.pojo.dto.ApplicationDTO;
import com.jobtrack.application.pojo.dto.ApplicationUpdateDTO;
import com.jobtrack.application.pojo.dto.ResumeUpdateDTO;
import com.jobtrack.application.pojo.vo.HistoryVO;
import com.jobtrack.application.pojo.vo.ResumeListVO;
import com.jobtrack.application.pojo.vo.ResumeVO;
import com.jobtrack.application.service.ApplicationService;
import com.jobtrack.application.service.ResumeService;
import com.jobtrack.common.api.PageResult;
import com.jobtrack.common.context.TenantContext;
import com.jobtrack.common.exception.BusinessException;
import com.jobtrack.system.mapper.SysUserMapper;
import com.jobtrack.system.pojo.po.SysUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 管理员查看指定用户的投递与简历库。
 *
 * <p>管理员请求默认携带自己的租户上下文，这里临时切换到目标用户的租户，
 * 复用现有的投递 / 简历查询逻辑，返回结果不会串到管理员自己的工作区。</p>
 */
@RestController
@RequestMapping("/api/admin/users/{userId}")
@RequiredArgsConstructor
public class AdminUserDataController {

    private final SysUserMapper userMapper;
    private final ApplicationService applicationService;
    private final ResumeService resumeService;

    @GetMapping("/applications")
    public PageResult<ApplicationRecord> applications(
            @PathVariable Long userId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        SysUser user = requireUser(userId);
        return inTenant(user.getTenantId(), () -> applicationService.list(search, category, status,
                channel, priority, year, from, to, page, pageSize));
    }

    /** 管理员代目标用户新增投递，复用普通用户的创建逻辑。 */
    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationRecord createApplication(@PathVariable Long userId,
                                               @Valid @RequestBody ApplicationDTO request) {
        SysUser user = requireUser(userId);
        return inTenant(user.getTenantId(), () -> applicationService.create(request));
    }

    /** 管理员查看目标用户的单条投递详情。 */
    @GetMapping("/applications/{applicationId}")
    public ApplicationRecord application(@PathVariable Long userId,
                                         @PathVariable Long applicationId) {
        SysUser user = requireUser(userId);
        return inTenant(user.getTenantId(), () -> applicationService.get(applicationId));
    }

    /** 管理员查看目标用户投递的状态时间线。 */
    @GetMapping("/applications/{applicationId}/history")
    public HistoryVO applicationHistory(@PathVariable Long userId,
                                        @PathVariable Long applicationId) {
        SysUser user = requireUser(userId);
        return inTenant(user.getTenantId(), () -> applicationService.history(applicationId));
    }

    /** 管理员代目标用户补全状态时间线。 */
    @PostMapping("/applications/{applicationId}/history")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> appendApplicationHistory(@PathVariable Long userId,
                                                        @PathVariable Long applicationId,
                                                        @RequestBody Map<String, String> body) {
        SysUser user = requireUser(userId);
        return inTenant(user.getTenantId(), () -> {
            List<com.jobtrack.application.pojo.vo.ApplicationChangeVO> items =
                    applicationService.appendHistory(applicationId,
                            body.get("status"), body.get("changedAt"));
            return Map.of("items", items);
        });
    }

    /** 管理员代目标用户修改投递。 */
    @PutMapping("/applications/{applicationId}")
    public ApplicationRecord updateApplication(@PathVariable Long userId,
                                               @PathVariable Long applicationId,
                                               @Valid @RequestBody ApplicationUpdateDTO request) {
        SysUser user = requireUser(userId);
        return inTenant(user.getTenantId(), () -> applicationService.update(applicationId, request));
    }

    /** 管理员代目标用户删除投递（与普通用户一致，进入目标用户的回收站）。 */
    @DeleteMapping("/applications/{applicationId}")
    public Map<String, Object> deleteApplication(@PathVariable Long userId,
                                                 @PathVariable Long applicationId) {
        SysUser user = requireUser(userId);
        return inTenant(user.getTenantId(), () -> {
            applicationService.delete(applicationId);
            return Map.of("deleted", 1);
        });
    }

    @GetMapping("/resumes")
    public ResumeListVO resumes(@PathVariable Long userId,
                                @RequestParam(required = false) String search) {
        SysUser user = requireUser(userId);
        return inTenant(user.getTenantId(), () -> resumeService.list(search));
    }

    /** 管理员代目标用户编辑投递时，使用该用户历史公司名作为补全建议。 */
    @GetMapping("/applications/companies")
    public Map<String, Object> applicationCompanies(@PathVariable Long userId,
                                                    @RequestParam(required = false) String search) {
        SysUser user = requireUser(userId);
        return inTenant(user.getTenantId(),
                () -> Map.of("items", applicationService.companySuggestions(search)));
    }

    /** 管理员代目标用户上传简历。 */
    @PostMapping("/resumes")
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeVO createResume(@PathVariable Long userId,
                                 @RequestParam(required = false) String name,
                                 @RequestParam(required = false) String tags,
                                 @RequestParam("file") MultipartFile file) {
        SysUser user = requireUser(userId);
        return inTenant(user.getTenantId(), () -> resumeService.create(name, tags, file));
    }

    /** 管理员代目标用户修改简历名称、标签或置顶状态。 */
    @PutMapping("/resumes/{resumeId}")
    public ResumeVO updateResume(@PathVariable Long userId,
                                 @PathVariable Long resumeId,
                                 @RequestBody ResumeUpdateDTO request) {
        SysUser user = requireUser(userId);
        return inTenant(user.getTenantId(), () -> resumeService.update(resumeId, request));
    }

    /** 管理员代目标用户删除简历。 */
    @DeleteMapping("/resumes/{resumeId}")
    public Map<String, Object> deleteResume(@PathVariable Long userId,
                                            @PathVariable Long resumeId) {
        SysUser user = requireUser(userId);
        return inTenant(user.getTenantId(), () -> {
            resumeService.delete(resumeId);
            return Map.of("deleted", 1);
        });
    }

    @GetMapping("/resumes/{resumeId}/file")
    public ResponseEntity<byte[]> resumeFile(@PathVariable Long userId,
                                             @PathVariable Long resumeId) {
        SysUser user = requireUser(userId);
        return inTenant(user.getTenantId(), () -> {
            Resume resume = resumeService.entity(resumeId);
            byte[] content = resumeService.readFile(resume);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(resumeService.contentType(resume)));
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                    contentDisposition("attachment", resume.getFileName()));
            headers.setCacheControl("no-store");
            return new ResponseEntity<>(content, headers, HttpStatus.OK);
        });
    }

    private SysUser requireUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("user_not_found", "用户不存在");
        }
        return user;
    }

    private <T> T inTenant(Long tenantId, Supplier<T> action) {
        Long previous = TenantContext.get();
        TenantContext.set(tenantId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(previous);
            }
        }
    }

    private String contentDisposition(String mode, String fileName) {
        String name = fileName == null || fileName.isBlank() ? "resume" : fileName;
        String ascii = name.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "_");
        if (ascii.isBlank()) {
            ascii = "resume";
        }
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return mode + "; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }
}
