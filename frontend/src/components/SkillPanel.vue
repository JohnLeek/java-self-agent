<template>
  <div class="skill-panel" v-if="visible">
    <div class="panel-header">
      <h3>技能管理</h3>
      <div class="panel-header-actions">
        <button class="refresh-btn" @click="refreshMcp" title="刷新 MCP 连接">↻</button>
        <button class="close-btn" @click="$emit('close')">✕</button>
      </div>
    </div>

    <button class="btn-add" @click="openImport">+ 导入 Skill</button>

    <div class="skill-list">
      <div v-for="s in skills" :key="s.id" :class="['skill-item', { disabled: !s.enabled }]">
        <div class="skill-info">
          <span class="skill-type">{{ s.type === 'TOOL' ? '🔧' : s.type === 'MCP' ? '🔌' : '📋' }}</span>
          <div>
            <div class="skill-name">{{ s.displayName || s.name }}</div>
            <div class="skill-desc">{{ s.description }}</div>
          </div>
        </div>
        <div class="skill-actions">
          <button v-if="s.enabled" @click="toggle(s.id, false)">禁用</button>
          <button v-else @click="toggle(s.id, true)">启用</button>
          <button class="del" @click="remove(s.id)">删除</button>
        </div>
      </div>
      <div v-if="skills.length === 0" class="empty">暂无导入的 Skill</div>
    </div>

    <!-- 导入表单弹窗 -->
    <div v-if="showImport" class="modal-overlay" @click.self="showImport = false">
      <div class="modal">
        <h3>导入 Skill</h3>
        <label>名称 <input v-model="form.name" placeholder="唯一标识，如 code-review" /></label>
        <label>显示名 <input v-model="form.displayName" placeholder="展示名称" /></label>
        <label>类型
          <select v-model="form.type">
            <option value="PROMPT">PROMPT — 行为指引文档</option>
            <option value="TOOL">TOOL — 可执行工具 (HTTP/脚本)</option>
            <option value="MCP">MCP — MCP 协议服务端</option>
          </select>
        </label>
        <label>描述 <input v-model="form.description" placeholder="给 LLM 看的描述" /></label>
        <label>触发词 <input v-model="form.triggersStr" placeholder="逗号分隔，如 代码审查,review" /></label>

        <!-- PROMPT 类型 -->
        <div v-if="form.type === 'PROMPT'">
          <label>Skill 文档 (Markdown)
            <textarea v-model="form.content" rows="8" placeholder="# 代码审查规范&#10;&#10;## 审查要点&#10;..." />
          </label>
        </div>

        <!-- TOOL 类型 -->
        <div v-if="form.type === 'TOOL'">
          <label>工具名 <input v-model="form.toolName" placeholder="getWeather" /></label>
          <label>工具描述 <input v-model="form.toolDesc" placeholder="查询天气" /></label>
          <label>执行方式
            <select v-model="form.execType">
              <option value="HTTP">HTTP API</option>
              <option value="SCRIPT">脚本执行</option>
            </select>
          </label>
          <div v-if="form.execType === 'HTTP'">
            <label>URL <input v-model="form.execUrl" placeholder="https://api.weather.com/current?city={city}" /></label>
            <label>请求方式 <input v-model="form.execMethod" placeholder="GET" /></label>
          </div>
          <div v-if="form.execType === 'SCRIPT'">
            <label>运行时
              <select v-model="form.execRuntime">
                <option value="python3">python3</option>
                <option value="bash">bash</option>
                <option value="node">node</option>
              </select>
            </label>
            <label>脚本内容
              <textarea v-model="form.execScript" rows="6" placeholder="Python/Bash/Node 脚本" />
            </label>
          </div>
          <label>参数 Schema (JSON) <textarea v-model="form.toolParams" rows="4" placeholder='{"type":"object","properties":{"city":{"type":"string"}},"required":["city"]}' /></label>
        </div>

        <!-- MCP 类型 -->
        <div v-if="form.type === 'MCP'">
          <div class="mcp-tabs">
            <button :class="{active: mcpTab === 'template'}" @click="mcpTab = 'template'">模板市场</button>
            <button :class="{active: mcpTab === 'custom'}" @click="mcpTab = 'custom'">自定义</button>
          </div>

          <!-- 模板市场 -->
          <div v-if="mcpTab === 'template'">
            <div class="template-grid">
              <div v-for="t in templates" :key="t.id"
                   :class="['template-card', {selected: selectedTemplate?.id === t.id}]"
                   @click="selectTemplate(t)">
                <span class="tpl-icon">{{ t.icon }}</span>
                <div>
                  <div class="tpl-name">{{ t.name }}</div>
                  <div class="tpl-desc">{{ t.description }}</div>
                </div>
              </div>
            </div>
            <div v-if="selectedTemplate" class="tpl-detail">
              <div class="tpl-detail-header">
                <span class="tpl-icon-large">{{ selectedTemplate.icon }}</span>
                <span class="tpl-detail-name">{{ selectedTemplate.name }}</span>
              </div>
              <div class="tpl-detail-hint">启动: {{ selectedTemplate.command }} {{ selectedTemplate.args.join(' ') }}</div>
              <div v-for="p in selectedTemplate.params" :key="p.key">
                <label>{{ p.label }} <span v-if="p.required" class="req">*</span>
                  <input v-model="mcpParams[p.key]" :placeholder="p.placeholder" />
                </label>
              </div>
              <div v-for="e in selectedTemplate.envHints" :key="e.key">
                <label>{{ e.label }}
                  <input v-model="mcpEnvInput[e.key]" :placeholder="e.placeholder" type="password" />
                </label>
              </div>
            </div>
            <div v-if="!selectedTemplate" class="tpl-empty">请选择一个模板</div>
          </div>

          <!-- 自定义 -->
          <div v-if="mcpTab === 'custom'">
            <label>启动命令 <input v-model="form.mcpCommand" placeholder="npx" /></label>
            <label>命令参数 <input v-model="form.mcpArgs" placeholder='-y, @modelcontextprotocol/server-filesystem, /tmp' /></label>
            <label class="hint">逗号分隔</label>
            <label>环境变量 (可选) <input v-model="form.mcpEnv" placeholder='GITHUB_TOKEN=ghp_xxx' /></label>
            <label class="hint">逗号分隔的 KEY=VALUE 对</label>
          </div>
        </div>

        <div class="modal-actions">
          <button @click="showImport = false">取消</button>
          <button class="primary" @click="doImport">导入</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const props = defineProps({ visible: Boolean })
