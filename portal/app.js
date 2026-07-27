// app.js – Core logic for Solar Portal UI
// Vanilla JS, premium UX, micro‑animations, SSE progress handling

// ---------- Utility ----------
function $(selector) {return document.querySelector(selector);}
function $all(selector) {return document.querySelectorAll(selector);}
function createEl(tag, className, innerHTML) {
  const el = document.createElement(tag);
  if (className) el.className = className;
  if (innerHTML) el.innerHTML = innerHTML;
  return el;
}

// ---------- Tab Navigation ----------
const tabs = $all('.tab');
const content = $('#content');

tabs.forEach(tab => {
  tab.addEventListener('click', () => {
    tabs.forEach(t => t.classList.remove('active'));
    tab.classList.add('active');
    loadSection(tab.id);
  });
});

function loadSection(tabId) {
  content.innerHTML = '';
  switch(tabId) {
    case 'tab-library': loadLibrary(); break;
    case 'tab-stream': loadStreaming(); break;
    case 'tab-stems': loadStemConfig(); break;
    case 'tab-manual-stems': loadManualStems(); break;
    default: content.textContent = 'Unknown section';
  }
}

// ---------- Library View ----------
async function loadLibrary(page = 1, query = '') {
  const wrapper = createEl('div', 'library');
  const loading = createEl('div','card','Loading…');
  wrapper.appendChild(loading);
  content.appendChild(wrapper);
  // Insert stem drop UI before track list
  renderStemDropSection(wrapper);
  try {
    const resp = await fetch(`/api/library?page=${page}&q=${encodeURIComponent(query)}`);
    if (!resp.ok) throw new Error('Failed to fetch library');
    const data = await resp.json();
    wrapper.innerHTML = '';
    data.tracks.forEach(t => {
      const card = createEl('div', 'card');
      card.innerHTML = `<strong>${t.title}</strong><br>${t.artist}<br><small>${t.album}</small>`;
      wrapper.appendChild(card);
    });
    // pagination controls
    const nav = createEl('div', 'pagination');
    if (data.prev) nav.appendChild(createNavBtn('Prev', () => loadLibrary(data.prev, query)));
    if (data.next) nav.appendChild(createNavBtn('Next', () => loadLibrary(data.next, query)));
    wrapper.appendChild(nav);
  } catch (e) {
    wrapper.innerHTML = `<div class="card error">Error</div>`;
  }
}

function createNavBtn(label, handler) {
  const btn = createEl('button', 'nav-btn', label);
  btn.addEventListener('click', handler);
  btn.style.margin = '0.5rem';
  return btn;
}

// ---------- Stem Drop UI (integrated into Library) ----------
function renderStemDropSection(parent) {
  const wrapper = createEl('div', 'manual-stems');
  wrapper.innerHTML = `
    <h2>Manual Stem Preparation</h2>
    <div class="bento-grid">
      <div class="bento-box" data-stem="drums"><span>Drums</span></div>
      <div class="bento-box" data-stem="vocals"><span>Vocals</span></div>
      <div class="bento-box" data-stem="bass"><span>Bass</span></div>
      <div class="bento-box" data-stem="melody"><span>Melody</span></div>
    </div>
    <div id="manual-status" class="status"></div>
  `;
  parent.appendChild(wrapper);

  const boxes = wrapper.querySelectorAll('.bento-box');
  boxes.forEach(box => {
    box.addEventListener('dragover', e => { e.preventDefault(); box.classList.add('drag-over'); });
    box.addEventListener('dragleave', () => box.classList.remove('drag-over'));
    box.addEventListener('drop', async e => {
      e.preventDefault();
      box.classList.remove('drag-over');
      const files = e.dataTransfer.files;
      if (!files.length) return;
      const stemType = box.dataset.stem;
      const statusDiv = $('#manual-status');
      statusDiv.textContent = `Uploading ${files[0].name} to ${stemType}…`;
      const file = files[0];
      try {
        let uploadBlob = file;
        if (file.type === 'audio/wav' || file.name.toLowerCase().endsWith('.wav')) {
          statusDiv.textContent = `Converting ${file.name} to MP3…`;
          uploadBlob = await wavFileToMp3Blob(file);
        }
        const form = new FormData();
        form.append('stem', uploadBlob, `${stemType}.mp3`);
        form.append('type', stemType);
        const resp = await fetch('/api/stems/upload', {method:'POST', body:form});
        if (!resp.ok) throw new Error('Upload failed');
        statusDiv.textContent = `${stemType} stem uploaded successfully`;
      } catch (err) {
        statusDiv.textContent = `Error: ${err.message}`;
      }
    });
  });
}


