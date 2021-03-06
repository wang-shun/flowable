package com.flowable.core.service;

import java.util.List;
import java.util.Map;

import com.flowable.common.service.IBaseService;
import com.flowable.core.bean.BizInfoConf;

/**
 * @author 2622
 * @time 2016年5月30日
 * @email lukw@eastcom-sw.com
 */
public interface BizInfoConfService extends IBaseService<BizInfoConf> {

    List<BizInfoConf> findBizInfoConf(BizInfoConf bizInfoConf);

    /**
     * 当前工单中我的待办
     *
     * @param bizId
     * @return
     */
    BizInfoConf getMyWork(String bizId);

    void saveOrUpdate(BizInfoConf bizInfoConf);

    /**
     * 工单转派 (不通过工作流)
     *
     * @param map
     */
    void turnTask(Map<String, Object> map);

}