const emit = defineEmits(['close'])

const skills = ref([])
const showImport = ref(false)
const form = ref({ type: 'PROMPT', execType: 'HTTP', execMethod: 'GET', execRuntime: 'python3', mcpCommand: 'npx', mcpArgs: '', mcpEnv: '' })
const templates = ref([])
const mcpTab = ref('template')
const selectedTemplate = ref(null)
const mcpParams = ref({})
const mcpEnvInput = ref({})

onMounted(() => { loadSkills(); loadTemplates() })

async function loadSkills() {
  try { const r = await fetch('/api/skills'); skills.value = await r.json() } catch (e) {}
}

async function loadTemplates() {
  try { const r = await fetch('/api/skills/mcp-templates'); templates.value = await r.json() } catch (e) {}
}

function selectTemplate(t) {
  selectedTemplate.value = t
  mcpParams.value = {}
  mcpEnvInput.value = {}
  t.params.forEach(p => { mcpParams.value[p.key] = '' })
  t.envHints.forEach(e => { mcpEnvInput.value[e.key] = '' })
}

function openImport() {
  showImport.value = true
  mcpTab.value = 'template'
  selectedTemplate.value = null
  mcpParams.value = {}
  mcpEnvInput.value = {}
}

async function toggle(id, enabled) {
  await fetch('/api/skills/' + id + '/' + (enabled ? 'enable' : 'disable'), { method: 'PUT' })
  loadSkills()
}

async function remove(id) {
  await fetch('/api/skills/' + id, { method: 'DELETE' })
  loadSkills()
}

async function refreshMcp() {
  await fetch('/api/skills/refresh', { method: 'POST' })
  loadSkills()
}

async function doImport() {
  const f = form.value
  const body = {
    name: f.name, type: f.type, displayName: f.displayName,
    description: f.description,
    triggers: f.triggersStr ? f.triggersStr.split(',').map(s => s.trim()) : [],
  }

  if (f.type === 'PROMPT') {
    body.content = f.content
  } else if (f.type === 'MCP') {
    if (mcpTab.value === 'template' && selectedTemplate.value) {
      const t = selectedTemplate.value
      // 将模板中的 {key} 占位符替换为用户填写的参数值
      const args = t.args.map(a => {
        let s = a
        for (const [k, v] of Object.entries(mcpParams.value)) {
          s = s.replace('{' + k + '}', v || '')
        }
        return s
      })
      const env = {}
      for (const e of t.envHints) {
        if (mcpEnvInput.value[e.key]) env[e.key] = mcpEnvInput.value[e.key]
      }
      body.skillJson = JSON.stringify({
        server: { command: t.command, args, env: Object.keys(env).length > 0 ? env : undefined }
      })
      if (!body.name) body.name = t.id
      if (!body.displayName) body.displayName = t.name
      if (!body.description) body.description = t.description
    } else {
      const args = f.mcpArgs ? f.mcpArgs.split(/[,\n]/).map(s => s.trim()).filter(s => s) : []
      const env = {}
      if (f.mcpEnv) {
        f.mcpEnv.split(',').forEach(pair => {
          const [k, v] = pair.split('=').map(s => s.trim())
          if (k && v) env[k] = v
        })
      }
      body.skillJson = JSON.stringify({
        server: { command: f.mcpCommand || 'npx', args, env: Object.keys(env).length > 0 ? env : undefined }
      })
    }
  } else {
    const executor = f.execType === 'HTTP'
      ? { type: 'HTTP', method: f.execMethod, url: f.execUrl }
      : { type: 'SCRIPT', runtime: f.execRuntime, script: f.execScript }
    let params = {}
    try { params = JSON.parse(f.toolParams) } catch (_) {}
    body.skillJson = JSON.stringify({
      tools: [{ name: f.toolName, description: f.toolDesc, parameters: params, executor }]
    })
  }

  await fetch('/api/skills', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
  })
  showImport.value = false
  loadSkills()
}
</script>

