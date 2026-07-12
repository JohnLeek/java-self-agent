<template>
  <div class="app">
    <header>
      <div class="logo"><h1>Day6 Agent</h1><span class="badge">Full Integration</span></div>
      <span class="sub">Skills + RAG + Memory + 可观测性</span>
      <button class="btn-skills" @click="showSkills = !showSkills">技能管理</button>
    </header>
    <div class="main">
      <aside class="sidebar">
        <button class="new-chat" @click="newChat">+ 新对话</button>
        <div class="session-list">
          <div v-for="s in sessions" :key="s.id"
               :class="['session-item', { active: s.id === currentId }]">
            <div class="session-main" @click="selectSession(s.id)">
              <div class="session-title">{{ s.title }}</div>
              <div class="session-meta">{{ s.messageCount }} 条消息</div>
            </div>
            <button class="session-del" @click.stop="deleteSession(s.id)" title="删除会话">✕</button>
          </div>
          <div v-if="sessions.length === 0" class="empty">暂无历史会话</div>
        </div>
      </aside>
      <ChatView :sessionId="currentId" @new-session="onNewSession" @trace-update="onTrace" />
      <TracePanel :trace="traceData" />
      <SkillPanel :visible="showSkills" @close="showSkills = false" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import ChatView from './components/ChatView.vue'
import TracePanel from './components/TracePanel.vue'
import SkillPanel from './components/SkillPanel.vue'

const sessions = ref([])
const currentId = ref('')
const traceData = ref(null)
const showSkills = ref(false)

onMounted(loadSessions)

async function loadSessions() {
  try {
    const r = await fetch('/api/sessions')
    sessions.value = await r.json()
    if (sessions.value.length > 0 && !currentId.value) {
      selectSession(sessions.value[0].id)
    }
  } catch (e) { console.error('加载会话列表失败', e) }
}

async function selectSession(id) {
  currentId.value = id
  try {
    const r = await fetch('/api/sessions/' + id + '/messages')
    const msgs = await r.json()
    // 通过自定义事件传给 ChatView 加载历史消息
    window.__loadMessages = msgs
  } catch (e) { console.error('加载消息失败', e) }
}

function newChat() {
  currentId.value = ''
  window.__loadMessages = []
}

async function deleteSession(id) {
  await fetch('/api/sessions/' + id, { method: 'DELETE' })
  if (currentId.value === id) currentId.value = ''
  loadSessions()
}

function onNewSession(sid) {
  currentId.value = sid
  loadSessions()
}

const stepsList = ref([])
function onTrace(data) { traceData.value = data }
function onStep(data) { stepsList.value.push(data) }
</script>

<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#fff;color:#343541}
.app{height:100vh;display:flex;flex-direction:column}
header{padding:10px 20px;display:flex;align-items:center;gap:10px;border-bottom:1px solid #ececf1;background:#fff;flex-shrink:0}
.logo{display:flex;align-items:center;gap:8px}
header h1{font-size:16px;font-weight:600}
header .badge{font-size:11px;background:#f0f0f5;color:#5436da;padding:2px 8px;border-radius:10px}
header .sub{font-size:13px;color:#8e8ea0;margin-left:auto}
.btn-skills{margin-left:12px;padding:5px 14px;border:1px solid #5436da;border-radius:6px;background:#fff;color:#5436da;font-size:12px;cursor:pointer}
.btn-skills:hover{background:#f0f0ff}
.main{flex:1;display:flex;overflow:hidden}

.sidebar{width:260px;background:#f7f7f8;border-right:1px solid #ececf1;display:flex;flex-direction:column;flex-shrink:0}
.new-chat{margin:12px;padding:10px;border:1px solid #d9d9e3;border-radius:8px;background:#fff;font-size:14px;cursor:pointer;text-align:center}
.new-chat:hover{background:#f0f0f5}
.session-list{flex:1;overflow-y:auto;padding:0 8px}
.session-item{display:flex;align-items:center;padding:6px 8px 6px 12px;border-radius:8px;margin-bottom:2px}
.session-item:hover{background:#ececf1}
.session-item.active{background:#fff;border:1px solid #d9d9e3}
.session-main{flex:1;cursor:pointer;min-width:0}
.session-title{font-size:13px;font-weight:500;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.session-meta{font-size:11px;color:#8e8ea0;margin-top:2px}
.session-del{opacity:0;background:none;border:none;color:#e94560;cursor:pointer;font-size:14px;padding:2px 6px;flex-shrink:0}
.session-item:hover .session-del{opacity:1}
.empty{padding:20px;text-align:center;font-size:13px;color:#8e8ea0}
</style>
