// ====== 岳麓山 AI 导游 · Vue 3 根实例 ======

const { createApp } = Vue;

const TAB_ORDER = ['home', 'chat', 'tools'];

const app = createApp({
  data() {
    return {
      currentTab: 'home',
      pendingQueue: [],
      touchStartX: 0,
      touchStartY: 0,
    };
  },
  computed: {
    currentPage() {
      const map = { home: 'home-page', chat: 'chat-page', tools: 'tools-page' };
      return map[this.currentTab] || 'home-page';
    },
  },
  watch: {
    currentTab(tab) {
      if (tab === 'chat' && this.pendingQueue.length > 0) {
        this.$nextTick(() => this.flushQueue());
      }
    },
  },
  methods: {
    switchTab(tab) { this.currentTab = tab; },
    onTouchStart(e) {
      this.touchStartX = e.touches[0].clientX;
      this.touchStartY = e.touches[0].clientY;
    },
    onTouchEnd(e) {
      const dx = e.changedTouches[0].clientX - this.touchStartX;
      const dy = e.changedTouches[0].clientY - this.touchStartY;
      if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 50) {
        const idx = TAB_ORDER.indexOf(this.currentTab);
        if (dx < 0 && idx < TAB_ORDER.length - 1) this.switchTab(TAB_ORDER[idx + 1]);
        if (dx > 0 && idx > 0) this.switchTab(TAB_ORDER[idx - 1]);
      }
    },
    askQuestion(q) {
      this.currentTab = 'chat';
      this.pendingQueue.push(q);
      this.$nextTick(() => this.flushQueue());
    },
    flushQueue() {
      const cp = window.__chatPage;
      if (!cp) { setTimeout(() => this.flushQueue(), 100); return; }
      while (this.pendingQueue.length > 0) {
        const q = this.pendingQueue.shift();
        if (cp.receiveQuestion) cp.receiveQuestion(q);
      }
    },
    resetChat() {
      if (window.__chatPage) window.__chatPage.resetConversation();
    },
  },
});

app.component('tab-bar', TabBar);
app.component('home-page', HomePage);
app.component('chat-page', ChatPage);
app.component('tools-page', ToolsPage);

const vm = app.mount('#app');
window.YueluApp = vm;
