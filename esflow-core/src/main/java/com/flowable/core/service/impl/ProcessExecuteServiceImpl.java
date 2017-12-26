package com.flowable.core.service.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.*;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.service.impl.persistence.entity.TaskEntityImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import com.flowable.common.exception.ServiceException;
import com.flowable.common.utils.DateUtils;
import com.flowable.common.utils.PageHelper;
import com.flowable.core.bean.AbstractVariable;
import com.flowable.core.bean.AbstractVariableInstance;
import com.flowable.core.bean.BizFile;
import com.flowable.core.bean.BizInfo;
import com.flowable.core.bean.BizInfoConf;
import com.flowable.core.bean.BizLog;
import com.flowable.core.bean.ProcessVariable;
import com.flowable.core.bean.ProcessVariableInstance;
import com.flowable.core.bean.TaskVariable;
import com.flowable.core.bean.TaskVariableInstance;
import com.flowable.core.bean.auth.SystemRole;
import com.flowable.core.service.BizInfoConfService;
import com.flowable.core.service.IBizFileService;
import com.flowable.core.service.IBizInfoService;
import com.flowable.core.service.IBizLogService;
import com.flowable.core.service.IProcessDefinitionService;
import com.flowable.core.service.IProcessExecuteService;
import com.flowable.core.service.IProcessVariableService;
import com.flowable.core.service.IVariableInstanceService;
import com.flowable.core.service.IVariableInstanceService.VariableLoadType;
import com.flowable.core.service.auth.ISystemUserService;
import com.flowable.core.util.Constants;
import com.flowable.core.util.UploadFileUtil;
import com.flowable.core.util.WebUtil;
import com.flowable.core.util.WorkOrderUtil;

@Service
@Transactional(readOnly = true)
public class ProcessExecuteServiceImpl implements IProcessExecuteService {

    private Logger log = LoggerFactory.getLogger(IProcessExecuteService.class);

    @Autowired
    private IProcessVariableService variableService;

    @Autowired
    private IVariableInstanceService instanceService;

    @Autowired
    private IBizInfoService bizInfoService;

    @Autowired
    private IBizLogService logService;

    @Autowired
    private IBizFileService bizFileService;

    @Autowired
    private IProcessDefinitionService processDefinitionService;

    @Autowired
    private ISystemUserService sysUserService;

    @Autowired
    private BizInfoConfService bizInfoConfService;

    @Override
    public Map<String, Object> loadBizLogInput(String logId) {

        BizLog logBean = logService.getBizLogById(logId);
        Map<String, Object> results = new HashMap<String, Object>();
        if (logBean == null) {
            return results;
        }
        List<AbstractVariableInstance> values = instanceService.loadValueByLog(logBean);
        if (CollectionUtils.isNotEmpty(values)) {
            values.forEach(instance -> results.put(instance.getVariable().getName(), instance.getValue()));
        }
        return results;
    }

    /**
     * 加载所有的流程
     */
    @Override
    public Map<String, Object> loadProcessList() {
        return processDefinitionService.loadProcessList();
    }

    /**
     * 根据流程定义ID获取流程名
     */
    public String getProcessDefinitionName(String procDefId) {
        return processDefinitionService.getProcDefById(procDefId).getName();
    }

    @Override
    public PageHelper<BizInfo> queryMyBizInfos(String targe, Map<String, Object> params, PageHelper<BizInfo> page) {

        log.info(" queryMyBizInfos begin ------");
        // 转换查询时间
        String ct1 = (String) params.get("createTime");
        String ct2 = (String) params.get("createTime2");
        Date dt1 = DateUtils.parseDate(ct1);
        Date dt2 = DateUtils.parseDate(ct2);
        if (dt1 == null) {
            params.remove("createTime");
        } else {
            params.put("createTime", dt1);
        }
        if (dt2 == null) {
            params.remove("createTime2");
        } else {
            params.put("createTime2", dt2);
        }
        if ("myCreate".equalsIgnoreCase(targe)) {
            params.remove("createUser");
        } else if ("myClose".equalsIgnoreCase(targe)) {
            params.put("status", Constants.BIZ_END);
        } else if ("myWork".equals(targe)) {
            params.put("checkAssignee", "checkAssignee");
        } else if ("myTemp".equalsIgnoreCase(targe)) {
            params.put("status", "草稿");
            params.remove("createUser");
        }
        return bizInfoService.getBizInfoList(params, page);
    }