<style scoped>
.skill-panel{position:fixed;right:0;top:0;width:360px;height:100vh;background:#fff;border-left:1px solid #ececf1;z-index:100;display:flex;flex-direction:column}
.panel-header{display:flex;justify-content:space-between;align-items:center;padding:14px 16px;border-bottom:1px solid #ececf1}
.panel-header h3{font-size:15px}
.panel-header-actions{display:flex;gap:8px;align-items:center}
.refresh-btn{background:none;border:none;font-size:18px;cursor:pointer;color:#8e8ea0;padding:2px 6px;border-radius:4px}
.refresh-btn:hover{color:#5436da;background:#f0f0f5}
.close-btn{background:none;border:none;font-size:18px;cursor:pointer;color:#8e8ea0}
.btn-add{margin:12px 16px;padding:8px;border:1px dashed #d9d9e3;border-radius:8px;background:#fff;cursor:pointer;text-align:center;font-size:13px}
.skill-list{flex:1;overflow-y:auto;padding:0 12px}
.skill-item{display:flex;justify-content:space-between;align-items:center;padding:10px;border-radius:8px;margin-bottom:4px;background:#f7f7f8}
.skill-item.disabled{opacity:.5}
.skill-info{display:flex;gap:8px;align-items:flex-start;min-width:0}
.skill-type{flex-shrink:0}
.skill-name{font-size:13px;font-weight:500}
.skill-desc{font-size:12px;color:#8e8ea0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.skill-actions{display:flex;gap:4px;flex-shrink:0}
.skill-actions button{padding:3px 8px;border:1px solid #d9d9e3;border-radius:4px;background:#fff;font-size:11px;cursor:pointer}
.skill-actions button.del{color:#e94560;border-color:#e94560}
.empty{padding:20px;text-align:center;font-size:13px;color:#8e8ea0}
.modal-overlay{position:fixed;inset:0;background:rgba(0,0,0,.3);display:flex;align-items:center;justify-content:center;z-index:200}
.modal{background:#fff;border-radius:12px;padding:24px;width:460px;max-height:80vh;overflow-y:auto}
.modal h3{margin-bottom:16px;font-size:16px}
.modal label{display:block;margin-bottom:10px;font-size:13px;color:#6e6e80}
.modal input,.modal select,.modal textarea{width:100%;margin-top:4px;padding:8px;border:1px solid #d9d9e3;border-radius:6px;font-size:13px}
.modal textarea{font-family:monospace}
.modal-actions{display:flex;justify-content:flex-end;gap:8px;margin-top:16px}
.modal-actions button{padding:8px 16px;border:1px solid #d9d9e3;border-radius:6px;background:#fff;cursor:pointer;font-size:13px}
.modal-actions button.primary{background:#5436da;color:#fff;border:none}
.modal label.hint{font-size:11px;color:#aaa;margin-top:-6px}
/* MCP template browser */
.mcp-tabs{display:flex;gap:0;margin-bottom:12px;border:1px solid #d9d9e3;border-radius:6px;overflow:hidden}
.mcp-tabs button{flex:1;padding:6px 0;border:none;background:#fff;font-size:12px;cursor:pointer}
.mcp-tabs button.active{background:#5436da;color:#fff}
.template-grid{display:grid;grid-template-columns:1fr 1fr;gap:6px;max-height:180px;overflow-y:auto;margin-bottom:10px}
.template-card{padding:8px;border:1px solid #ececf1;border-radius:6px;cursor:pointer;display:flex;gap:6px;align-items:flex-start}
.template-card:hover{border-color:#5436da;background:#f8f8ff}
.template-card.selected{border-color:#5436da;background:#f0f0ff}
.tpl-icon{font-size:18px;flex-shrink:0;width:24px;text-align:center}
.tpl-name{font-size:12px;font-weight:600}
.tpl-desc{font-size:11px;color:#8e8ea0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.tpl-detail{background:#f7f7f8;border-radius:8px;padding:10px;margin-bottom:8px}
.tpl-detail-header{display:flex;align-items:center;gap:8px;margin-bottom:6px}
.tpl-icon-large{font-size:24px}
.tpl-detail-name{font-size:14px;font-weight:600}
.tpl-detail-hint{font-size:11px;color:#8e8ea0;margin-bottom:8px;word-break:break-all}
.tpl-empty{text-align:center;padding:20px;color:#aaa;font-size:13px}
.req{color:#e94560}
</style>
