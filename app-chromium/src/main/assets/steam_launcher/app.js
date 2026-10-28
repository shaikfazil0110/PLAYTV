let catalog = [];
let focusedGame = null;

document.addEventListener("DOMContentLoaded", function () {
  updateClock();
  setInterval(updateClock, 1000);

  setupTabs();
  loadGames();
  runHardwareDiagnostics();

  document.getElementById("btn-hero-play").addEventListener("click", function () {
    if (focusedGame) launchGame(focusedGame);
    else if (catalog.length > 0) launchGame(catalog[0]);
  });

  document.getElementById("btn-refresh-diag").addEventListener("click", function () {
    runHardwareDiagnostics();
  });
});

function updateClock() {
  const now = new Date();
  const hrs = String(now.getHours()).padStart(2, "0");
  const mins = String(now.getMinutes()).padStart(2, "0");
  const el = document.getElementById("clock");
  if (el) el.innerText = `${hrs}:${mins}`;
}

function setupTabs() {
  const tabs = document.querySelectorAll(".tab-btn");
  tabs.forEach(tab => {
    tab.addEventListener("click", () => {
      tabs.forEach(t => t.classList.remove("active"));
      tab.classList.add("active");
      const target = tab.getAttribute("data-tab");
      document.querySelectorAll(".tab-pane").forEach(p => p.classList.remove("active"));
      document.getElementById(`tab-${target}`).classList.add("active");
    });
  });
}

function loadGames() {
  fetch("../games.json")
    .then(r => r.json())
    .then(data => {
      catalog = data;
      renderCarousel(data);
      if (data.length > 0) setHeroGame(data[0]);
    })
    .catch(() => {
      // Fallback
      catalog = [
        { name: "Need for Speed: Underground 2", genre: "Racing", url: "https://nfsu2.xyz/", icon: "🏎️", platform: "PC GAME PORTS" },
        { name: "Quake III Arena — Q3JS", genre: "Shooter", url: "https://q3js.com/", icon: "🎯", platform: "PC GAME PORTS" },
        { name: "Cyberpunk 2077 Browser Demo", genre: "Action", url: "https://cyberpunk2077.lol/", icon: "⚡", platform: "WebGPU (Experimental)" }
      ];
      renderCarousel(catalog);
      setHeroGame(catalog[0]);
    });
}

function setHeroGame(game) {
  focusedGame = game;
  document.getElementById("hero-title").innerText = game.name;
  document.getElementById("hero-desc").innerText = game.note || `${game.genre} title running on hardware canvas.`;
  document.getElementById("hero-compat").innerText = `${game.genre} • ${game.platform}`;
}

function renderCarousel(games) {
  const container = document.getElementById("carousel-games");
  container.innerHTML = "";
  games.forEach((game, idx) => {
    const card = document.createElement("div");
    card.className = "game-card";
    card.tabIndex = 100 + idx;
    card.innerHTML = `
      <div class="card-cover">${game.icon || "🎮"}</div>
      <div class="card-info">
        <div class="card-title">${game.name}</div>
        <div class="card-engine">${game.platform}</div>
      </div>
    `;

    card.addEventListener("focus", () => setHeroGame(game));
    card.addEventListener("click", () => launchGame(game));
    container.appendChild(card);
  });
}

function launchGame(game) {
  if (window.PlayTVNative && window.PlayTVNative.launchGame) {
    window.PlayTVNative.launchGame(JSON.stringify(game));
  } else {
    window.location.href = game.url;
  }
}

async function runHardwareDiagnostics() {
  const byId = (id) => document.getElementById(id);
  const setText = (id, value) => { const el = byId(id); if (el) el.innerText = value; };

  // Report actual Android device data when the native bridge is available.
  if (window.PlayTVNative && window.PlayTVNative.getDeviceInfo) {
    try {
      const info = JSON.parse(window.PlayTVNative.getDeviceInfo());
      setText("diag-model", info.deviceModel || "Android TV device");
      setText("diag-api", `Android ${info.androidVersion} (API ${info.apiLevel})`);
      setText("diag-ram", `${info.availMemMb} MB free / ${info.totalMemMb} MB total`);
      setText("diag-cpu", info.cpuAbi || "Not reported");
    } catch (e) {
      setText("diag-model", "Device info unavailable");
    }
  } else {
    setText("diag-model", navigator.userAgent.includes("AFT") ? "Amazon Fire TV" : "Android TV / browser");
    setText("diag-api", "Native Android details unavailable");
    setText("diag-ram", "Not exposed by this runtime");
    setText("diag-cpu", "Not exposed by this runtime");
  }

  // WebGL checks only show that the WebView can create these contexts; they do not prove WebGPU support.
  let gl1 = null;
  let gl2 = null;
  try { gl1 = document.createElement("canvas").getContext("webgl"); } catch (_) {}
  try { gl2 = document.createElement("canvas").getContext("webgl2"); } catch (_) {}
  setText("diag-webgl1", gl1 ? "Available" : "Unavailable");
  setText("diag-webgl2", gl2 ? "Available" : "Unavailable");

  // Probe each WebGPU stage separately. Vulkan feature flags reported by Android do not guarantee
  // that the WebView exposes WebGPU or that its adapter can create a device.
  const gpuEl = byId("diag-webgpu");
  if (gpuEl) gpuEl.innerText = "Testing WebGPU API…";
  if (!navigator.gpu || typeof navigator.gpu.requestAdapter !== "function") {
    setText("diag-webgpu", "Unavailable: navigator.gpu is not exposed by this WebView");
  } else {
    try {
      const adapter = await navigator.gpu.requestAdapter();
      if (!adapter) {
        setText("diag-webgpu", "API exposed, but requestAdapter() returned no adapter");
      } else {
        let adapterDetails = "Adapter found";
        try {
          if (adapter.info) {
            const parts = [adapter.info.vendor, adapter.info.architecture, adapter.info.device, adapter.info.description].filter(Boolean);
            if (parts.length) adapterDetails += ` (${parts.join(" / ")})`;
          }
        } catch (_) {}
        try {
          const device = await adapter.requestDevice();
          if (device) {
            setText("diag-webgpu", `${adapterDetails}; device creation succeeded`);
            device.destroy();
          } else {
            setText("diag-webgpu", `${adapterDetails}; device creation returned no device`);
          }
        } catch (e) {
          setText("diag-webgpu", `${adapterDetails}; device creation failed: ${e && e.message ? e.message : String(e)}`);
        }
      }
    } catch (e) {
      setText("diag-webgpu", `requestAdapter() failed: ${e && e.message ? e.message : String(e)}`);
    }
  }

  // Renderer string is optional and can be hidden by the browser for privacy/security reasons.
  const gl = gl2 || gl1;
  if (gl) {
    try {
      const dbg = gl.getExtension("WEBGL_debug_renderer_info");
      setText("diag-renderer", dbg ? gl.getParameter(dbg.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER));
    } catch (_) { setText("diag-renderer", "Renderer details blocked by WebView"); }
  } else {
    setText("diag-renderer", "No WebGL context available");
  }
}