    /**
     * 获取当前需要填写的属性列表<br>
     * 如果没有工单号则获取模板的公共属性<br>
     * 如果有工单号，则获取到工单当前需要处理的流程，然后再加载属性
     *
     * @param tempID
     * @return
     * @throws ServiceException
     */
    @Override
    public List<AbstractVariable> loadHandleProcessVariables(String tempID) {

        List<AbstractVariable> result = new ArrayList<AbstractVariable>();
        List<ProcessVariable> list = variableService.loadVariables(tempID, -1);
        if (list != null) {
            result.addAll(list);
        }
        return result;
    }

    @Override
    public List<AbstractVariable> loadHandleProcessValBean(BizInfo bean, String taskID) {

        List<AbstractVariable> result = new ArrayList<AbstractVariable>();
        Task task = processDefinitionService.getTaskBean(taskID);
        // 获取到当前工单的处理环节(任务)
        if (task != null) {
            List<TaskVariable> taskVariabels = variableService.loadTaskVariables(bean.getProcessDefinitionId(),
                    processDefinitionService.getWorkOrderVersion(bean), task.getTaskDefinitionKey());
            if (taskVariabels != null) {
                result.addAll(taskVariabels);
            }
        } else {
            return loadHandleProcessVariables(bean.getProcessDefinitionId());
        }
        return result;
    }

    public List<AbstractVariable> loadProcessValBean(BizInfo bean) {

        List<AbstractVariable> result = new ArrayList<AbstractVariable>();
        String processDefinitionId = bean.getProcessDefinitionId();
        Integer vesion = 0;
        if (StringUtils.isNotBlank(processDefinitionId)) {
            String[] definition = processDefinitionId.split(":");
            vesion = Integer.valueOf(definition[1]);
        }
        List<ProcessVariable> list = variableService.loadVariables(bean.getProcessDefinitionId(), vesion);
        if (list != null) {
            result.addAll(list);
        }
        return result;
    }

    /**
     * 签收工单
     *
     * @param bizInfo
     * @param loginUser
     * @return
     */
    private BizInfo sign(BizInfo bizInfo, String loginUser) {

        BizInfoConf bizInfoConf = this.bizInfoConfService.getMyWork(bizInfo.getId());
        String taskId = bizInfoConf.getTaskId();
        if (StringUtils.isEmpty(taskId)) {
            throw new ServiceException("找不到任务ID");
        }
        String username = WebUtil.getLoginUser() == null ? loginUser : WebUtil.getLoginUser().getUsername();
        processDefinitionService.claimTask(bizInfo, taskId, username);
        bizInfoConf.setTaskAssignee(username);
        bizInfoService.updateBizInfo(bizInfo);
        bizInfo.setTaskAssignee(username);
        this.bizInfoConfService.saveOrUpdate(bizInfoConf);
        return bizInfo;
    }

