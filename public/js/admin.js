import { SUPABASE_URL, SUPABASE_ANON_KEY } from "./supabase-config.js";

const client = window.supabase ? window.supabase.createClient(SUPABASE_URL, SUPABASE_ANON_KEY) : null;

document.addEventListener('DOMContentLoaded', () => {
  let applications = [];
  let currentFilter = 'pending'; // Default to "Новые" as requested by user!
  let searchQuery = '';
  let activeApp = null;
  let listenerStarted = false;

  // ========================================================
  // ADMIN AUTHENTICATION
  // ========================================================
  const VALID_PASSWORDS = ['ponos123', 'зщтщы123'];
  const authOverlay = document.getElementById('adminAuthOverlay');
  const authForm = document.getElementById('adminAuthForm');
  const passInput = document.getElementById('adminPassInput');
  const authError = document.getElementById('adminAuthError');

  function checkPassword(val) {
    if (!val) return false;
    const clean = val.trim().toLowerCase();
    return VALID_PASSWORDS.includes(clean);
  }

  let isAuthenticated = sessionStorage.getItem('twoblocks_admin_auth') === 'true' ||
                        localStorage.getItem('twoblocks_admin_auth') === 'true';

  if (isAuthenticated && authOverlay) {
    authOverlay.classList.add('hidden');
    startSupabaseListener();
  }

  if (passInput) {
    passInput.addEventListener('input', () => {
      if (authError) authError.classList.remove('visible');
    });
  }

  if (authForm) {
    authForm.addEventListener('submit', (e) => {
      e.preventDefault();
      const entered = passInput.value;
      if (checkPassword(entered)) {
        isAuthenticated = true;
        sessionStorage.setItem('twoblocks_admin_auth', 'true');
        localStorage.setItem('twoblocks_admin_auth', 'true');
        authOverlay.classList.add('hidden');
        if (authError) authError.classList.remove('visible');
        startSupabaseListener();
      } else {
        if (authError) authError.classList.add('visible');
        passInput.value = '';
        passInput.focus();
      }
    });
  }

  const appsList = document.getElementById('appsList');
  const searchInput = document.getElementById('searchInput');
  const filterTabs = document.querySelectorAll('.filter-tab');
  const btnRefreshList = document.getElementById('btnRefreshList');

  const statTotal = document.getElementById('statTotal');
  const statPending = document.getElementById('statPending');
  const statApproved = document.getElementById('statApproved');
  const countAll = document.getElementById('countAll');
  const countPending = document.getElementById('countPending');
  const countApproved = document.getElementById('countApproved');

  const detailModal = document.getElementById('detailModal');
  const closeDetailModal = document.getElementById('closeDetailModal');
  const modalAvatar = document.getElementById('modalAvatar');
  const modalNickname = document.getElementById('modalNickname');
  const modalStatusBadge = document.getElementById('modalStatusBadge');
  const modalNickVal = document.getElementById('modalNickVal');
  const modalAgeAndName = document.getElementById('modalAgeAndName');
  const modalYears = document.getElementById('modalYears');
  const modalStyles = document.getElementById('modalStyles');
  const modalPlans = document.getElementById('modalPlans');
  const modalFriends = document.getElementById('modalFriends');
  const modalTime = document.getElementById('modalTime');
  const modalWhy = document.getElementById('modalWhy');
  const modalCommunity = document.getElementById('modalCommunity');
  const modalRulesVoice = document.getElementById('modalRulesVoice');
  const modalApproveBtn = document.getElementById('modalApproveBtn');
  const modalCopySwlBtn = document.getElementById('modalCopySwlBtn');
  const deleteAppBtn = document.getElementById('deleteAppBtn');

  // Try fast session cache immediately for 0ms initial load
  try {
    const cached = sessionStorage.getItem('twoblocks_apps_cache');
    if (cached) {
      applications = JSON.parse(cached);
      updateStats();
      renderList();
    }
  } catch (e) {}

  let isLoading = false;
  async function loadApplications(isBackground = false) {
    if (!client || isLoading) return;
    isLoading = true;
    if (!isBackground && applications.length === 0 && appsList) {
      appsList.innerHTML = `<div class="empty-state-card" style="padding: 40px;"><div style="font-size: 32px; margin-bottom: 8px;">⏳</div><div style="font-weight: 700; color: #fff;">Загрузка базы заявок...</div></div>`;
    }
    try {
      const { data, error } = await client
        .from('applications')
        .select('*')
        .order('created_at', { ascending: false });

      if (error) throw error;
      applications = data || [];
      try {
        sessionStorage.setItem('twoblocks_apps_cache', JSON.stringify(applications));
      } catch (e) {}
      updateStats();
      renderList();
    } catch (err) {
      console.error("Supabase load error:", err);
      if (!isBackground && applications.length === 0 && appsList) {
        appsList.innerHTML = `<div class="empty-state-card">Ошибка загрузки базы: ${err.message || err}</div>`;
      }
    } finally {
      isLoading = false;
    }
  }

  let realtimeTimer = null;
  function startSupabaseListener() {
    if (listenerStarted || !isAuthenticated || !client) return;
    listenerStarted = true;

    loadApplications();

    client
      .channel('admin_applications_realtime')
      .on('postgres_changes', { event: '*', schema: 'public', table: 'applications' }, (payload) => {
        // Fast instant handling for new incoming application
        if (payload.eventType === 'INSERT' && payload.new) {
          if (!applications.some(a => a.id === payload.new.id)) {
            applications.unshift(payload.new);
            try { sessionStorage.setItem('twoblocks_apps_cache', JSON.stringify(applications)); } catch (e) {}
            updateStats();
            renderList();
          }
          return;
        }
        // Debounce other events quietly in background without freezing
        clearTimeout(realtimeTimer);
        realtimeTimer = setTimeout(() => {
          loadApplications(true);
        }, 1500);
      })
      .subscribe();
  }

  function updateStats() {
    const total = applications.length;
    const pending = applications.filter(a => a.status === 'pending').length;
    const approved = applications.filter(a => a.status === 'approved').length;

    if (statTotal) statTotal.textContent = total;
    if (statPending) statPending.textContent = pending;
    if (statApproved) statApproved.textContent = approved;

    if (countAll) countAll.textContent = total;
    if (countPending) countPending.textContent = pending;
    if (countApproved) countApproved.textContent = approved;
  }

  function renderList() {
    if (!appsList) return;
    let filtered = applications;

    if (currentFilter !== 'all') {
      filtered = filtered.filter(a => a.status === currentFilter);
    }

    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase().trim();
      filtered = filtered.filter(a => 
        (a.nickname || '').toLowerCase().includes(q) ||
        (a.age_and_name || '').toLowerCase().includes(q) ||
        (a.plans_on_server || '').toLowerCase().includes(q)
      );
    }

    if (filtered.length === 0) {
      appsList.innerHTML = `
        <div class="empty-state-card">
          <div style="font-size: 36px; margin-bottom: 8px;">📭</div>
          <div style="font-weight: 700; font-size: 16px; color: #fff;">Заявок пока нет</div>
          <div style="font-size: 13px; color: var(--text-sub); margin-top: 4px;">
            ${searchQuery ? 'По запросу ничего не найдено' : 'Новые заявки появятся здесь автоматически'}
          </div>
        </div>
      `;
      return;
    }

    appsList.innerHTML = filtered.map(app => {
      const isApproved = app.status === 'approved';
      const isPending = app.status === 'pending';
      const statusClass = isApproved ? 'approved' : isPending ? 'pending' : 'rejected';
      const statusLabel = isApproved ? 'Принят' : isPending ? 'Ожидает' : 'Отклонён';

      let timeAgo = '';
      if (app.created_at) {
        try {
          const d = new Date(app.created_at);
          timeAgo = d.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
        } catch (e) {}
      }

      const avatarUrl = `https://mc-heads.net/avatar/${encodeURIComponent(app.nickname || 'Steve')}/44`;

      return `
        <div class="app-capsule-card" data-id="${app.id}">
          <!-- Left: Avatar Circle -->
          <div class="app-avatar-circle">
            <img src="${avatarUrl}" loading="lazy" decoding="async" alt="${escapeHtml(app.nickname)}" onerror="this.onerror=null; this.src='https://minotar.net/helm/${encodeURIComponent(app.nickname || 'Steve')}/44.png';">
          </div>

          <!-- Middle: Info Pill -->
          <div class="app-info-pill">
            <div class="app-nick-row">
              <span class="app-nick-text">${escapeHtml(app.nickname)}</span>
              <span class="app-status-badge ${statusClass}">${statusLabel}</span>
            </div>
            <div class="app-meta-text">
              ${escapeHtml(app.age_and_name || '—')} • ${timeAgo || 'Недавно'}
            </div>
          </div>

          <!-- Right: Circular Action Buttons (Matching Mockup!) -->
          <div class="app-actions-group" onclick="event.stopPropagation()">
            ${isPending ? `
              <button type="button" class="action-circle-btn btn-approve btn-approve-fast" data-id="${app.id}" title="Принять заявку">
                ✓
              </button>
            ` : ''}

            <button type="button" class="action-circle-btn btn-view btn-view-details" data-id="${app.id}" title="Посмотреть анкету целиком">
              👁️
            </button>

            <button type="button" class="action-circle-btn btn-copy btn-swl-fast" data-nick="${escapeHtml(app.nickname)}" title="Скопировать swl add ${escapeHtml(app.nickname)}">
              📋
            </button>

            <button type="button" class="action-circle-btn btn-delete btn-delete-single" data-id="${app.id}" data-nick="${escapeHtml(app.nickname)}" title="Удалить заявку">
              ✕
            </button>
          </div>
        </div>
      `;
    }).join('');

    // Row click -> open details
    appsList.querySelectorAll('.app-capsule-card').forEach(card => {
      card.addEventListener('click', () => {
        const id = card.getAttribute('data-id');
        openDetails(id);
      });
    });

    // View details button
    appsList.querySelectorAll('.btn-view-details').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const id = btn.getAttribute('data-id');
        openDetails(id);
      });
    });

    // Fast approve button
    appsList.querySelectorAll('.btn-approve-fast').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const id = btn.getAttribute('data-id');
        approveApp(id);
      });
    });

    // Fast copy swl button
    appsList.querySelectorAll('.btn-swl-fast').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const nick = btn.getAttribute('data-nick');
        copySwlCommand(nick, btn);
      });
    });

    // Fast delete button
    appsList.querySelectorAll('.btn-delete-single').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const id = btn.getAttribute('data-id');
        const nick = btn.getAttribute('data-nick');
        if (confirm(`Удалить заявку игрока ${nick}?`)) {
          deleteApp(id);
        }
      });
    });
  }

  function copySwlCommand(nick, btnElement) {
    if (!nick) return;
    const cmd = `swl add ${nick}`;
    navigator.clipboard.writeText(cmd).then(() => {
      const originalText = btnElement.textContent;
      btnElement.textContent = '✓';
      btnElement.style.background = '#059669';
      setTimeout(() => {
        btnElement.textContent = originalText;
        btnElement.style.background = '';
      }, 1500);
    }).catch(() => {
      prompt('Скопируйте команду вручную:', cmd);
    });
  }

  function openDetails(id) {
    const app = applications.find(a => a.id === id);
    if (!app) return;
    activeApp = app;

    if (modalAvatar) {
      modalAvatar.innerHTML = `<img src="https://mc-heads.net/avatar/${encodeURIComponent(app.nickname || 'Steve')}/48" alt="${escapeHtml(app.nickname)}">`;
    }
    if (modalNickname) modalNickname.textContent = app.nickname || '—';
    if (modalNickVal) modalNickVal.textContent = app.nickname || '—';

    const isApproved = app.status === 'approved';
    if (modalStatusBadge) {
      modalStatusBadge.className = `app-status-badge ${isApproved ? 'approved' : app.status === 'pending' ? 'pending' : 'rejected'}`;
      modalStatusBadge.textContent = isApproved ? 'Принят' : app.status === 'pending' ? 'Ожидает' : 'Отклонён';
    }

    if (modalAgeAndName) modalAgeAndName.textContent = app.age_and_name || 'Не указано';
    if (modalYears) modalYears.textContent = app.years_playing || 'Не указано';
    
    const styles = Array.isArray(app.game_styles) ? app.game_styles.join(', ') : (app.game_styles || 'Не указано');
    if (modalStyles) modalStyles.textContent = `${styles} (Уровень: ${app.skill_level || 'Средний'})`;

    if (modalPlans) modalPlans.textContent = app.plans_on_server || '—';
    if (modalFriends) modalFriends.textContent = app.play_style_and_friends || '—';
    if (modalTime) modalTime.textContent = app.time_commitment || '—';
    if (modalWhy) modalWhy.textContent = app.why_vanilla || '—';
    if (modalCommunity) modalCommunity.textContent = app.community_importance || '—';
    if (modalRulesVoice) modalRulesVoice.textContent = `Правила: ${app.rules_agreed || 'Да'} • Микрофон/Войс: ${app.microphone || 'Да'}`;

    if (modalCopySwlBtn) {
      modalCopySwlBtn.onclick = (e) => {
        e.stopPropagation();
        copySwlCommand(app.nickname, modalCopySwlBtn);
      };
    }

    if (modalApproveBtn) {
      if (isApproved) {
        modalApproveBtn.textContent = 'Уже принят ✓';
        modalApproveBtn.disabled = true;
        modalApproveBtn.style.opacity = '0.6';
      } else {
        modalApproveBtn.textContent = 'Принять заявку';
        modalApproveBtn.disabled = false;
        modalApproveBtn.style.opacity = '1';
        modalApproveBtn.onclick = () => approveApp(app.id);
      }
    }

    if (detailModal) detailModal.classList.add('active');
  }

  async function approveApp(id) {
    if (!client) return;
    const idx = applications.findIndex(a => a.id === id);
    if (idx === -1) return;

    const oldStatus = applications[idx].status;
    applications[idx].status = 'approved';
    try { sessionStorage.setItem('twoblocks_apps_cache', JSON.stringify(applications)); } catch (e) {}

    // Instant UI update in 0ms!
    updateStats();
    renderList();

    if (activeApp && activeApp.id === id) {
      activeApp.status = 'approved';
      openDetails(id);
    }

    try {
      const { error } = await client
        .from('applications')
        .update({ status: 'approved' })
        .eq('id', id);

      if (error) throw error;
    } catch (err) {
      console.error(err);
      // Revert if error
      applications[idx].status = oldStatus;
      try { sessionStorage.setItem('twoblocks_apps_cache', JSON.stringify(applications)); } catch (e) {}
      updateStats();
      renderList();
      alert('Ошибка при принятии заявки: ' + (err.message || err));
    }
  }

  async function deleteApp(id) {
    if (!client) return;
    const idx = applications.findIndex(a => a.id === id);
    if (idx === -1) return;

    const removedApp = applications[idx];
    applications.splice(idx, 1);
    try { sessionStorage.setItem('twoblocks_apps_cache', JSON.stringify(applications)); } catch (e) {}

    if (activeApp && activeApp.id === id) {
      if (detailModal) detailModal.classList.remove('active');
      activeApp = null;
    }

    // Instant UI update in 0ms!
    updateStats();
    renderList();

    try {
      const { error } = await client
        .from('applications')
        .delete()
        .eq('id', id);

      if (error) throw error;
    } catch (err) {
      console.error(err);
      applications.splice(idx, 0, removedApp);
      try { sessionStorage.setItem('twoblocks_apps_cache', JSON.stringify(applications)); } catch (e) {}
      updateStats();
      renderList();
      alert('Ошибка при удалении заявки: ' + (err.message || err));
    }
  }

  if (deleteAppBtn) {
    deleteAppBtn.addEventListener('click', () => {
      if (!activeApp) return;
      if (confirm(`Удалить заявку от ${activeApp.nickname}?`)) {
        deleteApp(activeApp.id);
      }
    });
  }

  if (closeDetailModal) {
    closeDetailModal.addEventListener('click', () => {
      if (detailModal) detailModal.classList.remove('active');
      activeApp = null;
    });
  }

  if (detailModal) {
    detailModal.addEventListener('click', (e) => {
      if (e.target === detailModal) {
        detailModal.classList.remove('active');
        activeApp = null;
      }
    });
  }

  if (searchInput) {
    searchInput.addEventListener('input', (e) => {
      searchQuery = e.target.value;
      renderList();
    });
  }

  filterTabs.forEach(tab => {
    tab.addEventListener('click', () => {
      filterTabs.forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      currentFilter = tab.getAttribute('data-status');
      renderList();
    });
  });

  if (btnRefreshList) {
    btnRefreshList.addEventListener('click', () => {
      loadApplications();
    });
  }

  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }
});
