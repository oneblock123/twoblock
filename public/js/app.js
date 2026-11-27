import { SUPABASE_URL, SUPABASE_ANON_KEY } from "./supabase-config.js";

const client = window.supabase ? window.supabase.createClient(SUPABASE_URL, SUPABASE_ANON_KEY) : null;

document.addEventListener('DOMContentLoaded', () => {
  const form = document.getElementById('applicationForm');
  const formMainCard = document.getElementById('formMainCard');
  const statusContainer = document.getElementById('statusContainer');
  const statusHeading = document.getElementById('statusTitle');
  const statusDesc = document.getElementById('statusDesc');
  const statusNickLine = document.getElementById('statusNickLine');
  const statusIcon = document.getElementById('statusIcon');
  const submitBtn = document.getElementById('submitBtn');
  const nicknameInput = document.getElementById('nickname');
  const avatarBox = document.getElementById('avatarPreview');
  const rulesModal = document.getElementById('rulesModal');
  const openRulesBtn = document.getElementById('openRulesBtn');
  const closeRulesBtn = document.getElementById('closeRulesBtn');
  const closeRulesBtnBottom = document.getElementById('closeRulesBtnBottom');

  // Tabs elements
  const tabBtnApply = document.getElementById('tabBtnApply');
  const tabBtnChat = document.getElementById('tabBtnChat');
  const chatMainCard = document.getElementById('chatMainCard');
  const chatMessages = document.getElementById('chatMessages');
  const chatForm = document.getElementById('chatForm');
  const chatInput = document.getElementById('chatInput');
  const chatSendBtn = document.getElementById('chatSendBtn');
  const chatLockedBanner = document.getElementById('chatLockedBanner');
  const chatUserBadge = document.getElementById('chatUserBadge');
  const chatUserName = document.getElementById('chatUserName');
  const chatUserAvatar = document.getElementById('chatUserAvatar');

  let currentTab = 'apply';
  let isApprovedUser = false;
  let activePlayerNick = '';

  // Check saved state in localStorage
  const savedAppId = localStorage.getItem('twoblocks_app_id');
  const savedNick = localStorage.getItem('twoblocks_nick');

  function switchTab(target) {
    currentTab = target;
    if (target === 'chat') {
      if (tabBtnApply) tabBtnApply.classList.remove('active');
      if (tabBtnChat) tabBtnChat.classList.add('active');
      if (formMainCard) formMainCard.style.display = 'none';
      if (statusContainer) statusContainer.style.display = 'none';
      if (chatMainCard) chatMainCard.style.display = 'block';
      scrollChatToBottom();
    } else {
      if (tabBtnChat) tabBtnChat.classList.remove('active');
      if (tabBtnApply) tabBtnApply.classList.add('active');
      if (chatMainCard) chatMainCard.style.display = 'none';
      if (savedAppId) {
        if (statusContainer) statusContainer.style.display = 'block';
        if (formMainCard) formMainCard.style.display = 'none';
      } else {
        if (statusContainer) statusContainer.style.display = 'none';
        if (formMainCard) formMainCard.style.display = 'block';
      }
    }
  }

  if (tabBtnApply) tabBtnApply.addEventListener('click', () => switchTab('apply'));
  if (tabBtnChat) tabBtnChat.addEventListener('click', () => switchTab('chat'));

  function renderStatus(status, nickname) {
    if (nickname) activePlayerNick = nickname;

    if (status === 'approved') {
      isApprovedUser = true;
      unlockChatUI(nickname);

      if (statusContainer) {
        statusContainer.className = 'status-box approved';
        statusIcon.innerHTML = '✓';
        statusHeading.textContent = 'Админ принял вашу заявку, заходите на сервер.';
        statusDesc.textContent = 'Добро пожаловать на сервер Два Блока! Теперь вы можете играть и общаться в чате.';
      }
    } else if (status === 'rejected') {
      isApprovedUser = false;
      lockChatUI();

      if (statusContainer) {
        statusContainer.className = 'status-box rejected';
        statusIcon.innerHTML = '✕';
        statusHeading.textContent = 'Заявка отклонена';
        statusDesc.textContent = 'К сожалению, ваша заявка была отклонена администрацией сервера.';
      }
    } else {
      isApprovedUser = false;
      lockChatUI();

      if (statusContainer) {
        statusContainer.className = 'status-box pending';
        statusIcon.innerHTML = '⏳';
        statusHeading.textContent = 'Вы уже подали заявку, ожидайте админа';
        statusDesc.textContent = 'Ваша анкета находится на рассмотрении администрации проекта «Два Блока». Мы проверяем заявки регулярно.';
      }
    }

    if (nickname && statusNickLine) {
      statusNickLine.innerHTML = `Игровой никнейм: <strong>${escapeHtml(nickname)}</strong>`;
      statusNickLine.style.display = 'inline-block';
    }

    if (statusContainer && currentTab === 'apply') {
      statusContainer.style.display = 'block';
      if (formMainCard) formMainCard.style.display = 'none';
    }
  }

  function unlockChatUI(nick) {
    if (chatForm) chatForm.style.display = 'flex';
    if (chatLockedBanner) chatLockedBanner.style.display = 'none';
    if (chatUserBadge && nick) {
      chatUserBadge.style.display = 'inline-flex';
      chatUserName.textContent = nick;
      chatUserAvatar.src = `https://mc-heads.net/avatar/${encodeURIComponent(nick)}/24`;
    }
  }

  function lockChatUI() {
    if (chatForm) chatForm.style.display = 'none';
    if (chatLockedBanner) chatLockedBanner.style.display = 'block';
    if (chatUserBadge) chatUserBadge.style.display = 'none';
  }

  // Load and listen for application status in Supabase
  if (savedAppId && client) {
    renderStatus('pending', savedNick);

    async function checkAppStatus() {
      try {
        const { data, error } = await client
          .from('applications')
          .select('status, nickname')
          .eq('id', savedAppId)
          .single();

        if (error || !data) {
          localStorage.removeItem('twoblocks_app_id');
          localStorage.removeItem('twoblocks_nick');
          if (statusContainer) statusContainer.style.display = 'none';
          if (formMainCard) formMainCard.style.display = 'block';
          return;
        }

        renderStatus(data.status, data.nickname || savedNick);
      } catch (e) {}
    }

    checkAppStatus();

    client
      .channel('app_status_realtime')
      .on('postgres_changes', { event: 'UPDATE', schema: 'public', table: 'applications', filter: `id=eq.${savedAppId}` }, (payload) => {
        if (payload.new) {
          renderStatus(payload.new.status, payload.new.nickname || savedNick);
        }
      })
      .subscribe();
  }

  // Live Minecraft Avatar Preview
  let avatarTimeout = null;
  if (nicknameInput && avatarBox) {
    nicknameInput.addEventListener('input', () => {
      clearTimeout(avatarTimeout);
      const nick = nicknameInput.value.trim();
      if (!nick) {
        avatarBox.innerHTML = 'Скин';
        return;
      }

      avatarTimeout = setTimeout(() => {
        const img = new Image();
        img.src = `https://mc-heads.net/avatar/${encodeURIComponent(nick)}/32`;
        img.alt = nick;
        img.onload = () => {
          avatarBox.innerHTML = '';
          avatarBox.appendChild(img);
        };
        img.onerror = () => {
          avatarBox.innerHTML = `<img src="https://minotar.net/helm/${encodeURIComponent(nick)}/32.png" alt="${nick}" onerror="this.onerror=null; this.parentElement.innerHTML='Скин';">`;
        };
      }, 350);
    });
  }

  // Clear errors on typing
  if (form) {
    form.querySelectorAll('input, textarea').forEach(el => {
      el.addEventListener('input', () => {
        const wrap = el.closest('.qa-input-bar, .qa-textarea-bar');
        if (wrap) wrap.classList.remove('has-error');
      });
    });
  }

  // ========================================================
  // 9. БАН ВОРДЫ В ПРАВИЛАХ (ОБЯЗАТЕЛЬНО ТЫКНУТЬ ПО КАЖДОМУ)
  // ========================================================
  const banwordChips = Array.from(document.querySelectorAll('.banword-chip'));
  const banwordsProgress = document.getElementById('banwordsProgress');
  const rulesBanwordsSection = document.getElementById('rulesBanwordsSection');
  let checkedBanwords = new Set();
  try {
    const saved = JSON.parse(localStorage.getItem('twoblocks_banwords_checked') || '[]');
    if (Array.isArray(saved)) checkedBanwords = new Set(saved);
  } catch (e) {}

  function areAllBanwordsChecked() {
    return banwordChips.length > 0 && banwordChips.every(c => checkedBanwords.has(c.getAttribute('data-bw')));
  }

  function updateBanwordsUI() {
    let count = 0;
    for (const chip of banwordChips) {
      const key = chip.getAttribute('data-bw');
      if (checkedBanwords.has(key)) {
        chip.classList.add('checked');
        count++;
      } else {
        chip.classList.remove('checked');
      }
    }
    const allDone = count === banwordChips.length && banwordChips.length > 0;
    if (banwordsProgress) {
      banwordsProgress.textContent = allDone
        ? `✓ Все ${banwordChips.length} бан-вордов подтверждены!`
        : `Отмечено: ${count} из ${banwordChips.length} (нажмите на каждое слово)`;
      banwordsProgress.classList.toggle('done', allDone);
    }
    if (closeRulesBtnBottom) {
      if (allDone) {
        closeRulesBtnBottom.textContent = '✓ Принять правила и закрыть';
        closeRulesBtnBottom.classList.add('ready');
      } else {
        closeRulesBtnBottom.textContent = `Отметьте все бан-ворды (${count}/${banwordChips.length})`;
        closeRulesBtnBottom.classList.remove('ready');
      }
    }
  }

  banwordChips.forEach(chip => {
    chip.addEventListener('click', () => {
      const key = chip.getAttribute('data-bw');
      checkedBanwords.add(key);
      try { localStorage.setItem('twoblocks_banwords_checked', JSON.stringify(Array.from(checkedBanwords))); } catch (e) {}
      updateBanwordsUI();
    });
  });
  updateBanwordsUI();

  function openBanwordsPrompt() {
    if (rulesModal) rulesModal.classList.add('active');
    if (rulesBanwordsSection) {
      setTimeout(() => rulesBanwordsSection.scrollIntoView({ behavior: 'smooth', block: 'center' }), 120);
    }
  }

  const rulesCheckBox = document.getElementById('rulesAgreed');
  if (rulesCheckBox) {
    rulesCheckBox.addEventListener('change', () => {
      if (rulesCheckBox.checked && !areAllBanwordsChecked()) {
        rulesCheckBox.checked = false;
        alert('Откройте правила и нажмите на каждый бан-ворд в разделе 9, иначе анкета не отправится!');
        openBanwordsPrompt();
      }
    });
  }

  // Rules Modal Handlers
  if (openRulesBtn && rulesModal) {
    openRulesBtn.addEventListener('click', (e) => {
      e.preventDefault();
      rulesModal.classList.add('active');
    });
  }

  if (closeRulesBtn && rulesModal) {
    closeRulesBtn.addEventListener('click', () => {
      rulesModal.classList.remove('active');
    });
  }

  if (closeRulesBtnBottom && rulesModal) {
    closeRulesBtnBottom.addEventListener('click', () => {
      if (!areAllBanwordsChecked()) {
        if (rulesBanwordsSection) rulesBanwordsSection.scrollIntoView({ behavior: 'smooth', block: 'center' });
        alert('Нажмите на каждый бан-ворд в списке выше, чтобы продолжить!');
        return;
      }
      if (rulesCheckBox) rulesCheckBox.checked = true;
      rulesModal.classList.remove('active');
    });
  }

  if (rulesModal) {
    rulesModal.addEventListener('click', (e) => {
      if (e.target === rulesModal) {
        rulesModal.classList.remove('active');
      }
    });
  }

  // Form submission directly to Supabase
  if (form) {
    form.addEventListener('submit', async (e) => {
      e.preventDefault();

      let hasErrors = false;
      let firstErrorElem = null;

      const flagError = (rowId) => {
        const row = document.getElementById(rowId);
        if (row) {
          const inputWrap = row.querySelector('.qa-input-bar, .qa-textarea-bar');
          if (inputWrap) inputWrap.classList.add('has-error');
          if (!firstErrorElem) firstErrorElem = row;
        }
        hasErrors = true;
      };

      if (!nicknameInput.value.trim()) flagError('row-nickname');
      if (!document.getElementById('ageAndName').value.trim()) flagError('row-age');
      if (!document.getElementById('yearsPlaying').value.trim()) flagError('row-years');

      const stylesSelected = document.querySelectorAll('input[name="gameStyles"]:checked');
      if (stylesSelected.length === 0) {
        flagError('row-styles');
        alert('Пожалуйста, выберите хотя бы один стиль игры в 4 вопросе');
      }

      if (!document.getElementById('plansOnServer').value.trim()) flagError('row-plans');
      if (!document.getElementById('playStyleAndFriends').value.trim()) flagError('row-friends');
      if (!document.getElementById('timeCommitment').value.trim()) flagError('row-time');
      if (!document.getElementById('whyVanilla').value.trim()) flagError('row-why');
      if (!document.getElementById('communityImportance').value.trim()) flagError('row-community');

      const rulesCheck = document.getElementById('rulesAgreed');
      if (!areAllBanwordsChecked()) {
        alert('Необходимо открыть правила (раздел 9 «Бан ворды») и нажать на каждое запрещённое слово!');
        openBanwordsPrompt();
        const rowRules = document.getElementById('row-rules');
        if (rowRules) rowRules.classList.add('has-error');
        if (!firstErrorElem) firstErrorElem = rowRules;
        hasErrors = true;
      } else if (!rulesCheck || !rulesCheck.checked) {
        alert('Для подачи заявки необходимо прочитать правила сервера и поставить галочку согласия!');
        const rowRules = document.getElementById('row-rules');
        if (rowRules) rowRules.classList.add('has-error');
        if (!firstErrorElem) firstErrorElem = rowRules;
        hasErrors = true;
      }

      if (hasErrors) {
        if (firstErrorElem) {
          firstErrorElem.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
        return;
      }

      const nickname = nicknameInput.value.trim();
      const payload = {
        nickname: nickname,
        age_and_name: document.getElementById('ageAndName').value.trim(),
        years_playing: document.getElementById('yearsPlaying').value.trim(),
        game_styles: Array.from(stylesSelected).map(cb => cb.value),
        skill_level: document.querySelector('input[name="skillLevel"]:checked')?.value || 'Средний',
        plans_on_server: document.getElementById('plansOnServer').value.trim(),
        play_style_and_friends: document.getElementById('playStyleAndFriends').value.trim(),
        time_commitment: document.getElementById('timeCommitment').value.trim(),
        why_vanilla: document.getElementById('whyVanilla').value.trim(),
        community_importance: document.getElementById('communityImportance').value.trim(),
        rules_agreed: 'Да',
        microphone: document.querySelector('input[name="microphone"]:checked')?.value || 'Да',
        status: 'pending'
      };

      submitBtn.disabled = true;
      submitBtn.innerHTML = '<span class="spinner"></span> Отправка заявки...';

      try {
        if (!client) throw new Error("Supabase не инициализирован");

        const { data, error } = await client
          .from('applications')
          .insert([payload])
          .select('id')
          .single();

        if (error) throw error;

        const newId = data.id;
        localStorage.setItem('twoblocks_app_id', newId);
        localStorage.setItem('twoblocks_nick', nickname);
        renderStatus('pending', nickname);
        window.scrollTo({ top: 0, behavior: 'smooth' });
      } catch (err) {
        console.error("Submit error:", err);
        alert('Ошибка при отправке: ' + (err.message || err));
      } finally {
        submitBtn.disabled = false;
        submitBtn.textContent = 'Отправить заявку на Два Блока';
      }
    });
  }

  // ========================================================
  // LIVE MINECRAFT SERVER STATUS & PLAYERS MONITOR
  // ========================================================
  const SERVER_IP = '45.93.200.11:25569';
  const headerPill = document.getElementById('headerServerPill');
  const headerDot = document.getElementById('headerServerDot');
  const headerCount = document.getElementById('headerServerCount');
  const heroCount = document.getElementById('heroPlayerCount');
  const heroList = document.getElementById('heroPlayersList');
  const copyIpBtn = document.getElementById('copyIpBtn');
  const copyIpBtnText = document.getElementById('copyIpBtnText');

  function copyServerIp(e) {
    if (e) e.stopPropagation();
    navigator.clipboard.writeText(SERVER_IP).then(() => {
      if (headerPill) {
        headerPill.classList.add('copied');
      }
      const label = document.getElementById('headerServerCount');
      if (label) {
        const oldText = label.textContent;
        label.textContent = 'IP скопирован в буфер! ✓';
        setTimeout(() => {
          label.textContent = oldText;
          if (headerPill) headerPill.classList.remove('copied');
        }, 2000);
      }
      if (copyIpBtn) {
        copyIpBtn.classList.add('copied');
        if (copyIpBtnText) {
          const oldBtnText = copyIpBtnText.textContent;
          copyIpBtnText.textContent = 'Скопировано! ✓';
          setTimeout(() => {
            copyIpBtnText.textContent = oldBtnText;
            copyIpBtn.classList.remove('copied');
          }, 2000);
        }
      }
    }).catch(() => {
      prompt('Скопируйте IP сервера вручную:', SERVER_IP);
    });
  }

  if (headerPill) {
    headerPill.addEventListener('click', copyServerIp);
    headerPill.style.cursor = 'pointer';
    headerPill.setAttribute('title', 'Нажмите, чтобы скопировать IP сервера');
  }
  if (copyIpBtn) copyIpBtn.addEventListener('click', copyServerIp);

  async function updateServerStatus() {
    try {
      // 1. Try Supabase server_online first if available
      if (client) {
        try {
          const { data, error } = await client
            .from('server_online')
            .select('players, updated_at')
            .eq('id', 'current')
            .single();

          if (!error && data && data.updated_at) {
            const ageSec = (Date.now() - new Date(data.updated_at).getTime()) / 1000;
            if (ageSec < 45 && Array.isArray(data.players)) {
              const onlineCount = data.players.length;
              if (headerDot) headerDot.classList.remove('offline');
              if (heroCount) heroCount.textContent = `${onlineCount} из 30 игроков`;
              if (heroList) {
                if (onlineCount > 0) {
                  heroList.innerHTML = data.players.map(pName => {
                    const avatarUrl = `https://mc-heads.net/avatar/${encodeURIComponent(pName)}/24`;
                    return `
                      <div class="player-chip" title="${escapeHtml(pName)}">
                        <img src="${avatarUrl}" class="player-chip-avatar" alt="${escapeHtml(pName)}" onerror="this.onerror=null; this.src='https://minotar.net/helm/${encodeURIComponent(pName)}/24.png';">
                        <span>${escapeHtml(pName)}</span>
                      </div>
                    `;
                  }).join('');
                } else {
                  heroList.innerHTML = `<span class="monitor-empty-players">Сейчас на сервере никого нет. Будь первым!</span>`;
                }
              }
              return;
            }
          }
        } catch (e) {}
      }

      // 2. Fallback to mcapi.us / mcsrvstat.us
      let data = null;
      try {
        const res = await fetch(`https://mcapi.us/server/status?ip=45.93.200.11&port=25569`);
        if (res.ok) {
          const mcapiData = await res.json();
          if (mcapiData && mcapiData.status === 'success' && mcapiData.online) {
            const sample = (mcapiData.players && Array.isArray(mcapiData.players.sample)) ? mcapiData.players.sample : [];
            data = {
              online: true,
              players: {
                online: mcapiData.players ? mcapiData.players.now : sample.length,
                max: mcapiData.players ? mcapiData.players.max : 30,
                list: sample.map(p => (typeof p === 'string' ? p : p.name)).filter(Boolean)
              }
            };
          }
        }
      } catch (e) {}

      if (!data) {
        const res = await fetch(`https://api.mcsrvstat.us/3/${SERVER_IP}`);
        if (!res.ok) throw new Error('Network error');
        data = await res.json();
      }

      if (data && data.online) {
        const onlineCount = (data.players && typeof data.players.online === 'number') ? data.players.online : 0;
        const maxCount = (data.players && typeof data.players.max === 'number') ? data.players.max : 20;
        const playersList = (data.players && Array.isArray(data.players.list)) ? data.players.list : [];

        if (headerDot) headerDot.classList.remove('offline');
        if (heroCount) heroCount.textContent = `${onlineCount} из ${maxCount} игроков`;

        if (heroList) {
          if (playersList.length > 0) {
            heroList.innerHTML = playersList.map(p => {
              const pName = typeof p === 'string' ? p : (p.name || 'Игрок');
              const avatarUrl = `https://mc-heads.net/avatar/${encodeURIComponent(pName)}/24`;
              return `
                <div class="player-chip" title="${escapeHtml(pName)}">
                  <img src="${avatarUrl}" class="player-chip-avatar" alt="${escapeHtml(pName)}" onerror="this.onerror=null; this.src='https://minotar.net/helm/${encodeURIComponent(pName)}/24.png';">
                  <span>${escapeHtml(pName)}</span>
                </div>
              `;
            }).join('');
          } else if (onlineCount > 0) {
            heroList.innerHTML = `<span class="monitor-empty-players">На сервере играет ${onlineCount} чел.</span>`;
          } else {
            heroList.innerHTML = `<span class="monitor-empty-players">Сейчас на сервере никого нет. Будь первым!</span>`;
          }
        }
      } else {
        if (headerDot) headerDot.classList.add('offline');
        if (heroCount) heroCount.textContent = 'Оффлайн';
        if (heroList) {
          heroList.innerHTML = `<span class="monitor-empty-players" style="color: #f87171;">Сервер временно недоступен или перезагружается</span>`;
        }
      }
    } catch (err) {
      console.warn("Server status fetch error:", err);
    }
  }

  updateServerStatus();
  setInterval(updateServerStatus, 30000);

  // NETHER OPENING COUNTDOWN TIMER
  const TARGET_NETHER_TIME = new Date("2026-11-21T19:00:00+03:00").getTime();

  function updateNetherCountdown() {
    if (!headerCount) return;
    const now = Date.now();
    const diff = TARGET_NETHER_TIME - now;

    if (diff <= 0) {
      headerCount.textContent = "Ад открыт!";
      return;
    }

    const days = Math.floor(diff / (1000 * 60 * 60 * 24));
    const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
    const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
    const seconds = Math.floor((diff % (1000 * 60)) / 1000);

    let timeFormatted = "";
    if (days > 0) {
      timeFormatted = `${days}д ${hours}ч ${minutes}м`;
    } else {
      timeFormatted = `${hours}ч ${minutes}м ${seconds}с`;
    }

    headerCount.textContent = `Ад через: ${timeFormatted}`;
  }

  updateNetherCountdown();
  setInterval(updateNetherCountdown, 1000);

  // AMBIENT BACKGROUND MUSIC
  const musicToggleBtn = document.getElementById('musicToggleBtn');
  const musicBtnLabel = document.getElementById('musicBtnLabel');
  const bgAudio = document.getElementById('ambientAudio') || new Audio('./public/music.mp3');
  bgAudio.loop = true;
  bgAudio.volume = 0.28;

  let isMusicPlaying = false;

  function updateMusicUI(playing) {
    isMusicPlaying = playing;
    if (musicToggleBtn) {
      if (playing) {
        musicToggleBtn.classList.add('playing');
      } else {
        musicToggleBtn.classList.remove('playing');
      }
    }
    if (musicBtnLabel) {
      musicBtnLabel.textContent = playing ? 'Музыка вкл.' : 'Музыка выкл.';
    }
  }

  function startMusicImmediate() {
    const playPromise = bgAudio.play();
    if (playPromise !== undefined) {
      playPromise.then(() => {
        updateMusicUI(true);
      }).catch(() => {
        // In modern browsers, audio is strictly muted or gesture-gated until ANY user action occurs
        const startOnUserAction = () => {
          if (!isMusicPlaying) {
            bgAudio.play().then(() => {
              updateMusicUI(true);
            }).catch(() => {});
          }
          ['click', 'pointerdown', 'mousedown', 'keydown', 'scroll', 'touchstart', 'mousemove'].forEach(ev => {
            document.removeEventListener(ev, startOnUserAction);
          });
        };
        ['click', 'pointerdown', 'mousedown', 'keydown', 'scroll', 'touchstart', 'mousemove'].forEach(ev => {
          document.addEventListener(ev, startOnUserAction, { once: true, passive: true });
        });
      });
    }
  }

  startMusicImmediate();

  function toggleMusic() {
    if (!isMusicPlaying) {
      bgAudio.play().then(() => {
        updateMusicUI(true);
      }).catch((e) => {
        console.warn("Audio playback prevented:", e);
      });
    } else {
      bgAudio.pause();
      updateMusicUI(false);
    }
  }

  if (musicToggleBtn) {
    musicToggleBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      toggleMusic();
    });
  }

  // ========================================================
  // TWITCH EMBED PLAYER (channel: twoblocks)
  // ========================================================
  const twitchContainer = document.getElementById('twitchPlayerContainer');
  if (twitchContainer) {
    const currentHost = window.location.hostname || 'localhost';
    const parentDomains = new Set([currentHost, 'localhost', '127.0.0.1']);
    // If hosted on GitHub Pages or custom domain, include it
    if (currentHost.endsWith('.github.io')) {
      parentDomains.add(currentHost);
    }
    const parentParams = Array.from(parentDomains).map(d => `parent=${encodeURIComponent(d)}`).join('&');
    const iframeSrc = `https://player.twitch.tv/?channel=twoblocks&${parentParams}&muted=true&autoplay=false`;

    const iframe = document.createElement('iframe');
    iframe.src = iframeSrc;
    iframe.allowFullscreen = true;
    iframe.setAttribute('scrolling', 'no');
    iframe.setAttribute('frameborder', '0');
    iframe.setAttribute('allow', 'autoplay; fullscreen');
    iframe.title = 'TwoBlocks Twitch Stream';

    twitchContainer.appendChild(iframe);
  }

  // ========================================================
  // LIVE CHAT LOGIC (Website <-> Minecraft Server via Supabase)
  // ========================================================
  let chatMessagesLoaded = false;
  let lastSentTimestamp = parseInt(localStorage.getItem('twoblocks_last_chat_time') || '0', 10);
  const CHAT_COOLDOWN_MS = 30000;

  function scrollChatToBottom() {
    if (chatMessages) {
      setTimeout(() => {
        chatMessages.scrollTop = chatMessages.scrollHeight;
      }, 50);
    }
  }

  async function loadChatMessages() {
    if (!client || !chatMessages) return;

    try {
      const { data, error } = await client
        .from('chat_messages')
        .select('*')
        .order('created_at', { ascending: true })
        .limit(200);

      if (error) throw error;

      if (!data || data.length === 0) {
        chatMessages.innerHTML = `<div class="chat-empty-notice">В чате пока нет сообщений. Будьте первыми!</div>`;
        return;
      }

      const wasScrolledToBottom = (chatMessages.scrollHeight - chatMessages.scrollTop) <= (chatMessages.clientHeight + 60);

      chatMessages.innerHTML = data.map(msg => {
        const author = msg.author || 'Игрок';
        const text = msg.text || '';
        const source = msg.source === 'minecraft' ? 'minecraft' : 'site';
        const sourceLabel = source === 'minecraft' ? 'Сервер' : 'Сайт';

        let timeStr = '';
        if (msg.created_at) {
          try {
            const d = new Date(msg.created_at);
            timeStr = d.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
          } catch (e) {}
        }

        const avatarUrl = `https://mc-heads.net/avatar/${encodeURIComponent(author)}/32`;

        return `
          <div class="chat-msg-row">
            <img src="${avatarUrl}" class="chat-msg-avatar" alt="${escapeHtml(author)}" onerror="this.onerror=null; this.src='https://minotar.net/helm/${encodeURIComponent(author)}/32.png';">
            <div class="chat-msg-bubble">
              <div class="chat-msg-header">
                <span class="chat-msg-author">${escapeHtml(author)}</span>
                <span class="chat-msg-source ${source}">${sourceLabel}</span>
                ${timeStr ? `<span class="chat-msg-time">${timeStr}</span>` : ''}
              </div>
              <div class="chat-msg-text">${escapeHtml(text)}</div>
            </div>
          </div>
        `;
      }).join('');

      if (wasScrolledToBottom || !chatMessagesLoaded) {
        scrollChatToBottom();
        chatMessagesLoaded = true;
      }
    } catch (err) {
      console.warn("Chat read error:", err);
      if (chatMessages) {
        chatMessages.innerHTML = `<div class="chat-empty-notice" style="color: #f87171;">Ошибка подключения к чату: ${err.message || err}</div>`;
      }
    }
  }

  // Subscribe to real-time chat messages via Supabase
  if (client) {
    loadChatMessages();

    client
      .channel('chat_messages_realtime')
      .on('postgres_changes', { event: '*', schema: 'public', table: 'chat_messages' }, () => {
        loadChatMessages();
      })
      .subscribe();
  }

  // Handle Chat Message Sending
  if (chatForm) {
    chatForm.addEventListener('submit', async (e) => {
      e.preventDefault();

      if (!isApprovedUser || !activePlayerNick) {
        alert('Чат доступен только одобренным игрокам сервера.');
        return;
      }

      const msgText = chatInput.value.trim();
      if (!msgText) return;

      const now = Date.now();
      const timeSinceLast = now - lastSentTimestamp;
      if (timeSinceLast < CHAT_COOLDOWN_MS) {
        const remainingSec = Math.ceil((CHAT_COOLDOWN_MS - timeSinceLast) / 1000);
        alert(`Подождите ${remainingSec} сек. перед отправкой следующего сообщения.`);
        return;
      }

      lastSentTimestamp = now;
      localStorage.setItem('twoblocks_last_chat_time', now.toString());
      startCooldownTimer(30);

      chatInput.value = '';

      try {
        if (!client) throw new Error("Supabase не инициализирован");

        const { error } = await client
          .from('chat_messages')
          .insert([{
            author: activePlayerNick,
            text: msgText,
            source: 'site'
          }]);

        if (error) throw error;
        loadChatMessages();
      } catch (err) {
        console.error("Chat send error:", err);
        alert('Ошибка при отправке сообщения: ' + (err.message || err));
      }
    });
  }

  function startCooldownTimer(seconds) {
    if (!chatSendBtn) return;
    chatSendBtn.disabled = true;

    let rem = seconds;
    chatSendBtn.textContent = `${rem}с...`;

    const timer = setInterval(() => {
      rem--;
      if (rem <= 0) {
        clearInterval(timer);
        chatSendBtn.disabled = false;
        chatSendBtn.textContent = 'Отправить';
      } else {
        chatSendBtn.textContent = `${rem}с...`;
      }
    }, 1000);
  }

  const initialRemaining = Math.ceil((CHAT_COOLDOWN_MS - (Date.now() - lastSentTimestamp)) / 1000);
  if (initialRemaining > 0 && initialRemaining <= 30) {
    startCooldownTimer(initialRemaining);
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
