<template>
  <div class="trace-panel">
    <div class="panel-title">追踪</div>
    <div v-if="!trace" class="empty">发送消息后显示耗时统计</div>
    <div v-else class="trace-content">
      <div class="summary">
        <span class="metric">{{ trace.totalMs || '...' }}ms</span>
        <span class="sep">|</span>
        <span class="metric">{{ trace.tools || 0 }} 工具</span>
      </div>
      <div class="spans">
        <div v-for="(s,i) in (trace.spans||[])" :key="i" :class="['span', s.type?.toLowerCase()]">
          <span :class="['dot', s.type?.toLowerCase()]"></span>
          <span class="name">{{ s.name }}</span>
          <span class="dur">{{ s.ms }}ms</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({ trace: Object })
</script>

<style scoped>
.trace-panel{width:200px;background:#f7f7f8;border-left:1px solid #ececf1;padding:16px;overflow-y:auto;flex-shrink:0}
.panel-title{font-size:12px;font-weight:600;color:#5436da;margin-bottom:10px}
.empty{font-size:11px;color:#8e8ea0;text-align:center;margin-top:30px}
.summary{display:flex;gap:4px;align-items:center;padding:6px 10px;background:#fff;border-radius:6px;margin-bottom:10px;font-size:12px}
.metric{font-weight:600;color:#5436da}
.sep{color:#d9d9e3}
.spans{display:flex;flex-direction:column;gap:4px}
.span{display:flex;align-items:center;gap:4px;padding:4px 8px;background:#fff;border-radius:4px;font-size:11px}
.span.llm{border-left:3px solid #5436da}
.span.tool{border-left:3px solid #10a37f}
.dot{width:5px;height:5px;border-radius:50%;flex-shrink:0}
.dot.llm{background:#5436da}
.dot.tool{background:#10a37f}
.name{font-weight:500}
.dur{margin-left:auto;color:#8e8ea0}
</style>
