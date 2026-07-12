package com.agent.skill.external;

import java.util.List;

/**
 * MCP Server 模板 —— 预置常用 MCP Server 配置，包名经 npm registry 验证。
 */
public record McpTemplate(
    String id,
    String name,
    String description,
    String icon,
    String command,
    List<String> args,
    List<ParamDef> params,
    List<EnvDef> envHints
) {
    public record ParamDef(String key, String label, String type, String placeholder, boolean required) {}
    public record EnvDef(String key, String label, String placeholder) {}

    public static final List<McpTemplate> BUILTIN = List.of(
        // === 官方 @modelcontextprotocol/server-*（已验证存在）===
        new McpTemplate(
            "filesystem", "文件系统", "读写本地文件（14 个工具）", "📁",
            "npx",
            List.of("-y", "@modelcontextprotocol/server-filesystem", "{path}"),
            List.of(new ParamDef("path", "允许访问的根目录", "text", "/Users/liyinqiang/Desktop", true)),
            List.of()
        ),
        new McpTemplate(
            "memory", "记忆系统", "持久化知识图谱记忆", "🧠",
            "npx",
            List.of("-y", "@modelcontextprotocol/server-memory"),
            List.of(),
            List.of()
        ),
        new McpTemplate(
            "github", "GitHub", "仓库管理、Issue、PR、代码搜索", "🐙",
            "npx",
            List.of("-y", "@modelcontextprotocol/server-github"),
            List.of(),
            List.of(new EnvDef("GITHUB_TOKEN", "GitHub Personal Access Token", "ghp_xxxxxxxxxxxx"))
        ),
        new McpTemplate(
            "sequential-thinking", "顺序思考", "分步推理增强", "💭",
            "npx",
            List.of("-y", "@modelcontextprotocol/server-sequential-thinking"),
            List.of(),
            List.of()
        ),

        // === Brave 官方包 ===
        new McpTemplate(
            "brave-search", "Brave Search", "网络搜索（免费 2000 次/月）", "🔍",
            "npx",
            List.of("-y", "@brave/brave-search-mcp-server"),
            List.of(),
            List.of(new EnvDef("BRAVE_API_KEY", "Brave Search API Key", "BSA_xxxxxxxxxxxx"))
        ),

        // === 浏览器自动化（Playwright 替代 Puppeteer，社区标准）===
        new McpTemplate(
            "playwright", "浏览器自动化", "截图、PDF、页面交互（Playwright，社区标准）", "🖥️",
            "npx",
            List.of("-y", "@playwright/mcp@latest"),
            List.of(),
            List.of()
        ),

        // === 网页抓取（npm 社区包）===
        new McpTemplate(
            "fetch", "网页抓取", "抓取网页内容转 Markdown", "🌐",
            "npx",
            List.of("-y", "mcp-webscraper"),
            List.of(),
            List.of()
        ),

        // === pip 包（需 pipx: brew install pipx）===
        new McpTemplate(
            "postgres", "PostgreSQL", "数据库查询", "📦",
            "pipx",
            List.of("run", "mcp-server-postgres"),
            List.of(),
            List.of(new EnvDef("POSTGRES_URL", "数据库连接串", "postgresql://localhost:5432/db"))
        )
    );
}