    /**
     * 根据工单号修改工单属性，只修改工单的业务属性
     */
    @Override
    @Transactional
    public BizInfo update(Map<String, Object> params) {

        String workNumber = (String) params.get("base.workNumber");
        if (StringUtils.isEmpty(workNumber)) {
            throw new ServiceException("工单号为空");
        }
        BizInfo bean = bizInfoService.get(workNumber);
        if (bean == null) {
            throw new ServiceException("找不到工单");
        }
        String createUser = bean.getCreateUser();
        if (!createUser.equals(WebUtil.getLoginUser().getUsername())) {
            throw new ServiceException("只有当前创单用户才能修改");
        }
        List<AbstractVariable> processValList = loadHandleProcessValBean(bean, null);
        List<AbstractVariableInstance> list4 = instanceService.loadInstances(bean);
        // 设置流程参数

        for (AbstractVariable proAbs : processValList) {
            // 如果数据为空则不更新
            String value = (String) params.get(proAbs.getName());
            if (StringUtils.isEmpty(value)) {
                continue;
            }
            AbstractVariableInstance valueBean = null;
            for (AbstractVariableInstance instAbs : list4) {
                if (instAbs.getVariable().getId().equals(proAbs.getId())) {
                    valueBean = instAbs;
                    break;
                }
            }
            if (valueBean == null) {
                valueBean = proAbs instanceof ProcessVariable ? new ProcessVariableInstance()
                        : new TaskVariableInstance();
                valueBean.setProcessInstanceId(bean.getProcessInstanceId());
                valueBean.setValue(value);
                valueBean.setVariable(proAbs);
                instanceService.addProcessInstance(valueBean);
            } else {
                valueBean.setValue(value);
                instanceService.updateProcessInstance(valueBean);
            }
        }
        return bean;
    }

    @Override
    @Transactional
    public BizInfo createBizDraft(Map<String, Object> params, MultiValueMap<String, MultipartFile> multiValueMap,
                                  boolean startProc, String[] deleFileId) {

        String source = (String) params.get("$source");
        source = StringUtils.isBlank(source) ? "人工发起" : source;
        String procDefId = (String) params.get("base.tempID");
        String dt1 = (String) params.get("base.limitTime");
        String createUser = (String) params.get("base.createUser");
        String tempBizId = (String) params.get("tempBizId");
        Date limitTime = DateUtils.parseDate(dt1);
        Date now = new Date();
        BizInfo bizInfo = null;
        BizInfoConf bizInfoConf = null;
        if (StringUtils.isNotBlank(tempBizId)) {
            bizInfo = bizInfoService.get(tempBizId);
            bizInfoConf = this.bizInfoConfService.get(bizInfo.getId());
        } else {
            bizInfo = new BizInfo();
            bizInfo.setWorkNum(WorkOrderUtil.builWorkNumber(procDefId));
        }
        if (bizInfoConf == null) {
            bizInfoConf = new BizInfoConf();
            bizInfoConf.setBizInfo(bizInfo);
        }
        bizInfo.setSource(source);
        bizInfo.setLimitTime(limitTime);
        bizInfo.setProcessDefinitionId(procDefId);
        bizInfo.setBizType(getProcessDefinitionName(procDefId));
        bizInfo.setStatus(Constants.BIZ_TEMP); // TODO 临时填值
        bizInfo.setCreateTime(now);
        if (StringUtils.isNotBlank(createUser)) {
            bizInfo.setCreateUser(createUser);
            bizInfoConf.setTaskAssignee(createUser);
        } else {
            bizInfo.setCreateUser(WebUtil.getLoginUser().getUsername());
            bizInfoConf.setTaskAssignee(WebUtil.getLoginUser().getUsername());
        }
        bizInfo.setTitle((String) params.get("base.workTitle"));
        bizInfoService.addBizInfo(bizInfo);
        bizInfoConf.setBizInfo(bizInfo);
        this.bizInfoConfService.saveOrUpdate(bizInfoConf);
        if (startProc) {
            startProc(bizInfo, bizInfoConf, params, now);
        } else {
            List<AbstractVariable> processValList = loadHandleProcessVariables(procDefId);
            saveOrUpdateVars(bizInfo, bizInfoConf, processValList, params, now);
        }
        /* 处理附件 */
        saveFile(multiValueMap, now, bizInfo, null);
        this.deleBizFiles(deleFileId);
        return bizInfo;
    }

    @Override
    @Transactional
    public BizInfo updateBiz(String id, Map<String, Object> params, MultiValueMap<String, MultipartFile> fileMap,
                             boolean startProc) {

        BizInfo bizInfo = bizInfoService.get(id);
        BizInfoConf bizInfoConf = this.bizInfoConfService.getMyWork(id);
        Date now = new Date();
        saveFile(fileMap, now, bizInfo, null);
        if (StringUtils.isNotBlank(bizInfo.getProcessInstanceId()) && StringUtils.isNotBlank(bizInfoConf.getTaskId())) {
            reSubmit(params, bizInfo, bizInfoConf);
        } else if (startProc) {
            startProc(bizInfo, bizInfoConf, params, now);
        } else {
            List<AbstractVariable> variables = this.loadHandleProcessValBean(bizInfo, bizInfoConf.getTaskId());
            saveOrUpdateVars(bizInfo, bizInfoConf, variables, params, now);
        }
        return bizInfo;
    }

