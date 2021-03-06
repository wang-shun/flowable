<%@ page language="java" import="java.util.*" pageEncoding="UTF-8"%>
<%@ include file="/WEB-INF/views/include/taglib.jsp"%>
<%@ include file="/WEB-INF/views/include/head.jsp"%>
<!DOCTYPE html>
<html>
<head>
<base href="${ctx}">

<title>发起工单</title>
<script type="text/javascript" src="${ctx}/js/modules/biz/biz_edit_form.js"></script>
<script type="text/javascript" src="${ctx}/js/modules/biz/biz_edit_paramfunc.js"></script>
<script type="text/javascript" src="${ctx}/js/modules/biz/biz_show_table.js"></script>
<script type="text/javascript" src="${ctx}/js/modules/biz/biz_create.js"></script>

<script type="text/javascript">
	var key = "${key}"; 
	var createUser = ${createUser};
	var bizId = "${bizId}";
	var id = '';
	$(function(){
		$(".js-example-basic-single").select2();
	})
</script>
</head>
<body style="padding: 10px 150px;">
	<form id="form" method="post" enctype="multipart/form-data">
		<input type="hidden" id="base_tempID" name="base.tempID"/>
		<!-- <input type="file" name="files" style="display: none;"> -->
		<input type="hidden" name="startProc"/>
		<input type="hidden" name="base.handleName" value="发起工单"/>
		<input type="hidden" name="base.buttonId"/>
		<input type="hidden" name="base.handleResult"/>
		<div class="t_content">
			<div class="wb_msg" style="height: auto;">
				<div class="wb_msg_all">
					<div class="wb_tit">
						<i id="msgtitle">报障人信息</i>
					</div>
					<table id="bjrxx" cellpadding="0" cellspacing="0" class="infor_table">
					</table>
				</div>
			</div>
		</div>
	</form>
</body>