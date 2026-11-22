package com.jobtrack.system.service.impl;

import com.jobtrack.common.exception.BusinessException;
import com.jobtrack.system.mapper.UserProfileMapper;
import com.jobtrack.system.pojo.po.UserProfile;
import com.jobtrack.system.service.ContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
/**
 * 手机号/邮箱占用检查。
 * 手机号按数字尾号兼容匹配（历史数据可能缺少国家区号），邮箱忽略大小写。
 */
@Service
@RequiredArgsConstructor
public class ContactServiceImpl implements ContactService {

    private final UserProfileMapper profileMapper;

    public void ensureAvailable(Long selfUserId, String phone, String email) {
        String cleanPhone = phone == null ? "" : phone.trim();
        String cleanEmail = email == null ? "" : email.trim();
        for (UserProfile saved : profileMapper.selectActiveContactsIgnoreTenant()) {
            if (selfUserId != null && selfUserId.equals(saved.getUserId())) {
                continue;
            }
            if (!cleanPhone.isEmpty() && samePhone(cleanPhone, saved.getPhone())) {
                throw conflict();
            }
            if (!cleanEmail.isEmpty() && saved.getEmail() != null
                    && cleanEmail.equalsIgnoreCase(saved.getEmail().trim())) {
                throw conflict();
            }
        }
    }

    private BusinessException conflict() {
        return BusinessException.conflict("contact_taken",
                "手机号或邮箱已被其他账号使用，禁止修改");
    }

    /** 兼容账号中只保存本地号码、没有国家区号的历史数据。 */
    private boolean samePhone(String left, String right) {
        String a = phoneDigits(left);
        String b = phoneDigits(right);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return a.equals(b)
                || (a.length() >= 7 && b.length() >= 7
                && (a.endsWith(b) || b.endsWith(a)));
    }

    private String phoneDigits(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }
}
