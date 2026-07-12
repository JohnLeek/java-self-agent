package com.agent.skill.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * SCRIPT 类型 Skill 执行器。
 * 在本地进程中执行用户上传的脚本（Python/Bash/Node），
 * 参数通过 stdin JSON 传入，结果从 stdout 读取。
 *
 * 安全措施：运行时白名单、脚本长度限制、临时文件隔离、超时保护。
 */
@Component
public class ScriptExecutor {

    private static final Set<String> ALLOWED_RUNTIMES = Set.of("python3", "bash", "node");
    private static final int MAX_SCRIPT_LENGTH = 10_000;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * 执行脚本。
     * @param args      LLM 传来的参数 Map
     * @param runtime   运行时（python3/bash/node）
     * @param script    脚本内容
     * @param timeout   超时秒数
     * @param workDir   工作目录
     * @return 脚本 stdout 输出
     */
    public String execute(Map<String, Object> args, String runtime, String script,
                          int timeout, String workDir) {
        if (!ALLOWED_RUNTIMES.contains(runtime)) {
            return "错误：不允许的运行时 " + runtime + "（仅支持 python3/bash/node）";
        }
        if (script == null || script.length() > MAX_SCRIPT_LENGTH) {
            return "错误：脚本为空或超过最大长度 " + MAX_SCRIPT_LENGTH;
        }

        Path scriptFile = null;
        try {
            // 写入临时脚本文件
            scriptFile = Files.createTempFile("skill-", ".script");
            Files.writeString(scriptFile, script);

            ProcessBuilder pb = new ProcessBuilder(runtime, scriptFile.toString());
            if (workDir != null && !workDir.isEmpty()) {
                pb.directory(new java.io.File(workDir));
            }

            Process process = pb.start();

            // 参数通过 stdin 传给脚本（不是命令行，防注入）
            try (OutputStream os = process.getOutputStream()) {
                os.write(json.writeValueAsBytes(args));
            }

            // 等待完成或超时
            if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "错误：脚本执行超时（" + timeout + "秒）";
            }

            String stdout = new String(process.getInputStream().readAllBytes());
            String stderr = new String(process.getErrorStream().readAllBytes());

            if (process.exitValue() != 0) {
                return "脚本错误 (exit " + process.exitValue() + "): " + stderr;
            }
            return stdout.isEmpty() ? stderr : stdout;

        } catch (Exception e) {
            return "脚本执行异常: " + e.getMessage();
        } finally {
            if (scriptFile != null) {
                try { Files.deleteIfExists(scriptFile); } catch (Exception ignored) {}
            }
        }
    }
}
