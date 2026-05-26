// ====== 工具页组件 ======
const ToolsPage = {
  template: `
    <div>

      <!-- 天气查询 -->
      <div class="tool-panel">
        <div class="tool-panel-header">
          <span><img src="icons/weather.svg" alt="" class="panel-icon"></span>
          <span>实时天气</span>
        </div>
        <div class="tool-panel-body">
          <div class="tool-input-group">
            <input type="text" v-model="weatherCity" placeholder="城市（如 长沙）" @keydown.enter="getWeather">
            <button class="tool-btn" @click="getWeather" :disabled="weatherLoading || !weatherCity.trim()">
              {{ weatherLoading ? '查询中…' : '查询' }}
            </button>
          </div>
          <div v-if="weatherError" class="error-text">{{ weatherError }}</div>
          <div v-else-if="weatherResult" class="tool-result">{{ weatherResult }}</div>
          <div v-else class="empty-state" style="padding:12px 0">输入城市名查询实时天气</div>
        </div>
      </div>

      <!-- 路线规划 -->
      <div class="tool-panel">
        <div class="tool-panel-header">
          <span><img src="icons/route.svg" alt="" class="panel-icon"></span>
          <span>路线规划</span>
        </div>
        <div class="tool-panel-body">
          <div class="tool-input-group">
            <input type="text" v-model="dirOrigin" placeholder="起点（如 五一广场）" @keydown.enter="planRoute">
            <select v-model="dirMode">
              <option value="transit">公交</option>
              <option value="driving">驾车</option>
              <option value="walking">步行</option>
            </select>
            <button class="tool-btn" @click="planRoute" :disabled="dirLoading || !dirOrigin.trim()">
              {{ dirLoading ? '规划中…' : '规划' }}
            </button>
          </div>
          <div v-if="dirError" class="error-text">{{ dirError }}</div>
          <div v-else-if="dirText" class="tool-result">{{ dirText }}</div>
          <div v-else class="empty-state" style="padding:12px 0">输入起点规划前往岳麓山的路线</div>
          <!-- 路线地图 -->
          <div v-show="dirCoords.length > 0" class="map-container" :id="'routeMap-' + cmpUid"></div>
        </div>
      </div>

      <!-- 周边搜索 -->
      <div class="tool-panel">
        <div class="tool-panel-header">
          <span><img src="icons/search.svg" alt="" class="panel-icon"></span>
          <span>周边搜索</span>
        </div>
        <div class="tool-panel-body">
          <div class="tool-input-group">
            <input type="text" v-model="nearbyKW" placeholder="搜索关键词（如 餐厅、停车场）" @keydown.enter="searchNearby">
            <button class="tool-btn" @click="searchNearby" :disabled="nearbyLoading || !nearbyKW.trim()">
              {{ nearbyLoading ? '搜索中…' : '搜索' }}
            </button>
          </div>
          <div v-if="nearbyError" class="error-text">{{ nearbyError }}</div>
          <div v-else-if="nearbyResult" class="tool-result">{{ nearbyResult }}</div>
          <div v-else class="empty-state" style="padding:12px 0">搜索岳麓山附近的场所</div>
        </div>
      </div>

      <!-- 山内导览 -->
      <div class="tool-panel">
        <div class="tool-panel-header">
          <span><img src="icons/mountain.svg" alt="" class="panel-icon"></span>
          <span>山内导览</span>
          <span style="margin-left:auto;font-size:0.75rem;color:var(--text-light);font-weight:400">
            <button v-for="r in mountainRoutes" :key="r.id"
              class="category-tab" :class="{ active: activeRoute === r.id }"
              @click="highlightRoute(r.id)"
              style="padding:3px 8px;font-size:0.7rem;margin:0 2px">
              {{ r.name }}
            </button>
          </span>
        </div>
        <div class="tool-panel-body">
          <div class="map-container" id="mountainMap"></div>
          <div style="margin-top:8px;display:flex;flex-wrap:wrap;gap:6px">
            <span v-for="s in YUELU_SPOTS" :key="s.id"
              style="font-size:0.72rem;padding:3px 10px;background:var(--cream);border-radius:12px;color:var(--text-secondary);cursor:pointer"
              @click="spotClick(s)">
              {{ s.icon }} {{ s.name }}
            </span>
          </div>
        </div>
      </div>

    </div>
  `,
  setup() {
    // 组件 uid（取代 Vue 2 的 _uid）
    const instance = Vue.getCurrentInstance();
    const cmpUid = instance ? instance.uid : '0';
    // ====== 天气 ======
    const weatherCity = Vue.ref('长沙');
    const weatherResult = Vue.ref('');
    const weatherError = Vue.ref('');
    const weatherLoading = Vue.ref(false);

    // ====== 路线 ======
    const dirOrigin = Vue.ref('');
    const dirMode = Vue.ref('transit');
    const dirText = Vue.ref('');
    const dirError = Vue.ref('');
    const dirLoading = Vue.ref(false);
    const dirCoords = Vue.ref([]);
    const dirMapId = Vue.ref('routeMap');
    let dirMap = null;

    // ====== 周边 ======
    const nearbyKW = Vue.ref('');
    const nearbyResult = Vue.ref('');
    const nearbyError = Vue.ref('');
    const nearbyLoading = Vue.ref(false);

    // ====== 山内导览 ======
    const mountainRoutes = YUELU_ROUTES;
    const activeRoute = Vue.ref(null);
    let mountainMap = null;
    let routeLayers = [];
    let spotMarkers = [];

    // ====== 通用：调 LLM ======
    async function callLLM(prompt) {
      const res = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: prompt }),
      });
      if (!res.ok) throw new Error('请求失败');
      const data = await res.json();
      return data.reply || '';
    }

    // ====== 天气 ======
    async function getWeather() {
      const city = weatherCity.value.trim();
      if (!city) return;
      weatherLoading.value = true;
      weatherError.value = '';
      weatherResult.value = '';
      try {
        weatherResult.value = await callLLM('请查询' + city + '的实时天气和未来3天预报，用简洁格式回复');
      } catch (e) {
        weatherError.value = '天气查询失败，请稍后再试';
        console.error('天气查询失败:', e);
      } finally {
        weatherLoading.value = false;
      }
    }

    // ====== 路线规划 ======
    async function planRoute() {
      const origin = dirOrigin.value.trim();
      if (!origin) return;
      dirLoading.value = true;
      dirError.value = '';
      dirText.value = '';
      dirCoords.value = [];

      try {
        // 并行请求：LLM 文字 + 地图坐标
        const modeLabel = { driving: '驾车', transit: '公交', walking: '步行' };
        const [textRes, mapRes] = await Promise.all([
          callLLM('请规划从"' + origin + '"到岳麓山的' + modeLabel[dirMode.value] + '路线，包括距离、时间和路线指引'),
          fetch('/api/map/directions', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ origin, mode: dirMode.value }),
          }).then(r => r.json()),
        ]);

        dirText.value = textRes;

        if (mapRes.routeCoords && mapRes.routeCoords.length > 0) {
          dirCoords.value = mapRes.routeCoords;
          Vue.nextTick(() => drawRouteMap());
        }
      } catch (e) {
        dirError.value = '路线规划失败，请稍后再试';
        console.error('路线规划失败:', e);
      } finally {
        dirLoading.value = false;
      }
    }

    function drawRouteMap() {
      const containerId = 'routeMap-' + cmpUid;
      Vue.nextTick(() => {
        const el = document.getElementById(containerId);
        if (!el) return;

        if (dirMap) { dirMap.destroy(); dirMap = null; }

        const coords = dirCoords.value;
        if (coords.length < 2) return;

        try {
          dirMap = new AMap.Map(containerId, {
            center: coords[0],
            zoom: 14,
            mapStyle: 'amap://styles/light',
          });

          // 绘制路线
          const polyline = new AMap.Polyline({
            path: coords,
            strokeColor: '#C0392B',
            strokeWeight: 4,
            strokeOpacity: 0.8,
          });
          dirMap.add(polyline);

          // 起点标记（自定义内容避免 label 问题）
          new AMap.Marker({
            position: coords[0],
            map: dirMap,
            content: '<div style="background:#C0392B;color:#fff;font-size:11px;padding:2px 8px;border-radius:4px;white-space:nowrap">起点</div>',
            offset: new AMap.Pixel(-16, -10),
          });

          // 终点标记
          new AMap.Marker({
            position: coords[coords.length - 1],
            map: dirMap,
            content: '<div style="background:#C0392B;color:#fff;font-size:11px;padding:2px 8px;border-radius:4px;white-space:nowrap">岳麓山</div>',
            offset: new AMap.Pixel(-16, -10),
          });

          dirMap.setFitView([polyline], false, 60);
        } catch (e) {
          console.error('路线地图绘制失败:', e);
        }
      });
    }

    // ====== 周边搜索 ======
    async function searchNearby() {
      const kw = nearbyKW.value.trim();
      if (!kw) return;
      nearbyLoading.value = true;
      nearbyError.value = '';
      nearbyResult.value = '';
      try {
        nearbyResult.value = await callLLM('请在岳麓山附近搜索"' + kw + '"，列出找到的场所名称和距离');
      } catch (e) {
        nearbyError.value = '搜索失败，请稍后再试';
      } finally {
        nearbyLoading.value = false;
      }
    }

    // ====== 山内导览地图 ======
    let infoWindow = null;

    function initMountainMap() {
      const el = document.getElementById('mountainMap');
      if (!el || mountainMap) return;
      if (typeof AMap === 'undefined') {
        console.error('高德地图未加载');
        return;
      }

      try {
        // 中心点：岳麓山（GCJ-02，高德原生）
        mountainMap = new AMap.Map('mountainMap', {
          center: [112.936, 28.185],
          zoom: 15,
          mapStyle: 'amap://styles/light',
        });

        // 信息弹窗（复用实例）
        infoWindow = new AMap.InfoWindow({ offset: new AMap.Pixel(0, -30) });

        // 添加景点标记（用自定义内容替代 label，避免偏移计算问题）
        YUELU_SPOTS.forEach(spot => {
          const marker = new AMap.Marker({
            position: spot.coords,
            map: mountainMap,
            title: spot.name,
            content: '<div style="font-size:15px;line-height:1;text-shadow:0 1px 3px rgba(0,0,0,0.3);background:rgba(255,255,255,0.85);border-radius:50%;width:28px;height:28px;display:flex;align-items:center;justify-content:center;border:2px solid #C0392B">' + spot.icon + '</div>',
            offset: new AMap.Pixel(-14, -14),
          });
          marker.on('click', () => {
            infoWindow.setContent(`
              <div style="font-size:0.85rem;line-height:1.6">
                <b>${spot.icon} ${spot.name}</b><br>
                <span style="color:#666;font-size:0.8rem">${spot.desc}</span><br>
                <button class="map-popup-btn" style="margin-top:6px"
                  onclick="window.YueluApp && window.YueluApp.askQuestion('${spot.name}有什么好介绍的？')">
                  去问答 →
                </button>
              </div>
            `);
            infoWindow.open(mountainMap, spot.coords);
          });
          spotMarkers.push(marker);
        });

        // 绘制所有路线（默认半透明）
        YUELU_ROUTES.forEach(route => {
          const polyline = new AMap.Polyline({
            path: route.coords,
            strokeColor: route.color,
            strokeWeight: 3,
            strokeOpacity: 0.3,
          });
          mountainMap.add(polyline);
          routeLayers.push({ id: route.id, layer: polyline });
        });
      } catch (e) {
        console.error('山内导览地图初始化失败:', e);
      }
    }

    function highlightRoute(routeId) {
      if (!mountainMap) return;
      // 移除现有路线图层
      routeLayers.forEach(({ layer }) => mountainMap.remove(layer));
      routeLayers = [];

      // 重新绘制，选中的路线高亮
      YUELU_ROUTES.forEach(route => {
        const isActive = route.id === routeId;
        const polyline = new AMap.Polyline({
          path: route.coords,
          strokeColor: route.color,
          strokeWeight: isActive ? 5 : 3,
          strokeOpacity: isActive ? 1 : 0.3,
        });
        mountainMap.add(polyline);
        routeLayers.push({ id: route.id, layer: polyline });
      });

      activeRoute.value = routeId;
      // 缩放至合适视野
      setTimeout(() => mountainMap.setZoomAndCenter(15, [112.936, 28.185]), 100);
    }

    function spotClick(spot) {
      if (window.YueluApp) {
        window.YueluApp.askQuestion(spot.name + '有什么好介绍的？');
      }
    }

    // ====== 生命周期 ======
    Vue.onMounted(() => {
      setTimeout(initMountainMap, 300);
    });

    Vue.onUnmounted(() => {
      if (dirMap) { dirMap.destroy(); dirMap = null; }
      if (mountainMap) { mountainMap.destroy(); mountainMap = null; }
    });

    return {
      cmpUid,
      // 天气
      weatherCity, weatherResult, weatherError, weatherLoading, getWeather,
      // 路线
      dirOrigin, dirMode, dirText, dirError, dirLoading, dirCoords, planRoute,
      // 周边
      nearbyKW, nearbyResult, nearbyError, nearbyLoading, searchNearby,
      // 山内导览
      YUELU_SPOTS, mountainRoutes: YUELU_ROUTES, activeRoute, highlightRoute, spotClick,
    };
  },
};
