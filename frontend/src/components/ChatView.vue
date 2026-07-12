<template>
  <div class="chat-panel">
    <div class="messages" ref="msgArea">
      <div v-for="(m,i) in messages" :key="i" :class="['msg', m.role]">
        <div class="avatar">{{ m.role === 'user' ? 'U' : 'AI' }}</div>
        <div>
          <!-- 思考过程（可折叠） -->
          <div v-if="m.steps && m.steps.length" class="thinking-toggle" @click="m.expanded = !m.expanded">
            <span class="toggle-icon">{{ m.expanded ? '▼' : '▶' }}</span>
            <span class="toggle-text">{{ m.expanded ? '隐藏思考过程' : '查看思考过程' }} ({{ m.steps.length }} 步)</span>
          </div>
          <div v-if="m.steps && m.steps.length && m.expanded" class="thinking-steps">
            <div v-for="(s, si) in m.steps" :key="si" :class="['think-step', s.phase]">
              <span :class="['dot', s.phase]"></span>
              <div class="think-info">
                <span class="think-title">{{ s.title }}</span>
                <span v-if="s.content" class="think-content">{{ s.content }}</span>
              </div>
            </div>
          </div>
          <div class="bubble markdown" v-html="md(m.content)"></div>
        </div>
      </div>

      <!-- streaming 中的实时思考步骤 -->
      <div v-if="streaming && liveSteps.length > 0" class="msg agent">
        <div class="avatar">AI</div>
        <div>
          <div class="thinking-live">
            <div class="live-header">正在思考...</div>
            <div v-for="(s, si) in liveSteps" :key="si" :class="['think-step', s.phase]">
              <span :class="['dot', s.phase === 'agent_call' || s.phase === 'summarize' ? 'summarize' : s.phase]"></span>
              <div class="think-info">
                <span class="think-title">{{ s.title }}</span>
                <span v-if="s.content" class="think-content">{{ s.content }}</span>
              </div>
            </div>
          </div>
          <div class="bubble markdown" v-html="md(streamText || '')"></div>
        </div>
      </div>

      <!-- streaming 中无步骤时 -->
      <div v-if="streaming && liveSteps.length === 0" class="msg agent">
        <div class="avatar">AI</div>
        <div class="bubble markdown" v-html="md(streamText || '...')"></div>
      </div>
    </div>

    <!-- 审批弹窗 -->
    <div v-if="showApproval" class="approval-bar">
      <div class="approval-card">
        <div class="approval-icon">⚠️</div>
        <div class="approval-text">Agent 需要确认操作</div>
        <div class="approval-desc">审批 ID: {{ pendingIds[0] }}</div>
        <div class="approval-actions">
          <button class="btn-approve" @click="handleApproval(true)">确认</button>
          <button class="btn-reject" @click="handleApproval(false)">拒绝</button>
        </div>
      </div>
    </div>

    <div class="input-bar">
      <div class="input-wrap">
        <input v-model="input" @keyup.enter="send" placeholder="输入消息..." :disabled="streaming" />
        <button v-if="streaming" @click="stop" class="stop-btn">■</button>
        <button v-else @click="send" :disabled="!input.trim()">↑</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, watch } from 'vue'