    public BizInfo startProc(BizInfo bizInfo, BizInfoConf bizInfoConf, Map<String, Object> params, Date now) {

        String procDefId = bizInfo.getProcessDefinitionId();
        Map<String, Object> variables = new HashMap<String, Object>();
        List<AbstractVariable> processValList = loadHandleProcessVariables(procDefId);
        // 设置流程参数
        for (AbstractVariable abc : processValList) {
            if (abc.isProcessVariable() != null && abc.isProcessVariable()) {
                Object value = WorkOrderUtil.convObject((String) params.get(abc.getName()), abc.getViewComponent());
                variables.put(abc.getName(), value);
            }
        }
        variables.put("SYS_FORMTYPE", params.get(IProcessExecuteService.systemFormType));
        variables.put("SYS_BUTTON_VALUE", params.get("base.buttonId"));
        variables.put("SYS_BIZ_CREATEUSER", bizInfo.getCreateUser());
        variables.put(Constants.SYS_BIZ_ID, bizInfo.getId());
        ProcessInstance instance = processDefinitionService.newProcessInstance(procDefId, variables);
        bizInfo.setProcessInstanceId(instance.getId());
        this.processDefinitionService.autoClaim(instance.getId());// TODO任务创建时的自动签收

        TaskEntityImpl task = new TaskEntityImpl(); // 开始节点没有任务对象
        task.setId("START");
        task.setName((String) params.get("base.handleName"));
        writeBizLog(bizInfo, task, now, params);
        updateBizTaskInfo(bizInfo, bizInfoConf);
        this.bizInfoConfService.saveOrUpdate(bizInfoConf);
        // 保存流程字段
        saveOrUpdateVars(bizInfo, bizInfoConf, processValList, params, now);
        return bizInfo;
    }

    private void reSubmit(Map<String, Object> params, BizInfo bizInfo, BizInfoConf bizInfoConf) {

        Date now = new Date();
        Task task = processDefinitionService.getTaskBean(bizInfoConf.getTaskId());
        Map<String, Object> variables = new HashMap<String, Object>();
        // 从新提交时 参数有流程变量 和 任务变量
        List<AbstractVariable> processValList = loadHandleProcessValBean(bizInfo, task.getId());
        processValList.addAll(loadHandleProcessVariables(bizInfo.getProcessDefinitionId()));
        // 设置流程参数
        for (AbstractVariable abc : processValList) {
            if (abc.isProcessVariable() != null && abc.isProcessVariable()) {
                Object value = WorkOrderUtil.convObject((String) params.get(abc.getName()), abc.getViewComponent());
                variables.put(abc.getName(), value);
            }
        }
        variables.put("SYS_FORMTYPE", params.get(IProcessExecuteService.systemFormType));
        variables.put("SYS_BUTTON_VALUE", params.get("base.buttonId"));
        variables.put("SYS_BIZ_ID", bizInfo.getId());
        variables.put("handleUser", params.get("handleUser"));
        variables.put("SYS_BIZ_CREATEUSER", bizInfo.getCreateUser());
        // 增加处理结果
        processDefinitionService.completeTask(bizInfo, bizInfoConf.getTaskId(), WebUtil.getLoginUser(), variables);
        // 保存业务字段
        saveOrUpdateVars(bizInfo, bizInfoConf, processValList, params, now);
        this.bizInfoConfService.saveOrUpdate(bizInfoConf);
        updateBizTaskInfo(bizInfo, bizInfoConf);
        // 保存工单信息
        bizInfoService.updateBizInfo(bizInfo);
        /* 保存日志 */
        writeBizLog(bizInfo, task, now, params);
    }

