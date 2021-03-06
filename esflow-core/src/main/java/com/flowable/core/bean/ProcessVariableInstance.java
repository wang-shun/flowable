package com.flowable.core.bean;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

/**
 * 流程全局实例（存储具体填写的值）
 */
@Entity
@Table(name = "T_BIZ_PROCESS_INSTANCE")
@DynamicInsert
@DynamicUpdate
@JsonIgnoreProperties(value = { "hibernateLazyInitializer", "handler", "fieldHandler" })
public class ProcessVariableInstance implements Serializable,Cloneable {

	private static final long serialVersionUID = 620831623030964444L;

	/**
	 * ID
	 */
	private String id;

	/**
	 * 增加填写时间
	 */
	private Date createTime;

	/**
	 * 参数
	 */
	private ProcessVariable variable;

	/**
	 * 流程id
	 */
	private String bizId;

	/**
	 * 任务ID
	 */
	private String taskId;

	/**
	 * 流程实例ID
	 */
	private String processInstanceId;

	/**
	 * 值
	 */
	private String value;

	/**
	 * 处理人信息
	 */
	private String handleUser;

	/**
	 * 参数名称
	 */
	private String variableName;

	/**
	 * 参数别名
	 */
	private String variableAlias;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "PROCESS_VARIABLE_ID")
	public ProcessVariable getVariable() {
		return variable;
	}

	public void setVariable(ProcessVariable bean) {
		this.variable = bean;
	}

	@Id
	@GeneratedValue(generator = "generator")
	@GenericGenerator(name = "generator", strategy = "uuid")
	@Column(unique = true, nullable = false, length = 64, name = "ID")
	public String getId() {
		return id;
	}

	@Column(length = 64, name = "PROCESS_INSTANCE_ID")
	public String getProcessInstanceId() {
		return processInstanceId;
	}

	@Column(nullable = false, length = 512, name = "VALUE")
	public String getValue() {
		return value;
	}

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "CREATE_TIME")
	public Date getCreateTime() {
		return createTime;
	}

	public void setCreateTime(Date createTime) {
		this.createTime = createTime;
	}

	public void setId(String id) {
		this.id = id;
	}

	public void setProcessInstanceId(String processInstanceId) {
		this.processInstanceId = processInstanceId;
	}

	public void setValue(String value) {
		this.value = value;
	}

	@Column(name = "BIZ_ID", nullable = false, length = 64)
	public String getBizId() {
		return bizId;
	}

	public void setBizId(String bizId) {
		this.bizId = bizId;
	}

	@Column(name = "TASK_ID", nullable = false, length = 32)
	public String getTaskId() {
		return taskId;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

	@Column(name = "HANDLE_USER", nullable = false, length = 128)
	public String getHandleUser() {
		return handleUser;
	}

	public void setHandleUser(String handleUser) {
		this.handleUser = handleUser;
	}

	@Column(name = "VARIABLE_NAME", nullable = false, length = 32)
	public String getVariableName() {
		return variableName;
	}

	public void setVariableName(String variableName) {
		this.variableName = variableName;
	}

	@Column(name = "VARIABLE_ALIAS", nullable = false, length = 32)
	public String getVariableAlias() {
		return variableAlias;
	}

	public void setVariableAlias(String variableAlias) {
		this.variableAlias = variableAlias;
	}

	public ProcessVariableInstance clone() {
		ProcessVariableInstance instance = null;
		try {
			instance = (ProcessVariableInstance) super.clone();
			instance.setVariable(getVariable());
		} catch (Exception e) {
			e.printStackTrace();
		}
		return instance;
	}

}
