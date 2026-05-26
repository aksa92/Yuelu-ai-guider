// ====== 首页组件 ======
const HomePage = {
  template: `
    <div>
      <!-- 天气卡片 -->
      <div class="card">
        <div class="card-header">
          <span><span class="card-icon">🌤️</span>长沙 · 岳麓山天气</span>
          <button class="tool-btn" style="padding:4px 12px;font-size:0.75rem" @click="refreshWeather" :disabled="weatherLoading">
            {{ weatherLoading ? '刷新中…' : '刷新' }}
          </button>
        </div>
        <div class="card-body">
          <div v-if="weatherLoading" class="empty-state">查询天气中…</div>
          <div v-else-if="weatherError" class="error-text">{{ weatherError }}</div>
          <div v-else-if="weather" style="font-size:0.85rem;line-height:1.8;color:var(--text-secondary)">
            <div style="display:flex;align-items:center;gap:12px;margin-bottom:8px">
              <span style="font-size:36px">{{ weatherIcon }}</span>
              <div>
                <div style="font-size:1.5rem;font-weight:700;color:var(--text)">
                  {{ weather.temp || '--' }}°C
                </div>
                <div style="font-size:0.8rem">{{ weather.text || '' }}</div>
                <div v-if="weather.wind" style="font-size:0.72rem;color:var(--text-light)">{{ weather.wind }}</div>
              </div>
            </div>
            <div v-if="weather.forecast && weather.forecast.length" style="display:flex;gap:16px;padding-top:8px;border-top:1px solid var(--border-light)">
              <div v-for="(day, i) in weather.forecast" :key="i" style="text-align:center;flex:1">
                <div style="font-weight:500;font-size:0.75rem">{{ day.day }}</div>
                <div style="font-size:0.85rem;margin:4px 0">{{ forecastIcon(day.text) }}</div>
                <div style="font-size:0.7rem;color:var(--text-light)">{{ day.low || '--' }}~{{ day.high || '--' }}°C</div>
              </div>
            </div>
          </div>
          <div v-else class="empty-state">点击刷新获取实时天气</div>
        </div>
      </div>

      <!-- 快捷操作 -->
      <div class="action-grid">
        <button v-for="act in actions" :key="act.id" class="action-btn" @click="doAction(act)">
          <span class="action-icon"><img :src="act.icon" alt="" style="width:28px;height:28px"></span>
          <span class="action-label">{{ act.label }}</span>
        </button>
      </div>

      <!-- 热门问题 -->
      <div class="card">
        <div class="card-header">
          <span><span class="card-icon">🔥</span>热门问题</span>
        </div>
        <div class="hot-list">
          <button v-for="(q, i) in hotQuestions" :key="i" class="hot-item" @click="askQuestion(q)">
            <span class="hot-num">{{ i + 1 }}</span>
            <span>{{ q }}</span>
          </button>
        </div>
      </div>

      <!-- 贴士轮播 -->
      <div class="tips-carousel">
        <div class="tips-text">{{ tips[currentTip] }}</div>
        <div class="tips-dots">
          <button v-for="(_, i) in tips" :key="i" class="tips-dot" :class="{ active: i === currentTip }" @click="currentTip = i"></button>
        </div>
      </div>
    </div>
  `,
  setup() {
    const weather = Vue.ref(null);
    const weatherError = Vue.ref('');
    const weatherLoading = Vue.ref(false);
    const currentTip = Vue.ref(0);

    const weatherIcon = Vue.computed(() => {
      if (!weather.value || !weather.value.text) return '🌤️';
      const t = weather.value.text;
      if (t.includes('晴')) return '☀️';
      if (t.includes('云') || t.includes('阴')) return '⛅';
      if (t.includes('雨')) return '🌧️';
      if (t.includes('雪')) return '❄️';
      if (t.includes('雾')) return '🌫️';
      return '🌤️';
    });

    function forecastIcon(text) {
      if (!text) return '⛅';
      if (text.includes('晴')) return '☀️';
      if (text.includes('云') || text.includes('阴')) return '⛅';
      if (text.includes('雨')) return '🌧️';
      if (text.includes('雪')) return '❄️';
      return '⛅';
    }

    const actions = [
      { id: 'weather', icon: 'icons/weather.svg', label: '查天气' },
      { id: 'route', icon: 'icons/route.svg', label: '问路线' },
      { id: 'food', icon: 'icons/food.svg', label: '找美食' },
      { id: 'spots', icon: 'icons/spots.svg', label: '看景点' },
    ];

    const hotQuestions = [
      '爱晚亭名字的由来是什么？',
      '岳麓山有哪些必去景点？',
      '推荐一条岳麓山游览路线',
      '岳麓书院有什么历史故事？',
      '岳麓山附近有什么好吃的？',
      '怎么去岳麓山？坐地铁到哪一站？',
    ];

    const tips = [
      '🍁 岳麓山免费开放，但需在"岳麓山橘子洲旅游区"公众号提前预约',
      '⏰ 开放时间 6:00-23:00（夏秋季），游览约需 3-4 小时',
      '🍂 最佳游览季节：深秋（11月）赏枫叶，春季（3-4月）赏杜鹃',
      '🚇 地铁 2/4 号线可达，4 号线湖南大学站最方便',
      '👟 建议穿防滑运动鞋，山路多石阶',
    ];

    let tipTimer = null;

    Vue.onMounted(() => {
      refreshWeather();
      tipTimer = setInterval(() => {
        currentTip.value = (currentTip.value + 1) % tips.length;
      }, 5000);
    });

    Vue.onUnmounted(() => {
      if (tipTimer) clearInterval(tipTimer);
    });

    async function refreshWeather() {
      weatherLoading.value = true;
      weatherError.value = '';
      weather.value = null;
      try {
        const res = await fetch('/api/weather?city=长沙');
        if (!res.ok) throw new Error('请求失败');
        const data = await res.json();
        if (data.temp || (data.forecast && data.forecast.length)) {
          weather.value = data;
        } else {
          weatherError.value = '天气数据暂不可用';
        }
      } catch (e) {
        weatherError.value = '天气查询暂时不可用';
      } finally {
        weatherLoading.value = false;
      }
    }

    function doAction(act) {
      const questionMap = {
        weather: '今天长沙岳麓山天气怎么样？适合爬山吗？',
        route: '怎么去岳麓山？推荐路线和交通方式',
        food: '岳麓山附近有什么好吃的推荐？',
        spots: '岳麓山有哪些必看景点？给我介绍一下',
      };
      const q = questionMap[act.id] || act.label;
      if (window.YueluApp) {
        window.YueluApp.askQuestion(q);
      }
    }

    function askQuestion(q) {
      if (window.YueluApp) {
        window.YueluApp.askQuestion(q);
      }
    }

    return {
      weather, weatherError, weatherLoading,
      weatherIcon, forecastIcon,
      currentTip, actions, hotQuestions, tips,
      refreshWeather, doAction, askQuestion,
    };
  }
};