    @Override
    public void saveOrUpdateVars(BizInfo bizInfo, BizInfoConf bizInfoConf, List<AbstractVariable> processValList,
                                 Map<String, Object> params, Date now) {

        String procInstId = bizInfo.getProcessInstanceId();
        String taskId = bizInfoConf.getTaskId();
        Map<String, ? extends AbstractVariableInstance> currentVars = instanceService.getVarMap(bizInfo, bizInfoConf,
                VariableLoadType.UPDATABLE);
        for (AbstractVariable proAbs : processValList) {

            String proName = proAbs.getName().trim();
            String component = proAbs.getViewComponent();
            String value = "REQUIREDFILE".equalsIgnoreCase(component) ? "file" : (String) params.get(proName);
            if (StringUtils.isEmpty(value)) {
                continue;
            }
            AbstractVariableInstance valueBean = currentVars.get(proAbs.getName());
            if (null != valueBean) {
                valueBean.setValue(value);
                valueBean.setCreateTime(new Date());
                instanceService.updateProcessInstance(valueBean);
            } else {
                valueBean = proAbs instanceof ProcessVariable ? new ProcessVariableInstance()
                        : new TaskVariableInstance();
                valueBean.setProcessInstanceId(procInstId);
                valueBean.setValue(value);
                valueBean.setCreateTime(now);
                valueBean.setVariable(proAbs);
                if (valueBean instanceof TaskVariableInstance) {
                    ((TaskVariableInstance) valueBean).setTaskId(taskId);
                } else {
                    ((ProcessVariableInstance) valueBean).setBizId(bizInfo.getId());
                }
                instanceService.addProcessInstance(valueBean);
            }
        }
    }

    private void deleBizFiles(String[] ids) {

        File file;
        BizFile bizFile;
        List<String> filePathList = new ArrayList<String>();
        if (ArrayUtils.isNotEmpty(ids)) {
            for (String id : ids) {
                bizFile = bizFileService.getBizFileById(id);
                filePathList.add(bizFile.getPath());
            }
            bizFileService.deleteBizFile(ids);
            for (String filePath : filePathList) {
                file = new File(filePath);
                FileUtils.deleteQuietly(file);
            }
        }
    }

    @Override
    public void updateBizTaskInfo(BizInfo bizInfo, BizInfoConf bizInfoConf) {

        List<Task> taskList = processDefinitionService.getNextTaskInfo(bizInfo.getProcessInstanceId());
        // 如果nextTaskInfo返回null，标示流程已结束
        if (CollectionUtils.isEmpty(taskList)) {
            bizInfoConf = this.bizInfoConfService.getMyWork(bizInfo.getId());
            bizInfoConf.setTaskId("END");
            bizInfo.setTaskName("已结束");
            bizInfo.setStatus(Constants.BIZ_END);
            bizInfo.setTaskDefKey(Constants.BIZ_END);
            bizInfoConf.setTaskAssignee("-");
        } else {
            Task taskInfo = taskList.get(0);
            bizInfo.setStatus(taskInfo.getName());
            bizInfoConf.setTaskId(taskInfo.getId());
            bizInfo.setTaskName(taskInfo.getName());
            bizInfo.setTaskDefKey(taskInfo.getTaskDefinitionKey());
            bizInfoConf.setTaskAssignee(taskInfo.getAssignee());
            StringBuffer taskIds = new StringBuffer(taskInfo.getId() + ",");
            StringBuffer taskAssignee = new StringBuffer();
            if (StringUtils.isNotBlank(taskInfo.getAssignee())) {
                taskAssignee.append(taskInfo.getAssignee() + ",");
            }
            BizInfoConf bizConf = null;
            if (taskList.size() > 1) {
                for (int i = 1; i < taskList.size(); i++) {
                    bizConf = new BizInfoConf();
                    taskInfo = taskList.get(i);
                    bizConf.setTaskId(taskInfo.getId());
                    bizConf.setTaskAssignee(taskInfo.getAssignee());
                    bizConf.setBizInfo(bizInfo);
                    this.bizInfoConfService.saveOrUpdate(bizConf);
                    taskIds.append(taskIds + taskInfo.getId() + ",");
                    if (StringUtils.isNotBlank(taskInfo.getAssignee())) {
                        taskAssignee.append(taskAssignee + taskInfo.getAssignee() + ",");
                    }
                }
            }
            bizInfo.setTaskId(taskIds.substring(0, taskIds.lastIndexOf(",")));
            String assignee = StringUtils.isBlank(taskAssignee.toString()) ? null : taskAssignee.substring(0, taskAssignee.lastIndexOf(","));
            bizInfo.setTaskAssignee(assignee);
        }
    }

