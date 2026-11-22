package com.jobtrack.application.service;

import com.jobtrack.application.pojo.vo.ApplicationChangeVO;
import com.jobtrack.application.pojo.po.ApplicationRecord;
import com.jobtrack.application.pojo.dto.ApplicationDTO;
import com.jobtrack.application.pojo.dto.ApplicationUpdateDTO;
import com.jobtrack.application.pojo.vo.HistoryVO;
import com.jobtrack.application.pojo.vo.YearListVO;
import com.jobtrack.common.api.PageResult;

import java.util.List;

/**
 * 投递记录的增删改查与批量操作。
 */
public interface ApplicationService {

    PageResult<ApplicationRecord> list(String search, String category, String status,
                                       String channel, String priority, Integer year,
                                       String from, String to, int page, int pageSize);

    YearListVO years();

    ApplicationRecord create(ApplicationDTO request);

    ApplicationRecord get(Long id);

    ApplicationRecord update(Long id, ApplicationUpdateDTO request);

    void delete(Long id);

    int batchDelete(List<Long> ids);

    int batchStatus(List<Long> ids, String status);

    int batchCategory(List<Long> ids, String category);

    List<String> companySuggestions(String search);

    HistoryVO history(Long id);

    List<ApplicationChangeVO> appendHistory(Long id, String status, String changedAt);
}
