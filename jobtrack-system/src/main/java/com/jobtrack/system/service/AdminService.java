package com.jobtrack.system.service;

import com.jobtrack.system.pojo.dto.AccessTokenCreateDTO;
import com.jobtrack.system.pojo.dto.AdminPasswordDTO;
import com.jobtrack.system.pojo.dto.AdminStatusDTO;
import com.jobtrack.system.pojo.dto.AdminUserUpdateDTO;
import com.jobtrack.system.pojo.dto.ContactUpdateDTO;
import com.jobtrack.system.pojo.dto.UserProfileDTO;
import com.jobtrack.system.pojo.vo.AccessTokenLogPageVO;
import com.jobtrack.system.pojo.vo.AccessTokenVO;
import com.jobtrack.system.pojo.vo.AdminUserDetailVO;
import com.jobtrack.system.pojo.vo.AdminUserPageVO;
import com.jobtrack.system.pojo.vo.AdminUserVO;
import com.jobtrack.system.pojo.vo.OnlineUserVO;
import com.jobtrack.system.pojo.vo.PermissionVO;
import com.jobtrack.system.pojo.vo.UserAgreementVO;
import com.jobtrack.system.pojo.vo.UserProfileVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 管理员控制台：跨租户用户管理、在线用户与权限授权。
 */
public interface AdminService {

    AdminUserPageVO listUsers(String search, String status, int page, int pageSize);

    AdminUserDetailVO detail(Long userId);

    UserAgreementVO userAgreement(Long userId);

    AdminUserVO updateUser(Long userId, AdminUserUpdateDTO request);

    UserProfileVO saveProfile(Long userId, UserProfileDTO request);

    UserProfileVO saveContact(Long userId, ContactUpdateDTO request);

    UserProfileVO uploadAvatar(Long userId, MultipartFile file);

    AdminUserVO resetPassword(Long userId, AdminPasswordDTO request);

    AdminUserVO updateStatus(Long operatorId, Long userId, AdminStatusDTO request);

    void deleteUser(Long operatorId, Long userId);

    List<AccessTokenVO> tokens(Long userId);

    AccessTokenVO createToken(Long userId, AccessTokenCreateDTO request);

    AccessTokenVO setTokenEnabled(Long userId, Long tokenId, boolean enabled);

    void deleteToken(Long userId, Long tokenId);

    AccessTokenLogPageVO tokenLogs(Long userId, int page, int pageSize, String name, String result);

    List<OnlineUserVO> onlineUsers();

    int kick(Long operatorId, Long userId);

    List<PermissionVO> permissions();

    List<String> userPermissions(Long userId);

    AdminUserVO grantPermission(Long operatorId, Long userId, String code);

    AdminUserVO revokePermission(Long operatorId, Long userId, String code);
}