import { marked } from 'marked'
marked.setOptions({ breaks: true, gfm: true })
function md(text) {
  let fixed = (text || '').replace(/^(#{1,6})([^\s#])/gm, '$1 $2')
  // 修复 DeepSeek 输出的英文长串无空格问题（在大小写边界加空格）
  fixed = fixed.replace(/([a-z])([A-Z])/g, '$1 $2')
  return marked(fixed)
}

const props = defineProps({ sessionId: String })
const emit = defineEmits(['trace-update', 'new-session', 'step'])

const messages = ref([]), input = ref(''), streaming = ref(false), streamText = ref(''), msgArea = ref(null)
const showApproval = ref(false), pendingIds = ref([])
const liveSteps = ref([])
let creatingSession = false, approvalTimer = null

watch(() => props.sessionId, (id) => {
  if (creatingSession) { creatingSession = false; return }
  if (id) {
    fetch('/api/sessions/' + id + '/messages').then(r => r.json()).then(msgs => {
      messages.value = msgs.map(m => ({ role: m.role, content: m.content, steps: [], expanded: false }))
	      fetch('/api/sessions/' + id + '/traces?round=1').then(r => r.json()).then(traces => {
	        if (traces.length > 0) {
	          const steps = traces.filter(t => t.stepType !== 'CHUNK').map(t => ({
	            phase: t.stepType === 'TOOL_CALL' ? 'thinking' : 'agent_call',
	            title: (t.agentName || t.toolName || t.stepType),
	            content: (t.output || '').substring(0, 150)
	          }))
	          const last = [...messages.value].reverse().find(m => m.role === 'agent')
	          if (last) { last.steps = steps; last.expanded = false }
	        }
	      }).catch(() => {})
      scroll()
    })
  } else {
    messages.value = []
  }
}, { immediate: true })

function scroll() { nextTick(() => { if (msgArea.value) msgArea.value.scrollTop = msgArea.value.scrollHeight }) }

function send() {
  const t = input.value.trim(); if (!t || streaming.value) return
  messages.value.push({ role: 'user', content: t, steps: [] }); input.value = ''; streaming.value = true; streamText.value = ''; liveSteps.value = []
  scroll()
  approvalTimer = setInterval(checkApproval, 2000)

  let sid = props.sessionId
  if (!sid) {
    creatingSession = true
    fetch('/api/session', { method: 'POST' }).then(r => r.json()).then(d => {
      emit('new-session', d.sessionId)
      doStream(d.sessionId, t)
    })
    return
  }
  doStream(sid, t)
}

function doStream(sid, t) {
  const es = new EventSource('/api/chat/stream?message=' + encodeURIComponent(t) + '&sessionId=' + sid)
  es.addEventListener('step', e => {
    try {
      const d = JSON.parse(e.data)
      liveSteps.value.push(d)
      emit('step', d)
      scroll()
    } catch (_) {}
  })
  es.addEventListener('chunk', e => { streamText.value += e.data; scroll() })
  es.addEventListener('trace', e => {
    try { const d = typeof e.data === 'string' ? JSON.parse(e.data) : e.data; emit('trace-update', d) } catch (_) {}
  })
  es.addEventListener('done', () => {
    messages.value.push({ role: 'agent', content: streamText.value, steps: [...liveSteps.value], expanded: false })
    streamText.value = ''; liveSteps.value = []; streaming.value = false; es.close(); scroll()
    setTimeout(() => emit('new-session', sid), 2000)
    if (approvalTimer) { clearInterval(approvalTimer); approvalTimer = null }
  })
  es.addEventListener('error', () => {
    if (streamText.value) messages.value.push({ role: 'agent', content: streamText.value, steps: [...liveSteps.value], expanded: false })
    else messages.value.push({ role: 'agent', content: '[连接失败]', steps: [] })
    streamText.value = ''; liveSteps.value = []; streaming.value = false; es.close()
    if (approvalTimer) { clearInterval(approvalTimer); approvalTimer = null }
  })
}

async function checkApproval() {
  try {
    const r = await fetch('/api/approval/pending')
    const d = await r.json()
    if (d.count > 0) { pendingIds.value = d.ids; showApproval.value = true }
  } catch (_) {}
}
function stop() {
  if (props.sessionId) fetch('/api/chat/stop?sessionId=' + props.sessionId, { method: 'POST' })
  streaming.value = false
}
async function handleApproval(approved) {
  const id = pendingIds.value[0]; if (!id) return
  await fetch('/api/approval/' + id + '/' + (approved ? 'approve' : 'reject'), { method: 'POST' })
  showApproval.value = false; pendingIds.value = []
}
</script>

<style scoped>
.chat-panel{flex:1;display:flex;flex-direction:column;min-width:0}
.messages{flex:1;overflow-y:auto;padding:16px}
.msg{display:flex;gap:12px;padding:12px 0;max-width:800px;margin:0 auto}
.msg.user{flex-direction:row-reverse}
.avatar{width:28px;height:28px;border-radius:4px;display:flex;align-items:center;justify-content:center;font-size:11px;font-weight:600;color:#fff;flex-shrink:0}
.msg.user .avatar{background:#10a37f}
.msg.agent .avatar{background:#5436da}
.bubble{font-size:14px;line-height:1.7;white-space:pre-wrap;word-break:break-word;flex:1;margin-top:4px}
.msg.user .bubble{text-align:right}

/* 思考过程（已完成消息） */
.thinking-toggle{cursor:pointer;font-size:12px;color:#8e8ea0;padding:4px 0;user-select:none}
.toggle-icon{font-size:10px;margin-right:4px}
.toggle-text:hover{color:#5436da}
.thinking-steps{margin:6px 0 8px;padding:8px 12px;background:#f7f7f8;border-radius:8px;border:1px solid #ececf1}

/* 实时思考（streaming中） */
.thinking-live{margin:0 0 8px;padding:8px 12px;background:#fef9e7;border-radius:8px;border:1px solid #f0c040}
.live-header{font-size:12px;font-weight:600;color:#b8860b;margin-bottom:6px}

/* 思考步骤 */
.think-step{display:flex;gap:6px;padding:2px 0;font-size:12px}
.think-info{min-width:0;display:flex;flex-direction:column}
.think-title{font-weight:500}
.think-content{color:#8e8ea0;font-size:11px;word-break:break-all;display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden}
.dot{width:6px;height:6px;border-radius:50%;flex-shrink:0;margin-top:4px}
.dot.plan{background:#f0c040}
.dot.agent_call{background:#5436da;animation:pulse .8s infinite}
.dot.agent_done{background:#10a37f}
.dot.thinking{background:#8e8ea0}
.dot.summarize{background:#5436da;animation:pulse .8s infinite}
@keyframes pulse{0%,100%{opacity:1}50%{opacity:.4}}

.input-bar{padding:10px 16px 14px;border-top:1px solid #ececf1;flex-shrink:0}
.input-wrap{max-width:800px;margin:0 auto;display:flex;gap:8px;border:1px solid #d9d9e3;border-radius:12px;padding:6px 6px 6px 16px;box-shadow:0 0 8px rgba(0,0,0,.03)}
.input-wrap:focus-within{border-color:#5436da}
.input-wrap input{flex:1;border:none;outline:none;font-size:14px;line-height:24px}
.input-wrap button{width:32px;height:32px;border:none;border-radius:8px;background:#5436da;color:#fff;cursor:pointer;font-size:16px}
.input-wrap button:disabled{background:#d9d9e3;color:#aaa;cursor:default}
.stop-btn{background:#e94560!important;width:32px;height:32px;border:none;border-radius:8px;color:#fff;cursor:pointer;font-size:14px}

/* markdown */
.markdown :deep(*){line-height:1.5}
.markdown :deep(p){margin:2px 0}
.markdown :deep(code){background:#f0f0f5;padding:1px 5px;border-radius:4px;font-size:13px;font-family:'SF Mono',Menlo,monospace}
.markdown :deep(pre){background:#1a1a2e;color:#e0e0e0;padding:12px 16px;border-radius:8px;overflow-x:auto;margin:6px 0;font-size:13px;line-height:1.4}
.markdown :deep(pre code){background:none;padding:0;color:inherit}
.markdown :deep(ul),.markdown :deep(ol){padding-left:20px;margin:2px 0}
.markdown :deep(table){border-collapse:collapse;margin:6px 0;width:100%}
.markdown :deep(th),.markdown :deep(td){border:1px solid #d9d9e3;padding:5px 8px;text-align:left;font-size:13px}
.markdown :deep(th){background:#f7f7f8;font-weight:600}
.markdown :deep(blockquote){border-left:3px solid #5436da;padding-left:12px;color:#6e6e80;margin:6px 0}
.markdown :deep(h1),.markdown :deep(h2),.markdown :deep(h3){font-size:15px;font-weight:600;margin:6px 0 2px}

.approval-bar{display:flex;justify-content:center;padding:0 16px;flex-shrink:0}
.approval-card{display:flex;align-items:center;gap:12px;padding:10px 16px;background:#fff3cd;border:1px solid #ffc107;border-radius:10px;margin-bottom:8px;max-width:800px;width:100%}
.approval-icon{font-size:20px}
.approval-text{font-size:13px;font-weight:600}
.approval-desc{font-size:11px;color:#8e8ea0}
.approval-actions{display:flex;gap:6px;margin-left:auto}
.btn-approve,.btn-reject{padding:4px 12px;border-radius:6px;border:none;font-size:12px;cursor:pointer}
.btn-approve{background:#10a37f;color:#fff}
.btn-reject{background:#e94560;color:#fff}
</style>