    /**
     * 提交工单，实现流转
     *
     * @param params
     * @param fileMap
     * @return
     * @throws ServiceException
     */
    @Override
    @Transactional
    public BizInfo submit(Map<String, Object> params, MultiValueMap<String, MultipartFile> fileMap) {

        log.info("params :" + params);
        Date now = new Date();
        BizInfo bizInfo = null;
        BizInfoConf bizInfoConf = null;
        String buttonId = (String) params.get("base.buttonId");
        String bizId = (String) params.get("base.workNumber");
        if (StringUtils.isNotBlank(bizId)) {
            bizInfo = bizInfoService.getByBizId(bizId);
        }
        if (null == bizInfo) {
            throw new ServiceException("工单不存在");
        }
        bizInfoConf = this.bizInfoConfService.getMyWork(bizInfo.getId());
        if (bizInfoConf == null) {
            throw new ServiceException("请确认是否有提交工单权限");
        }
        String taskId = bizInfoConf.getTaskId();
        Task task = processDefinitionService.getTaskBean(taskId);
        if (Constants.SIGN.equalsIgnoreCase(buttonId)) {
            sign(bizInfo, (String) params.get("loginUser"));
        } else {
            Map<String, Object> variables = new HashMap<String, Object>();
            List<AbstractVariable> processValList = loadHandleProcessValBean(bizInfo, taskId);
            // 设置流程参数
            for (AbstractVariable abc : processValList) {
                if (abc.isProcessVariable() != null && abc.isProcessVariable()) {
                    Object value = WorkOrderUtil.convObject((String) params.get(abc.getName()), abc.getViewComponent());
                    variables.put(abc.getName(), value);
                }
            }
            variables.put("SYS_FORMTYPE", params.get(IProcessExecuteService.systemFormType));
            variables.put("SYS_BUTTON_VALUE", buttonId);
            variables.put("SYS_BIZ_ID", bizId);
            ArrayList<String> usernames = this.getUsernames((String) params.get("handleUser"));
            // 会签
            variables.put(Constants.COUNTER_SIGN, usernames);
            variables.put("SYS_BIZ_CREATEUSER", bizInfo.getCreateUser());
            // 增加处理结果
            processDefinitionService.completeTask(bizInfo, taskId, WebUtil.getLoginUser(), variables);
            // 保存业务字段
            saveOrUpdateVars(bizInfo, bizInfoConf, processValList, params, now);
            updateBizTaskInfo(bizInfo, bizInfoConf);
            bizInfoService.updateBizInfo(bizInfo);
            this.bizInfoConfService.saveOrUpdate(bizInfoConf);
        }
        saveFile(fileMap, now, bizInfo, task);
        writeBizLog(bizInfo, task, now, params);
        return bizInfo;
    }

    /**
     * 附件保存
     *
     * @param fileMap
     * @param now
     * @param bizInfo
     * @param task
     */
    private void saveFile(MultiValueMap<String, MultipartFile> fileMap, Date now, BizInfo bizInfo, Task task) {

        if (MapUtils.isNotEmpty(fileMap)) {
            for (String fileCatalog : fileMap.keySet()) {
                List<MultipartFile> files = (List<MultipartFile>) fileMap.get(fileCatalog);
                if (CollectionUtils.isNotEmpty(files)) {
                    files.forEach(file -> {
                        BizFile bizFile = UploadFileUtil.saveFile(file);
                        if (bizFile != null) {
                            bizFile.setCreateDate(now);
                            bizFile.setFileCatalog(fileCatalog);
                            bizFile.setCreateUser(WebUtil.getLoginUser().getUsername());
                            if (null != task) {
                                bizFile.setTaskName(task.getName());
                                bizFile.setTaskId(task.getId());
                            }
                            bizFile.setBizInfo(bizInfo);
                            bizFileService.addBizFile(bizFile);
                        }
                    });
                }
            }
        }
    }