// ---------- Streaming UI ----------
function loadStreaming() {
  const wrapper = createEl('div', 'streaming');
  const form = createEl('form', 'search-form');
  form.innerHTML = `
    <input type="text" id="search-query" placeholder="Search YouTube / Deezer" required style="width:70%;padding:0.4rem;"/>
    <select id="service-select"><option value="youtube">YouTube</option><option value="deezer">Deezer</option></select>
    <button type="submit" style="margin-left:0.5rem;">Search</button>
  `;
  wrapper.appendChild(form);
  const results = createEl('div', 'results');
  wrapper.appendChild(results);
  content.appendChild(wrapper);

  form.addEventListener('submit', async e => {
    e.preventDefault();
    results.innerHTML = '';
    const q = $('#search-query').value.trim();
    const svc = $('#service-select').value;
    const loading = createEl('div','card','Searching…');
    results.appendChild(loading);
    try {
      const resp = await fetch(`/api/search/${svc}?q=${encodeURIComponent(q)}`);
      if (!resp.ok) throw new Error('Search failed');
      const data = await resp.json();
      results.innerHTML = '';
      data.items.forEach(item => {
        const card = createEl('div', 'card');
        card.innerHTML = `<strong>${item.title}</strong><br><small>${item.duration}</small>`;
        const dlBtn = createEl('button', 'dl-btn', 'Download');
        dlBtn.addEventListener('click', () => downloadMedia(svc, item.id, item.title));
        card.appendChild(dlBtn);
        results.appendChild(card);
      });
    } catch (err) {
      results.innerHTML = `<div class="card error">Error</div>`;
    }
  });
}

function downloadMedia(service, id, title) {
  const card = createEl('div', 'card');
  const titleEl = createEl('div', '', `<strong>${title}</strong>`);
  const progBar = createEl('div', 'progress-bar');
  const progFill = createEl('div');
  progBar.appendChild(progFill);
  const percentEl = createEl('div', 'percent', '0%');
  const log = createEl('ul', 'log');
  card.appendChild(titleEl);
  card.appendChild(progBar);
  card.appendChild(percentEl);
  card.appendChild(log);
  content.appendChild(card);

  const evtSource = new EventSource(`/api/download/${service}/${id}`);
  evtSource.onmessage = e => {
    const msg = JSON.parse(e.data);
    if (msg.progress !== undefined) {
      progFill.style.width = `${msg.progress}%`;
      percentEl.textContent = `${msg.progress}%`;
    }
    if (msg.step) {
      const li = createEl('li', '', msg.step);
      log.appendChild(li);
    }
    if (msg.status === 'complete') {
      evtSource.close();
      titleEl.textContent = `${title} – ready`;
    }
    if (msg.error) {
      evtSource.close();
      titleEl.textContent = `Error: ${msg.error}`;
      titleEl.style.color = 'red';
    }
  };
  evtSource.onerror = () => {
    evtSource.close();
    titleEl.textContent = 'Connection lost';
    titleEl.style.color = 'red';
  };
}

