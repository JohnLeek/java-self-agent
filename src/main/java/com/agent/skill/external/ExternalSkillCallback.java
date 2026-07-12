package com.agent.skill.external;

import com.agent.trace.TracingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 外部 TOOL Skill → Spring AI FunctionCallback 适配器。
 *
 * 支持 HTTP 和 SCRIPT 两种执行模式。
 * LLM 调用时，根据 executor type 分发到对应的执行器。
 */
public class ExternalSkillCallback implements FunctionCallback {

    private final String toolName;
    private final String toolDescription;
    private final String inputSchemaJson;
    private final Map<String, Object> executor;
    private final RestClient.Builder restClientBuilder;
    private final ScriptExecutor scriptExecutor;
    private final ObjectMapper json = new ObjectMapper();

    public ExternalSkillCallback(String toolName, String toolDescription,
                                  String inputSchemaJson, Map<String, Object> executor,
                                  RestClient.Builder restClientBuilder,
                                  ScriptExecutor scriptExecutor) {
        this.toolName = "ext_" + toolName;
        this.toolDescription = toolDescription;
        this.inputSchemaJson = inputSchemaJson;
        this.executor = executor;
        this.restClientBuilder = restClientBuilder;
        this.scriptExecutor = scriptExecutor;
    }

    @Override
    public String getName() { return toolName; }

    @Override
    public String getDescription() { return toolDescription; }

    @Override
    public String getInputTypeSchema() { return inputSchemaJson; }

    @Override
    @SuppressWarnings("unchecked")
    public String call(String functionArguments) {
        TracingService.recordToolCall(toolName);
        try {
            Map<String, Object> args = functionArguments != null && !functionArguments.isEmpty()
                    ? json.readValue(functionArguments, Map.class) : Map.of();

            String type = (String) executor.getOrDefault("type", "HTTP");
            System.out.println("  [ExternalSkill] " + toolName + " called, type=" + type);

            if ("SCRIPT".equalsIgnoreCase(type)) {
                return executeScript(args);
            }
            return executeHttp(args);

        } catch (Exception e) {
            return "外部 Skill 调用错误 [" + toolName + "]: " + e.getMessage();
        }
    }

    private String executeHttp(Map<String, Object> args) {
        String url = replacePlaceholders((String) executor.get("url"), args);
        String method = (String) executor.getOrDefault("method", "GET");

        RestClient.RequestBodySpec req = restClientBuilder.build()
                .method(org.springframework.http.HttpMethod.valueOf(method.toUpperCase()))
                .uri(url);

        // 请求头
        Map<String, String> headers = (Map<String, String>) executor.get("headers");
        if (headers != null) headers.forEach(req::header);

        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
            try { req.body(json.writeValueAsString(args)); } catch (Exception ignored) {}
        }

        return req.retrieve().body(String.class);
    }

    private String executeScript(Map<String, Object> args) {
        String runtime = (String) executor.getOrDefault("runtime", "python3");
        String script = (String) executor.get("script");
        int timeout = executor.containsKey("timeout") ? ((Number) executor.get("timeout")).intValue() : 30;
        String workDir = (String) executor.getOrDefault("workDir", "/tmp");

        return scriptExecutor.execute(args, runtime, script, timeout, workDir);
    }

    private String replacePlaceholders(String url, Map<String, Object> args) {
        for (Map.Entry<String, Object> e : args.entrySet()) {
            url = url.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
        }
        return url;
    }

    @Override
    public String call(String fa, org.springframework.ai.chat.model.ToolContext tc) {
        return call(fa);
    }
}
