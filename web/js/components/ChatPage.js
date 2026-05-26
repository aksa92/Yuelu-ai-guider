// ====== 问答页组件 ======
const ChatPage = {
  template: `
    <div style="display:flex;flex-direction:column;height:100%">
      <!-- 快捷分类 -->
      <div class="category-section">
        <div class="category-tabs">
          <button v-for="cat in categories" :key="cat.id" class="category-tab" :class="{ active: currentCat === cat.id }" @click="currentCat = cat.id">
            <img :src="cat.icon" alt="" class="cat-icon"> {{ cat.label }}
          </button>
        </div>
        <div class="category-questions">
          <button v-for="(q, i) in currentQuestions" :key="i" class="cq-btn" @click="sendMessage(q)">
            {{ q }}
          </button>
        </div>
      </div>

      <!-- 消息列表 -->
      <div class="chat-messages" ref="msgListRef">
        <div v-if="messages.length === 0" class="welcome-banner">
          <div class="welcome-icon">🍁</div>
          <h2>与杜牧同行</h2>
          <p>我是晚唐诗人杜牧，千年后化为此山中的 AI 导游。君若有问，在下知无不言。</p>
        </div>

        <div v-for="(msg, i) in messages" :key="i" class="msg" :class="msg.role">
          <div class="msg-avatar">{{ msg.role === 'user' ? '您' : '岳' }}</div>
          <div class="msg-bubble" :class="{ 'error-bubble': msg.isError }" v-html="msg.html"></div>
        </div>

        <div v-if="isLoading" class="msg assistant">
          <div class="msg-avatar">岳</div>
          <div class="msg-bubble">
            <div class="typing-dots">
              <div class="typing-dot"></div>
              <div class="typing-dot"></div>
              <div class="typing-dot"></div>
            </div>
          </div>
        </div>

        <div v-if="typingContent !== null" class="msg assistant">
          <div class="msg-avatar">岳</div>
          <div class="msg-bubble">
            <span v-html="typingHtml"></span><span class="cursor-blink"></span>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="chat-input-area">
        <div class="chat-input-wrap">
          <input type="text" v-model="inputText" placeholder="询问有关岳麓山的问题" @keydown.enter="sendCurrent" ref="inputRef" :disabled="isLoading || typingContent !== null">
          <button class="chat-send-btn" @click="sendCurrent" :disabled="!inputText.trim() || isLoading || typingContent !== null">
            ➤
          </button>
        </div>
      </div>
    </div>
  `,
  setup() {
    const inputText = Vue.ref('');
    const messages = Vue.reactive([]);
    const convId = Vue.ref(null);
    const isLoading = Vue.ref(false);
    const typingContent = Vue.ref(null);
    const typingHtml = Vue.ref('');
    const currentCat = Vue.ref('spots');
    const inputRef = Vue.ref(null);
    const msgListRef = Vue.ref(null);
    let typingTimer = null;

    const categories = [
      { id: 'spots', icon: 'icons/spots.svg', label: '景点' },
      { id: 'history', icon: 'icons/history.svg', label: '历史' },
      { id: 'food', icon: 'icons/food.svg', label: '美食' },
      { id: 'route', icon: 'icons/route.svg', label: '路线' },
      { id: 'transport', icon: 'icons/transport.svg', label: '交通' },
      { id: 'tips', icon: 'icons/tips.svg', label: '贴士' },
    ];

    const questionsByCat = {
      spots: ['爱晚亭的来历', '岳麓山必看景点', '岳麓书院参观指南', '麓山寺历史', '禹王碑是什么'],
      history: ['岳麓书院历史', '朱张会讲是什么', '湖湘文化名人', '青年毛泽东在岳麓山', '杜牧与岳麓山'],
      food: ['岳麓山附近美食', '登高路有什么小吃', '大学城美食推荐', '长沙湘菜推荐', '特产买什么'],
      route: ['推荐登山路线', '南门进还是东门进', '岳麓山怎么玩最省力', '半日游路线', '一日游路线'],
      transport: ['怎么去岳麓山', '地铁哪站下', '自驾停车方便吗', '公交线路', '开放时间'],
      tips: ['门票多少钱', '需要预约吗', '最佳游览季节', '穿什么鞋合适', '附近还有什么好玩的'],
    };

    const currentQuestions = Vue.computed(() => {
      return questionsByCat[currentCat.value] || questionsByCat.spots;
    });

    function scrollToBottom() {
      Vue.nextTick(() => {
        if (msgListRef.value) {
          msgListRef.value.scrollTop = msgListRef.value.scrollHeight;
        }
      });
    }

    function escHtml(text) {
      const d = document.createElement('div');
      d.textContent = text;
      return d.innerHTML;
    }

    function fmtContent(text) {
      let html = escHtml(text);
      html = html.replace(/\n{2,}/g, '</p><p>');
      html = html.replace(/\n/g, '<br>');
      html = '<p>' + html + '</p>';
      html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
      return html;
    }

    function addMsg(role, content, isError) {
      messages.push({
        role,
        content,
        isError: !!isError,
        html: role === 'assistant' ? fmtContent(content) : escHtml(content),
      });
      scrollToBottom();
    }

    async function sendCurrent() {
      const text = inputText.value.trim();
      if (!text || isLoading.value || typingContent.value !== null) return;
      inputText.value = '';
      await sendMessage(text);
    }

    async function sendMessage(text) {
      if (!text || isLoading.value || typingContent.value !== null) return;
      isLoading.value = true;
      addMsg('user', text);

      try {
        const res = await fetch('/api/rag/chat', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ message: text, conversationId: convId.value }),
        });
        if (!res.ok) throw new Error('API Error');

        const data = await res.json();
        convId.value = data.conversationId;
        const reply = data.reply || '';
        isLoading.value = false;

        // 打字机效果
        typingContent.value = '';
        typingHtml.value = '';
        let idx = 0;
        const SPEED = 14;

        return new Promise((resolve) => {
          function type() {
            if (idx < reply.length) {
              typingContent.value = reply.slice(0, idx + 1);
              typingHtml.value = fmtContent(typingContent.value);
              idx++;
              const ch = reply[idx - 1];
              const extra = '，。！？；：'.includes(ch) ? 3 : '、……—'.includes(ch) ? 2 : 1;
              typingTimer = setTimeout(type, SPEED * extra);
              scrollToBottom();
            } else {
              addMsg('assistant', reply);
              typingContent.value = null;
              typingHtml.value = '';
              resolve();
            }
          }
          type();
        });
      } catch (e) {
        isLoading.value = false;
        addMsg('assistant', '抱歉，连接暂时不可用，请稍后再试。', true);
      }
    }

    function resetConversation() {
      convId.value = null;
      messages.splice(0, messages.length);
      if (typingTimer) clearTimeout(typingTimer);
      typingContent.value = null;
      typingHtml.value = '';
      isLoading.value = false;
    }

    function receiveQuestion(q) {
      if (q) sendMessage(q);
    }

    Vue.onUnmounted(() => {
      if (typingTimer) clearTimeout(typingTimer);
    });

    // 暴露给外部
    window.__chatPage = { receiveQuestion, resetConversation };

    return {
      inputText, messages, isLoading,
      typingContent, typingHtml,
      currentCat, categories, currentQuestions,
      inputRef, msgListRef,
      sendMessage, sendCurrent, resetConversation,
      receiveQuestion, escHtml, fmtContent, scrollToBottom,
    };
  }
};
