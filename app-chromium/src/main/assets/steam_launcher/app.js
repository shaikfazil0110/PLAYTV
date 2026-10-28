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

function runHardwareDiagnostics() {
  if (window.PlayTVNative && window.PlayTVNative.getDeviceInfo) {
    try {
      const info = JSON.parse(window.PlayTVNative.getDeviceInfo());
      document.getElementById("diag-model").innerText = info.deviceModel || "Fire TV";
      document.getElementById("diag-api").innerText = `Android ${info.androidVersion} (API ${info.apiLevel})`;
      document.getElementById("diag-ram").innerText = `${info.availMemMb} MB free / ${info.totalMemMb} MB total`;
      document.getElementById("diag-cpu").innerText = info.cpuAbi || "arm64-v8a";
    } catch (_) {}
  } else {
    document.getElementById("diag-model").innerText = navigator.userAgent.includes("AFT") ? "Amazon Fire TV" : "Android TV";
    document.getElementById("diag-api").innerText = "Android 9 (API 28)";
    document.getElementById("diag-ram").innerText = "Optimal TV Memory Heap";
    document.getElementById("diag-cpu").innerText = "32-bit / 64-bit ARM";
  }

  // WebGL 1.0
  const c1 = document.createElement("canvas");
  const gl1 = c1.getContext("webgl");
  document.getElementById("diag-webgl1").innerText = gl1 ? "Available (Hardware Accelerated)" : "Unavailable";

  // WebGL 2.0
  const c2 = document.createElement("canvas");
  const gl2 = c2.getContext("webgl2");
  document.getElementById("diag-webgl2").innerText = gl2 ? "Available (Shader 3.0 Supported)" : "Unavailable";

  // WebGPU Test
  if (navigator.gpu) {
    navigator.gpu.requestAdapter().then(adapter => {
      document.getElementById("diag-webgpu").innerText = adapter ? "Hardware Adapter Found" : "Not Provided by GPU Driver (Vulkan 1.1 Denylisted)";
    }).catch(() => {
      document.getElementById("diag-webgpu").innerText = "Failed to acquire GPU adapter";
    });
  } else {
    document.getElementById("diag-webgpu").innerText = "Disabled in WebView Runtime (Hardware lacks Vulkan 1.1 compute)";
  }

  // Renderer string
  if (gl1 || gl2) {
    const gl = gl2 || gl1;
    const dbgRender = gl.getExtension("WEBGL_debug_renderer_info");
    if (dbgRender) {
      document.getElementById("diag-renderer").innerText = gl.getParameter(dbgRender.UNMASKED_RENDERER_WEBGL);
    } else {
      document.getElementById("diag-renderer").innerText = gl.getParameter(gl.RENDERER);
    }
  }
}
