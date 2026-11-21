package com.jobtrack.application.service;

import com.jobtrack.application.pojo.ApplicationRecord;
import com.jobtrack.application.pojo.DeletedApplication;
import com.jobtrack.application.pojo.TrashListResponse;

/**
 * 回收站。
 */
public interface TrashService {

    DeletedApplication moveToTrash(ApplicationRecord record);

    TrashListResponse list();

    ApplicationRecord restore(Long trashId);

    int delete(Long trashId);

    int empty();
}
