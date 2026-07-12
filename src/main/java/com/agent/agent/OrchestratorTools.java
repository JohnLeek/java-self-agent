package com.agent.agent;

import com.agent.controller.ApprovalController;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Orchestrator 的 @Tool — 暴露给主 Agent。
 * delegateSearch/Tools/Memory → 委托给 Specialist
 * requestApproval → Human-in-the-Loop 审批
 */
@Component
public class OrchestratorTools {

    private final SpecialistAgent searchAgent;
    private final SpecialistAgent toolAgent;
    private final SpecialistAgent memoryAgent;

    public OrchestratorTools(SpecialistAgent searchAgent,
                             SpecialistAgent toolAgent,
                             SpecialistAgent memoryAgent) {
        this.searchAgent = searchAgent;
        this.toolAgent = toolAgent;
        this.memoryAgent = memoryAgent;
    }

    @Tool(description = "委托搜索专家在知识库中检索")
    public String delegateSearch(@ToolParam(description = "搜索查询") String query) {
        return searchAgent.delegate(query);
    }

    @Tool(description = "委托工具专家执行操作：计算、翻译、掷骰子、外部脚本")
    public String delegateTools(@ToolParam(description = "任务描述") String task) {
        return toolAgent.delegate(task);
    }

    @Tool(description = "委托记忆专家管理笔记和用户信息")
    public String delegateMemory(@ToolParam(description = "任务描述") String task) {
        return memoryAgent.delegate(task);
    }

    @Tool(description = "危险操作前请求人类审批。删除文档、写入文件、执行脚本时必须先调用")
    public String requestApproval(@ToolParam(description = "需要审批的操作描述") String action) {
        System.out.println("  [Approval] 等待用户审批: " + action);
        return ApprovalController.requestApproval(action);
    }
}
