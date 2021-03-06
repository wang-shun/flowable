package com.flowable.core.service;

import java.util.List;

import com.flowable.common.service.IBaseService;
import com.flowable.core.bean.ActBizInfoDelayTime;

public interface ActBizInfoDelayTimeService extends IBaseService<ActBizInfoDelayTime> {

    /**
     * 根据工单id查询最新记录
     *
     * @param bizId
     * @return
     */
    ActBizInfoDelayTime findActBizInfoByBizId(String bizId, String taskName);

    void saveOrUpdate(ActBizInfoDelayTime actBizInfo);

    List<ActBizInfoDelayTime> findActBizInfoDelayTime(ActBizInfoDelayTime delayTime);
}
