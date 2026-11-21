package com.jobtrack.application.service;

import com.jobtrack.application.pojo.ApplicationChangeView;
import com.jobtrack.application.pojo.ApplicationRecord;
import com.jobtrack.application.pojo.ApplicationRequest;
import com.jobtrack.application.pojo.ApplicationUpdateRequest;
import com.jobtrack.application.pojo.HistoryResponse;
import com.jobtrack.application.pojo.YearList;
import com.jobtrack.common.api.PageResult;

import java.util.List;

/**
 * 投递记录的增删改查与批量操作。
 */
public interface ApplicationService {

    PageResult<ApplicationRecord> list(String search, String category, String status,
                                       String channel, String priority, Integer year,
                                       String from, String to, int page, int pageSize);

    YearList years();

    ApplicationRecord create(ApplicationRequest request);

    ApplicationRecord get(Long id);

    ApplicationRecord update(Long id, ApplicationUpdateRequest request);

    void delete(Long id);

    int batchDelete(List<Long> ids);

    int batchStatus(List<Long> ids, String status);

    int batchCategory(List<Long> ids, String category);

    List<String> companySuggestions(String search);

    HistoryResponse history(Long id);

    List<ApplicationChangeView> appendHistory(Long id, String status, String changedAt);
}
