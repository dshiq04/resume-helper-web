package com.jobtrack.system.service;

import java.util.Map;

/**
 * 前后端共享的公共配置（选项列表、版本、上传上限等）。
 */
public interface PublicConfigService {

    Map<String, Object> config();
}
