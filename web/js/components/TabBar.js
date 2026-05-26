// ====== 底部 TabBar 组件 ======
const TabBar = {
  template: `
    <div class="tab-bar">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        class="tab-item"
        :class="{ active: currentTab === tab.id }"
        @click="$emit('switch', tab.id)"
      >
        <span class="tab-icon"><img :src="tab.icon" alt="" class="tab-icon-img"></span>
        <span>{{ tab.label }}</span>
      </button>
    </div>
  `,
  props: {
    currentTab: { type: String, required: true }
  },
  emits: ['switch'],
  setup() {
    const tabs = Vue.reactive([
      { id: 'home', icon: 'icons/home.svg', label: '首页' },
      { id: 'chat', icon: 'icons/chat.svg', label: '问答' },
      { id: 'tools', icon: 'icons/tools.svg', label: '工具' },
    ]);
    return { tabs };
  }
};