    private ArrayList<String> getUsernames(String handleUser) {

        ArrayList<String> list = new ArrayList<String>();
        if (StringUtils.isNotBlank(handleUser)) {
            if (handleUser.startsWith(Constants.BIZ_GROUP)) {
                String group = handleUser.replace(Constants.BIZ_GROUP, "");
                if (StringUtils.isNotBlank(group)) {
                    List<String> usernames = sysUserService.findUserByRole(new SystemRole(null, group));
                    if (CollectionUtils.isNotEmpty(usernames)) {
                        usernames.forEach(username -> list.add(username));
                    }
                }
            } else {
                String[] usernames = handleUser.split("\\,");
                for (String username : usernames) {
                    if (StringUtils.isNotBlank(username)) {
                        list.add(username);
                    }
                }
            }
        }
        return list;
    }

    @Override
    public void writeBizLog(BizInfo bizInfo, Task task, Date now, Map<String, Object> params) {

        BizLog logBean = new BizLog();
        logBean.setCreateTime(now);
        logBean.setTaskID(task.getId());
        logBean.setTaskName(task.getName());
        logBean.setBizInfo(bizInfo);
        logBean.setHandleDescription((String) params.get("base.handleMessage"));
        logBean.setHandleResult((String) params.get("base.handleResult"));
        String loginUser = WebUtil.getLoginUser() != null ? WebUtil.getLoginUser().getUsername() : (String) params.get("loginUser");
        logBean.setHandleUser(loginUser);
        logBean.setHandleName((String) params.get("base.handleName"));
        logService.addBizLog(logBean);
    }

    @Override
    public BizInfo getBizInfo(String id) {

        BizInfo bean = null;
        if (StringUtils.isNotBlank(id)) {
            bean = bizInfoService.getBizInfo(id, WebUtil.getLoginUser().getUsername());
        }
        if (bean == null) {
            throw new ServiceException("找不到工单");
        }
        return bean;
    }

    /**
     * 获取某个流程的开始按钮
     *
     * @param tempId
     * @return
     * @throws ServiceException
     */
    @Override
    public Map<String, String> loadStartButtons(String tempId) {

        Map<String, String> buttons = processDefinitionService.loadStartButtons(tempId);
        if (buttons == null || buttons.size() <= 0) {
            buttons = buttons == null ? new HashMap<String, String>() : buttons;
            buttons.put("submit", "提交");
        }
        return buttons;
    }