// ---------- Stem Separation Config ----------
function loadStemConfig() {
  const wrapper = createEl('div', 'stem-config');
  wrapper.innerHTML = `
    <h2>Stem Separation</h2>
    <label for="api-select">Service:</label>
    <select id="api-select">
      <option value="lalal" selected>Lalal.ai (recommended)</option>
      <option value="spleeter">Spleeter</option>
    </select><br><br>
    <label for="api-token">API Token:</label>
    <input type="text" id="api-token" placeholder="Enter token" style="width:60%;"/><br><br>
    <label><input type="checkbox" id="run-on-device"/> Run on device (fallback)</label><br><br>
    <button id="save-stem-config">Save Settings</button>
    <div id="stem-status" class="status"></div>
  `;
  content.appendChild(wrapper);

  $('#save-stem-config').addEventListener('click', async () => {
    const service = $('#api-select').value;
    const token = $('#api-token').value.trim();
    const onDevice = $('#run-on-device').checked;
    const payload = {service, token, onDevice};
    const statusDiv = $('#stem-status');
    statusDiv.textContent = 'Saving…';
    try {
      const resp = await fetch('/api/stem/config', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(payload)
      });
      if (!resp.ok) throw new Error('Save failed');
      statusDiv.textContent = 'Settings saved.';
    } catch (e) {
      statusDiv.textContent = `Error: ${e.message}`;
    }
  });
}
function loadManualStems() {
  const wrapper = createEl('div', 'manual-stems');
  wrapper.innerHTML = `
    <h2>Manual Stem Preparation</h2>
    <div class="bento-grid">
      <div class="bento-box" data-stem="drums"><span>Drums</span></div>
      <div class="bento-box" data-stem="vocals"><span>Vocals</span></div>
      <div class="bento-box" data-stem="bass"><span>Bass</span></div>
      <div class="bento-box" data-stem="melody"><span>Melody</span></div>
    </div>
    <div id="manual-status" class="status"></div>
  `;
  content.appendChild(wrapper);

  const boxes = wrapper.querySelectorAll('.bento-box');
  boxes.forEach(box => {
    box.addEventListener('dragover', e => { e.preventDefault(); box.classList.add('drag-over'); });
    box.addEventListener('dragleave', () => box.classList.remove('drag-over'));
    box.addEventListener('drop', async e => {
      e.preventDefault();
      box.classList.remove('drag-over');
      const files = e.dataTransfer.files;
      if (!files.length) return;
      const stemType = box.dataset.stem;
      const statusDiv = $('#manual-status');
      statusDiv.textContent = `Uploading ${files[0].name} to ${stemType}…`;
      const file = files[0];
      try {
        let uploadBlob = file;
        if (file.type === 'audio/wav' || file.name.toLowerCase().endsWith('.wav')) {
          statusDiv.textContent = `Converting ${file.name} to MP3…`;
          uploadBlob = await wavFileToMp3Blob(file);
        }
        const form = new FormData();
        form.append('stem', uploadBlob, `${stemType}.mp3`);
        form.append('type', stemType);
        const resp = await fetch('/api/stems/upload', {method:'POST', body:form});
        if (!resp.ok) throw new Error('Upload failed');
        statusDiv.textContent = `${stemType} stem uploaded successfully`;
      } catch (err) {
        statusDiv.textContent = `Error: ${err.message}`;
      }
    });
  });
}

// ---------- Settings (generic) ----------
function loadSettings() {
  const wrapper = createEl('div', 'settings');
  wrapper.innerHTML = `<h2>Device Settings</h2><div id="settings-container">Loading…</div>`;
  content.appendChild(wrapper);
  fetch('/api/settings')
    .then(r => r.json())
    .then(data => {
      const container = $('#settings-container');
      container.innerHTML = '';
      Object.entries(data).forEach(([key, val]) => {
        const row = createEl('div', 'setting-row');
        row.innerHTML = `<label>${key}</label> <input type="text" value="${val}" data-key="${key}"/>`;
        container.appendChild(row);
      });
      const saveBtn = createEl('button', '', 'Save Settings');
      saveBtn.addEventListener('click', () => {
        const updates = {};
        container.querySelectorAll('input').forEach(inp => {
          updates[inp.dataset.key] = inp.value;
        });
        fetch('/api/settings', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(updates)})
          .then(r=>r.ok?alert('Saved'):alert('Save failed'));
      });
      container.appendChild(saveBtn);
    })
    .catch(() => {$('#settings-container').textContent='Failed to load settings.';});
}

// Initial load
loadSection('tab-library');
