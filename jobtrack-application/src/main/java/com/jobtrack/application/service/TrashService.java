package com.jobtrack.application.service;

import com.jobtrack.application.pojo.po.ApplicationRecord;
import com.jobtrack.application.pojo.po.DeletedApplication;
import com.jobtrack.application.pojo.vo.TrashListVO;

/**
 * 回收站。
 */
public interface TrashService {

    DeletedApplication moveToTrash(ApplicationRecord record);

    TrashListVO list();

    ApplicationRecord restore(Long trashId);

    int delete(Long trashId);

    int empty();
}