    /**
     * 根据工单号查询工单信息，并且处理工单的处理权限,KEY列表如下<br>
     * ---ID跟taskID配套，如果传了taskID,则会判断当前是否可编辑，否则工单只呈现 workInfo： 工单对象信息<br>
     * CURRE_OP: 当前用户操作权限<br>
     * ProcessValBeanMap :需要呈现的业务字段<br>
     * ProcessTaskValBeans:当前编辑的业务字段<br>
     * extInfo :扩展信息<br>
     * extInfo.createUser:创建人信息<br>
     * serviceInfo:业务字段信息内容<br>
     * annexs:附件列表<br>
     * workLogs:日志
     *
     * @param id
     * @return
     * @throws ServiceException
     */
    @Override
    public Map<String, Object> queryWorkOrder(String id) {

        String loginUser = WebUtil.getLoginUser().getUsername();
        Map<String, Object> result = new HashMap<String, Object>();
        // 加载工单对象
        BizInfo bizInfo = bizInfoService.get(id);
        if (bizInfo == null) {
            throw new ServiceException("找不到工单:" + id);
        }
        result.put("workInfo", bizInfo);
        BizInfoConf bizInfoConf = this.bizInfoConfService.getMyWork(id);
        String taskId = bizInfoConf == null ? null : bizInfoConf.getTaskId();
        // 加载工单详情字段
        List<AbstractVariable> list = loadProcessValBean(bizInfo);
        result.put("ProcessValBeanMap", list);

        // 处理扩展信息
        Map<String, Object> extInfo = new HashMap<String, Object>();
        extInfo.put("createUser", sysUserService.getUserByUsername(bizInfo.getCreateUser()));
        extInfo.put("base_taskID", taskId);
        result.put("extInfo", extInfo);

        // 子工单信息
        result.put("subBizInfo", bizInfoService.getBizByParentId(id));
        String curreOp = null;
        if (StringUtils.isNotEmpty(taskId)) {
            curreOp = processDefinitionService.getWorkAccessTask(taskId, loginUser);
        }
        result.put("CURRE_OP", curreOp);
        Task task = processDefinitionService.getTaskBean(taskId);
        if (task != null) {
            result.put("$currentTaskName", task.getName());
        }
        list = loadHandleProcessValBean(bizInfo, taskId);
        // 加载当前编辑的业务字段,只有当前操作为HANDLE的时候才加载
        if (Constants.HANDLE.equalsIgnoreCase(curreOp)) {
            result.put("ProcessTaskValBeans", list);
            extInfo.put("handleUser", sysUserService.getUserByUsername(loginUser));
            Map<String, String> buttons = processDefinitionService.findOutGoingTransNames(taskId, false);
            if (MapUtils.isEmpty(buttons)) {
                buttons = new HashMap<String, String>();
                buttons.put("submit", "提交");
            }
            result.put("SYS_BUTTON", buttons);
        } else if (Constants.SIGN.equalsIgnoreCase(curreOp)) {
            Map<String, String> buttons = new HashMap<String, String>(1);
            buttons.put(Constants.SIGN, "签收");
            result.put("SYS_BUTTON", buttons);
        }
        // 加载工单流程参数
        result.put("serviceInfo", instanceService.loadInstances(bizInfo));
        // 加载流程参数附件
        result.put("annexs", bizFileService.loadBizFilesByBizId(bizInfo.getId(), null));
        // 加载日志
        List<BizLog> bizLogs = logService.loadBizLogs(bizInfo.getId());
        Map<String, List<AbstractVariableInstance>> logVars = new HashMap<String, List<AbstractVariableInstance>>(0);
        Map<String, Object> fileMap = new HashMap<String, Object>();
        if (CollectionUtils.isNotEmpty(bizLogs)) {
            for (BizLog bizLog : bizLogs) {
                fileMap.put(bizLog.getId(), bizFileService.loadBizFilesByBizId(id, bizLog.getTaskID()));
                logVars.put(bizLog.getId(), instanceService.loadValueByLog(bizLog));
            }
        }
        result.put("files", fileMap);
        result.put("workLogs", bizLogs);
        result.put("logVars", logVars);
        return result;
    }

    /**
     * 下载或查看文件
     *
     * @param action
     * @param id
     * @return [文件类型, InputStream]
     * @throws ServiceException
     */
    @Override
    public Object[] downloadFile(String action, String id) {

        Object[] result = new Object[4];
        if ("work".equalsIgnoreCase(action)) {
            BizInfo bean = bizInfoService.get(id);
            if (bean == null) {
                throw new ServiceException("找不到工单");
            }
            result[0] = "IMAGE";
            result[1] = processDefinitionService.viewProcessImage(bean);
        } else {
            BizFile bean = bizFileService.getBizFileById(id);
            if (bean == null) {
                throw new ServiceException("找不到附件");
            }
            File file = UploadFileUtil.getUploadFile(bean);
            if (!file.exists()) {
                throw new ServiceException("找不到附件");
            }
            InputStream is = null;
            try {
                is = new FileInputStream(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            result[0] = bean.getFileType();
            result[1] = is;
            result[2] = file.length();
            result[3] = bean.getName();
        }
        return result;
    }
}
