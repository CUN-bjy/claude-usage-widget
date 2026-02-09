// Application state
let credentials = null;
let updateInterval = null;
let countdownInterval = null;
let latestUsageData = null;
let isExpanded = false;
const UPDATE_INTERVAL = 5 * 60 * 1000; // 5 minutes
const WIDGET_HEIGHT_COLLAPSED = 140;
const WIDGET_ROW_HEIGHT = 30;
const COACH_BANNER_HEIGHT = 26;

// Productivity Coach state
let coachEnabled = true;
let prevSessionUtilization = null;
let prevSessionResetsAt = null;
let prevWeeklyUtilization = null;
let prevWeeklyResetsAt = null;
let lastSessionResetNotifTime = 0;
let lastWeeklyResetNotifTime = 0;
let resetBannerOverride = null;
let resetBannerTimeout = null;
let lastCoachMessage = null;

// Usage history for forecast graph
let usageHistory = [];
const MAX_HISTORY_ENTRIES = 2016; // 7 days * 24h * 60min / 5min

// Debug logging — only shows in DevTools (development mode).
// Regular users won't see verbose logs in production.
const DEBUG = (new URLSearchParams(window.location.search)).has('debug');
function debugLog(...args) {
  if (DEBUG) console.log('[Debug]', ...args);
}

// DOM elements
const elements = {
    loadingContainer: document.getElementById('loadingContainer'),
    loginContainer: document.getElementById('loginContainer'),
    noUsageContainer: document.getElementById('noUsageContainer'),
    mainContent: document.getElementById('mainContent'),
    loginStep1: document.getElementById('loginStep1'),
    loginStep2: document.getElementById('loginStep2'),
    autoDetectBtn: document.getElementById('autoDetectBtn'),
    autoDetectError: document.getElementById('autoDetectError'),
    openBrowserLink: document.getElementById('openBrowserLink'),
    nextStepBtn: document.getElementById('nextStepBtn'),
    backStepBtn: document.getElementById('backStepBtn'),
    sessionKeyInput: document.getElementById('sessionKeyInput'),
    connectBtn: document.getElementById('connectBtn'),
    sessionKeyError: document.getElementById('sessionKeyError'),
    refreshBtn: document.getElementById('refreshBtn'),
    minimizeBtn: document.getElementById('minimizeBtn'),
    closeBtn: document.getElementById('closeBtn'),

    sessionPercentage: document.getElementById('sessionPercentage'),
    sessionProgress: document.getElementById('sessionProgress'),
    sessionTimer: document.getElementById('sessionTimer'),
    sessionTimeText: document.getElementById('sessionTimeText'),

    weeklyPercentage: document.getElementById('weeklyPercentage'),
    weeklyProgress: document.getElementById('weeklyProgress'),
    weeklyTimer: document.getElementById('weeklyTimer'),
    weeklyTimeText: document.getElementById('weeklyTimeText'),

    expandToggle: document.getElementById('expandToggle'),
    expandArrow: document.getElementById('expandArrow'),
    expandSection: document.getElementById('expandSection'),
    extraRows: document.getElementById('extraRows'),

    settingsBtn: document.getElementById('settingsBtn'),
    settingsOverlay: document.getElementById('settingsOverlay'),
    closeSettingsBtn: document.getElementById('closeSettingsBtn'),
    logoutBtn: document.getElementById('logoutBtn'),
    coffeeBtn: document.getElementById('coffeeBtn'),

    // Coach
    coachBanner: document.getElementById('coachBanner'),
    coachText: document.getElementById('coachText'),
    coachToggle: document.getElementById('coachToggle'),

    // Graph
    chartBtn: document.getElementById('chartBtn'),
    graphOverlay: document.getElementById('graphOverlay'),
    closeGraphBtn: document.getElementById('closeGraphBtn'),
    forecastGraph: document.getElementById('forecastGraph'),
    graphStats: document.getElementById('graphStats'),
    graphLegend: document.getElementById('graphLegend')
};

// Initialize
async function init() {
    setupEventListeners();

    // Load settings
    const settings = await window.electronAPI.getSettings();
    coachEnabled = settings.coachEnabled !== false;
    elements.coachToggle.checked = coachEnabled;

    // Load usage history
    usageHistory = await window.electronAPI.getUsageHistory();

    credentials = await window.electronAPI.getCredentials();

    if (credentials.sessionKey && credentials.organizationId) {
        showMainContent();
        await fetchUsageData();
        startAutoUpdate();
    } else {
        showLoginRequired();
    }
}

// Event Listeners
function setupEventListeners() {
    // Step 1: Login via BrowserWindow
    elements.autoDetectBtn.addEventListener('click', handleAutoDetect);

    // Step navigation
    elements.nextStepBtn.addEventListener('click', () => {
        elements.loginStep1.style.display = 'none';
        elements.loginStep2.style.display = 'block';
        elements.sessionKeyInput.focus();
    });

    elements.backStepBtn.addEventListener('click', () => {
        elements.loginStep2.style.display = 'none';
        elements.loginStep1.style.display = 'flex';
        elements.sessionKeyError.textContent = '';
    });

    // Open browser link in step 2
    elements.openBrowserLink.addEventListener('click', (e) => {
        e.preventDefault();
        window.electronAPI.openExternal('https://claude.ai');
    });

    // Step 2: Manual sessionKey connect
    elements.connectBtn.addEventListener('click', handleConnect);
    elements.sessionKeyInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') handleConnect();
        elements.sessionKeyError.textContent = '';
    });

    elements.refreshBtn.addEventListener('click', async () => {
        debugLog('Refresh button clicked');
        elements.refreshBtn.classList.add('spinning');
        await fetchUsageData();
        elements.refreshBtn.classList.remove('spinning');
    });

    elements.minimizeBtn.addEventListener('click', () => {
        window.electronAPI.minimizeWindow();
    });

    elements.closeBtn.addEventListener('click', () => {
        window.electronAPI.closeWindow();
    });

    // Expand/collapse toggle
    elements.expandToggle.addEventListener('click', () => {
        isExpanded = !isExpanded;
        elements.expandArrow.classList.toggle('expanded', isExpanded);
        elements.expandSection.style.display = isExpanded ? 'block' : 'none';
        resizeWidget();
    });

    // Settings calls
    elements.settingsBtn.addEventListener('click', () => {
        elements.settingsOverlay.style.display = 'flex';
    });

    elements.closeSettingsBtn.addEventListener('click', () => {
        elements.settingsOverlay.style.display = 'none';
    });

    elements.logoutBtn.addEventListener('click', async () => {
        await window.electronAPI.deleteCredentials();
        credentials = { sessionKey: null, organizationId: null };
        elements.settingsOverlay.style.display = 'none';
        showLoginRequired();
    });

    elements.coffeeBtn.addEventListener('click', () => {
        window.electronAPI.openExternal('https://paypal.me/SlavomirDurej?country.x=GB&locale.x=en_GB');
    });

    // Coach toggle
    elements.coachToggle.addEventListener('change', async () => {
        coachEnabled = elements.coachToggle.checked;
        await window.electronAPI.saveSettings({ coachEnabled });
        updateCoachBanner();
    });

    // Graph overlay
    elements.chartBtn.addEventListener('click', () => {
        renderForecastGraph();
        elements.graphOverlay.style.display = 'flex';
        // Resize window to accommodate graph overlay
        window.electronAPI.resizeWindow(320);
    });

    elements.closeGraphBtn.addEventListener('click', () => {
        elements.graphOverlay.style.display = 'none';
        resizeWidget();
    });

    // Listen for refresh requests from tray
    window.electronAPI.onRefreshUsage(async () => {
        await fetchUsageData();
    });

    // Listen for session expiration events (403 errors)
    window.electronAPI.onSessionExpired(() => {
        debugLog('Session expired event received');
        credentials = { sessionKey: null, organizationId: null };
        showLoginRequired();
    });
}

// Handle manual sessionKey connect
async function handleConnect() {
    const sessionKey = elements.sessionKeyInput.value.trim();
    if (!sessionKey) {
        elements.sessionKeyError.textContent = 'Please paste your session key';
        return;
    }

    elements.connectBtn.disabled = true;
    elements.connectBtn.textContent = '...';
    elements.sessionKeyError.textContent = '';

    try {
        const result = await window.electronAPI.validateSessionKey(sessionKey);
        if (result.success) {
            credentials = { sessionKey, organizationId: result.organizationId };
            await window.electronAPI.saveCredentials(credentials);
            elements.sessionKeyInput.value = '';
            showMainContent();
            await fetchUsageData();
            startAutoUpdate();
        } else {
            elements.sessionKeyError.textContent = result.error || 'Invalid session key';
        }
    } catch (error) {
        elements.sessionKeyError.textContent = 'Connection failed. Check your key.';
    } finally {
        elements.connectBtn.disabled = false;
        elements.connectBtn.textContent = 'Connect';
    }
}

// Handle auto-detect from browser cookies
async function handleAutoDetect() {
    elements.autoDetectBtn.disabled = true;
    elements.autoDetectBtn.textContent = 'Waiting...';
    elements.autoDetectError.textContent = '';

    try {
        const result = await window.electronAPI.detectSessionKey();
        if (!result.success) {
            elements.autoDetectError.textContent = result.error || 'Login failed';
            return;
        }

        // Got sessionKey from login, now validate it
        elements.autoDetectBtn.textContent = 'Validating...';
        const validation = await window.electronAPI.validateSessionKey(result.sessionKey);

        if (validation.success) {
            credentials = {
                sessionKey: result.sessionKey,
                organizationId: validation.organizationId
            };
            await window.electronAPI.saveCredentials(credentials);
            showMainContent();
            await fetchUsageData();
            startAutoUpdate();
        } else {
            elements.autoDetectError.textContent =
                'Session invalid. Try again or use Manual \u2192';
        }
    } catch (error) {
        elements.autoDetectError.textContent = error.message || 'Login failed';
    } finally {
        elements.autoDetectBtn.disabled = false;
        elements.autoDetectBtn.textContent = 'Log in';
    }
}

// Fetch usage data from Claude API
async function fetchUsageData() {
    debugLog('fetchUsageData called');

    if (!credentials.sessionKey || !credentials.organizationId) {
        debugLog('Missing credentials, showing login');
        showLoginRequired();
        return;
    }

    try {
        debugLog('Calling electronAPI.fetchUsageData...');
        const data = await window.electronAPI.fetchUsageData();
        debugLog('Received usage data:', data);
        updateUI(data);
    } catch (error) {
        console.error('Error fetching usage data:', error);
        if (error.message.includes('SessionExpired') || error.message.includes('Unauthorized')) {
            credentials = { sessionKey: null, organizationId: null };
            showLoginRequired();
        } else {
            debugLog('Failed to fetch usage data');
        }
    }
}

// Check if there's no usage data
function hasNoUsage(data) {
    const sessionUtilization = data.five_hour?.utilization || 0;
    const sessionResetsAt = data.five_hour?.resets_at;
    const weeklyUtilization = data.seven_day?.utilization || 0;
    const weeklyResetsAt = data.seven_day?.resets_at;

    return sessionUtilization === 0 && !sessionResetsAt &&
        weeklyUtilization === 0 && !weeklyResetsAt;
}

// Update UI with usage data
// Extra row label mapping for API fields
const EXTRA_ROW_CONFIG = {
    seven_day_sonnet: { label: 'Sonnet (7d)', color: 'weekly' },
    seven_day_opus: { label: 'Opus (7d)', color: 'opus' },
    seven_day_cowork: { label: 'Cowork (7d)', color: 'weekly' },
    seven_day_oauth_apps: { label: 'OAuth Apps (7d)', color: 'weekly' },
    extra_usage: { label: 'Extra Usage', color: 'extra' },
};

function buildExtraRows(data) {
    elements.extraRows.innerHTML = '';
    let count = 0;

    for (const [key, config] of Object.entries(EXTRA_ROW_CONFIG)) {
        const value = data[key];
        // extra_usage is valid with utilization OR balance_cents (prepaid only)
        const hasUtilization = value && value.utilization !== undefined;
        const hasBalance = key === 'extra_usage' && value && value.balance_cents != null;
        if (!hasUtilization && !hasBalance) continue;

        const utilization = value.utilization || 0;
        const resetsAt = value.resets_at;
        const colorClass = config.color;

        let percentageHTML;
        let timerHTML;

        if (key === 'extra_usage') {
            // Percentage area → spending amounts
            if (value.used_cents != null && value.limit_cents != null) {
                const usedDollars = (value.used_cents / 100).toFixed(0);
                const limitDollars = (value.limit_cents / 100).toFixed(0);
                percentageHTML = `<span class="usage-percentage extra-spending">$${usedDollars}/$${limitDollars}</span>`;
            } else {
                percentageHTML = `<span class="usage-percentage">${Math.round(utilization)}%</span>`;
            }
            // Timer area → prepaid balance
            if (value.balance_cents != null) {
                const balanceDollars = (value.balance_cents / 100).toFixed(0);
                timerHTML = `
                    <div class="timer-container">
                        <span class="timer-text extra-balance">Bal $${balanceDollars}</span>
                    </div>
                `;
            } else {
                timerHTML = `<div class="timer-container"></div>`;
            }
        } else {
            percentageHTML = `<span class="usage-percentage">${Math.round(utilization)}%</span>`;
            const totalMinutes = key.includes('seven_day') ? 7 * 24 * 60 : 5 * 60;
            timerHTML = `
                <div class="timer-container">
                    <div class="timer-text" data-resets="${resetsAt || ''}" data-total="${totalMinutes}">--:--</div>
                    <svg class="mini-timer" width="24" height="24" viewBox="0 0 24 24">
                        <circle class="timer-bg" cx="12" cy="12" r="10" />
                        <circle class="timer-progress ${colorClass}" cx="12" cy="12" r="10"
                            style="stroke-dasharray: 63; stroke-dashoffset: 63" />
                    </svg>
                </div>
            `;
        }

        const row = document.createElement('div');
        row.className = 'usage-section';
        row.innerHTML = `
            <span class="usage-label">${config.label}</span>
            <div class="progress-bar">
                <div class="progress-fill ${colorClass}" style="width: ${Math.min(utilization, 100)}%"></div>
            </div>
            ${percentageHTML}
            ${timerHTML}
        `;

        // Apply warning/danger classes
        const progressEl = row.querySelector('.progress-fill');
        if (utilization >= 90) progressEl.classList.add('danger');
        else if (utilization >= 75) progressEl.classList.add('warning');

        elements.extraRows.appendChild(row);
        count++;
    }

    // Hide toggle if no extra rows
    elements.expandToggle.style.display = count > 0 ? 'flex' : 'none';
    if (count === 0 && isExpanded) {
        isExpanded = false;
        elements.expandArrow.classList.remove('expanded');
        elements.expandSection.style.display = 'none';
    }

    return count;
}

function refreshExtraTimers() {
    const timerTexts = elements.extraRows.querySelectorAll('.timer-text');
    const timerCircles = elements.extraRows.querySelectorAll('.timer-progress');

    timerTexts.forEach((textEl, i) => {
        const resetsAt = textEl.dataset.resets;
        const totalMinutes = parseInt(textEl.dataset.total);
        const circleEl = timerCircles[i];
        if (resetsAt && circleEl) {
            updateTimer(circleEl, textEl, resetsAt, totalMinutes);
        }
    });
}

function resizeWidget() {
    const extraCount = elements.extraRows.children.length;
    let height = WIDGET_HEIGHT_COLLAPSED;

    // Add banner height if visible
    if (elements.coachBanner && elements.coachBanner.style.display !== 'none') {
        height += COACH_BANNER_HEIGHT;
    }

    if (isExpanded && extraCount > 0) {
        height += 12 + (extraCount * WIDGET_ROW_HEIGHT);
    }

    window.electronAPI.resizeWindow(height);
}

function updateUI(data) {
    // Detect resets BEFORE overwriting latestUsageData
    detectResets(data);

    latestUsageData = data;

    showMainContent();
    buildExtraRows(data);
    refreshTimers();
    if (isExpanded) refreshExtraTimers();
    recordUsageHistory(data);
    updateCoachBanner();
    resizeWidget();
    startCountdown();
}

// Track if we've already triggered a refresh for expired timers
let sessionResetTriggered = false;
let weeklyResetTriggered = false;

function refreshTimers() {
    if (!latestUsageData) return;

    // Session data
    const sessionUtilization = latestUsageData.five_hour?.utilization || 0;
    const sessionResetsAt = latestUsageData.five_hour?.resets_at;

    // Check if session timer has expired and we need to refresh
    if (sessionResetsAt) {
        const sessionDiff = new Date(sessionResetsAt) - new Date();
        if (sessionDiff <= 0 && !sessionResetTriggered) {
            sessionResetTriggered = true;
            debugLog('Session timer expired, triggering refresh...');
            // Wait a few seconds for the server to update, then refresh
            setTimeout(() => {
                fetchUsageData();
            }, 3000);
        } else if (sessionDiff > 0) {
            sessionResetTriggered = false; // Reset flag when timer is active again
        }
    }

    updateProgressBar(
        elements.sessionProgress,
        elements.sessionPercentage,
        sessionUtilization
    );

    updateTimer(
        elements.sessionTimer,
        elements.sessionTimeText,
        sessionResetsAt,
        5 * 60 // 5 hours in minutes
    );

    // Weekly data
    const weeklyUtilization = latestUsageData.seven_day?.utilization || 0;
    const weeklyResetsAt = latestUsageData.seven_day?.resets_at;

    // Check if weekly timer has expired and we need to refresh
    if (weeklyResetsAt) {
        const weeklyDiff = new Date(weeklyResetsAt) - new Date();
        if (weeklyDiff <= 0 && !weeklyResetTriggered) {
            weeklyResetTriggered = true;
            debugLog('Weekly timer expired, triggering refresh...');
            setTimeout(() => {
                fetchUsageData();
            }, 3000);
        } else if (weeklyDiff > 0) {
            weeklyResetTriggered = false;
        }
    }

    updateProgressBar(
        elements.weeklyProgress,
        elements.weeklyPercentage,
        weeklyUtilization,
        true
    );

    updateTimer(
        elements.weeklyTimer,
        elements.weeklyTimeText,
        weeklyResetsAt,
        7 * 24 * 60 // 7 days in minutes
    );
}

function startCountdown() {
    if (countdownInterval) clearInterval(countdownInterval);
    countdownInterval = setInterval(() => {
        refreshTimers();
        if (isExpanded) refreshExtraTimers();
        updateCoachBanner();
    }, 1000);
}

// Update progress bar
function updateProgressBar(progressElement, percentageElement, value, isWeekly = false) {
    const percentage = Math.min(Math.max(value, 0), 100);

    progressElement.style.width = `${percentage}%`;
    percentageElement.textContent = `${Math.round(percentage)}%`;

    // Update color based on usage level
    progressElement.classList.remove('warning', 'danger');
    if (percentage >= 90) {
        progressElement.classList.add('danger');
    } else if (percentage >= 75) {
        progressElement.classList.add('warning');
    }
}

// Update circular timer
function updateTimer(timerElement, textElement, resetsAt, totalMinutes) {
    if (!resetsAt) {
        textElement.textContent = '--:--';
        textElement.style.opacity = '0.5';
        textElement.title = 'Starts when a message is sent';
        timerElement.style.strokeDashoffset = 63;
        return;
    }

    // Clear the greyed out styling and tooltip when timer is active
    textElement.style.opacity = '1';
    textElement.title = '';

    const resetDate = new Date(resetsAt);
    const now = new Date();
    const diff = resetDate - now;

    if (diff <= 0) {
        textElement.textContent = 'Resetting...';
        timerElement.style.strokeDashoffset = 0;
        return;
    }

    // Calculate remaining time
    const hours = Math.floor(diff / (1000 * 60 * 60));
    const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
    // const seconds = Math.floor((diff % (1000 * 60)) / 1000); // Optional seconds

    // Format time display
    if (hours >= 24) {
        const days = Math.floor(hours / 24);
        const remainingHours = hours % 24;
        textElement.textContent = `${days}d ${remainingHours}h`;
    } else if (hours > 0) {
        textElement.textContent = `${hours}h ${minutes}m`;
    } else {
        textElement.textContent = `${minutes}m`;
    }

    // Calculate progress (elapsed percentage)
    const totalMs = totalMinutes * 60 * 1000;
    const elapsedMs = totalMs - diff;
    const elapsedPercentage = (elapsedMs / totalMs) * 100;

    // Update circle (63 is ~2*pi*10)
    const circumference = 63;
    const offset = circumference - (elapsedPercentage / 100) * circumference;
    timerElement.style.strokeDashoffset = offset;

    // Update color based on remaining time
    timerElement.classList.remove('warning', 'danger');
    if (elapsedPercentage >= 90) {
        timerElement.classList.add('danger');
    } else if (elapsedPercentage >= 75) {
        timerElement.classList.add('warning');
    }
}

// ===== Productivity Coach =====

function formatShortTime(ms) {
    if (ms <= 0) return 'now';
    const totalMin = Math.floor(ms / 60000);
    if (totalMin < 60) return totalMin + 'm';
    const h = Math.floor(totalMin / 60);
    const m = totalMin % 60;
    if (h >= 24) {
        const d = Math.floor(h / 24);
        const rh = h % 24;
        return d + 'd ' + rh + 'h';
    }
    return h + 'h ' + m + 'm';
}

function evaluateCoachNotification() {
    if (!coachEnabled) return null;
    if (!latestUsageData) return null;

    // Temporary reset override takes priority
    if (resetBannerOverride) return resetBannerOverride;

    const sessionUtil = latestUsageData.five_hour?.utilization || 0;
    const sessionResetsAt = latestUsageData.five_hour?.resets_at;
    const weeklyUtil = latestUsageData.seven_day?.utilization || 0;
    const weeklyResetsAt = latestUsageData.seven_day?.resets_at;

    const now = Date.now();

    function msUntilReset(isoDate) {
        if (!isoDate) return Infinity;
        return new Date(isoDate).getTime() - now;
    }

    const sessionMs = msUntilReset(sessionResetsAt);
    const weeklyMs = msUntilReset(weeklyResetsAt);
    const sessionMin = sessionMs / 60000;
    const weeklyDays = weeklyMs / (24 * 60 * 60 * 1000);

    // H: session maxed out
    if (sessionUtil >= 100) {
        return {
            message: `Session maxed out \u2014 resets in ${formatShortTime(sessionMs)}. Switch tasks!`,
            severity: 'danger'
        };
    }

    // B: session > 80% and reset < 30min
    if (sessionUtil > 80 && sessionMin < 30 && sessionMin > 0) {
        return {
            message: 'Reset imminent! Take a break, come back fully charged',
            severity: 'warning'
        };
    }

    // A: session < 30% and reset < 1h
    if (sessionUtil < 30 && sessionUtil > 0 && sessionMin < 60 && sessionMin > 0) {
        return {
            message: `Session resets in ${formatShortTime(sessionMs)} \u2014 don\u2019t waste the remaining capacity!`,
            severity: 'warning'
        };
    }

    // D: weekly > 70% and reset > 2 days
    if (weeklyUtil > 70 && weeklyDays > 2) {
        return {
            message: '70%+ weekly used \u2014 focus on high-impact tasks only',
            severity: 'warning'
        };
    }

    // E: weekly < 40% and reset < 1 day
    if (weeklyUtil < 40 && weeklyUtil > 0 && weeklyDays < 1 && weeklyDays > 0) {
        return {
            message: 'Last sprint! Use your remaining weekly capacity before reset',
            severity: 'warning'
        };
    }

    // C: weekly < 50% and reset > 3 days
    if (weeklyUtil < 50 && weeklyDays > 3) {
        return {
            message: 'Plenty of capacity \u2014 perfect time for deep work!',
            severity: 'positive'
        };
    }

    return null;
}

function updateCoachBanner() {
    const notification = evaluateCoachNotification();

    if (!notification) {
        if (elements.coachBanner.style.display !== 'none') {
            elements.coachBanner.style.display = 'none';
            lastCoachMessage = null;
            resizeWidget();
        }
        return;
    }

    // Skip DOM update if message hasn't changed
    if (notification.message === lastCoachMessage) return;
    lastCoachMessage = notification.message;

    elements.coachText.textContent = notification.message;
    elements.coachBanner.classList.remove('warning', 'danger', 'positive');
    if (notification.severity !== 'neutral') {
        elements.coachBanner.classList.add(notification.severity);
    }

    if (elements.coachBanner.style.display === 'none') {
        elements.coachBanner.style.display = 'flex';
        resizeWidget();
    }
}

// ===== Reset Detection =====

function detectResets(data) {
    if (!coachEnabled) {
        // Still track prev values even when disabled, for correct detection when re-enabled
        prevSessionUtilization = data.five_hour?.utilization || 0;
        prevSessionResetsAt = data.five_hour?.resets_at || null;
        prevWeeklyUtilization = data.seven_day?.utilization || 0;
        prevWeeklyResetsAt = data.seven_day?.resets_at || null;
        return;
    }

    const now = Date.now();
    const NOTIF_COOLDOWN = 60000;

    const sessionUtil = data.five_hour?.utilization || 0;
    const sessionResetsAt = data.five_hour?.resets_at || null;
    const weeklyUtil = data.seven_day?.utilization || 0;
    const weeklyResetsAt = data.seven_day?.resets_at || null;

    // Session reset: utilization dropped to 0 or resets_at shifted forward
    const sessionDidReset =
        prevSessionUtilization !== null &&
        prevSessionUtilization > 0 &&
        sessionUtil === 0;
    const sessionTimerShifted =
        prevSessionResetsAt !== null &&
        sessionResetsAt !== null &&
        prevSessionResetsAt !== sessionResetsAt &&
        new Date(sessionResetsAt) > new Date(prevSessionResetsAt);

    if ((sessionDidReset || sessionTimerShifted) &&
        (now - lastSessionResetNotifTime > NOTIF_COOLDOWN)) {
        lastSessionResetNotifTime = now;
        window.electronAPI.showNotification({
            title: 'Claude Usage',
            body: 'Fully charged! Start a new session now'
        });
        showResetBanner({
            message: 'Fully charged! Start a new session now',
            severity: 'positive'
        });
    }

    // Weekly reset
    const weeklyDidReset =
        prevWeeklyUtilization !== null &&
        prevWeeklyUtilization > 0 &&
        weeklyUtil === 0;
    const weeklyTimerShifted =
        prevWeeklyResetsAt !== null &&
        weeklyResetsAt !== null &&
        prevWeeklyResetsAt !== weeklyResetsAt &&
        new Date(weeklyResetsAt) > new Date(prevWeeklyResetsAt);

    if ((weeklyDidReset || weeklyTimerShifted) &&
        (now - lastWeeklyResetNotifTime > NOTIF_COOLDOWN)) {
        lastWeeklyResetNotifTime = now;
        window.electronAPI.showNotification({
            title: 'Claude Usage',
            body: 'Weekly reset! Let\u2019s make this week count'
        });
        showResetBanner({
            message: 'Weekly reset! Let\u2019s make this week count',
            severity: 'positive'
        });
    }

    prevSessionUtilization = sessionUtil;
    prevSessionResetsAt = sessionResetsAt;
    prevWeeklyUtilization = weeklyUtil;
    prevWeeklyResetsAt = weeklyResetsAt;
}

function showResetBanner(notification) {
    if (resetBannerTimeout) clearTimeout(resetBannerTimeout);
    resetBannerOverride = notification;
    updateCoachBanner();

    resetBannerTimeout = setTimeout(() => {
        resetBannerOverride = null;
        updateCoachBanner();
    }, 30000);
}

// ===== Usage History for Forecast Graph =====

function recordUsageHistory(data) {
    const weeklyUtil = data.seven_day?.utilization;
    if (weeklyUtil === undefined) return;

    const entry = {
        timestamp: Date.now(),
        utilization: weeklyUtil
    };

    usageHistory.push(entry);

    // Cap at max entries
    if (usageHistory.length > MAX_HISTORY_ENTRIES) {
        usageHistory = usageHistory.slice(-MAX_HISTORY_ENTRIES);
    }

    // Persist asynchronously
    window.electronAPI.saveUsageHistory(usageHistory);
}

function clearHistoryOnWeeklyReset() {
    usageHistory = [];
    window.electronAPI.clearUsageHistory();
}

// ===== Forecast Graph =====

function calculateBurningRatio() {
    if (!latestUsageData) return null;

    const weeklyResetsAt = latestUsageData.seven_day?.resets_at;
    const weeklyUtil = latestUsageData.seven_day?.utilization || 0;
    if (!weeklyResetsAt || weeklyUtil === 0) return null;

    const resetTime = new Date(weeklyResetsAt).getTime();
    const weekStart = resetTime - (7 * 24 * 60 * 60 * 1000);
    const now = Date.now();
    const elapsedHours = (now - weekStart) / (1000 * 60 * 60);

    if (elapsedHours <= 0) return null;
    return weeklyUtil / elapsedHours; // % per hour
}

function renderForecastGraph() {
    const svg = elements.forecastGraph;
    const NS = 'http://www.w3.org/2000/svg';

    // Clear previous content
    svg.innerHTML = '';

    if (!latestUsageData || !latestUsageData.seven_day?.resets_at) {
        const text = document.createElementNS(NS, 'text');
        text.setAttribute('x', '220');
        text.setAttribute('y', '100');
        text.setAttribute('text-anchor', 'middle');
        text.setAttribute('class', 'graph-axis-label');
        text.setAttribute('font-size', '11');
        text.textContent = 'No weekly usage data available';
        svg.appendChild(text);
        elements.graphStats.innerHTML = '';
        elements.graphLegend.innerHTML = '';
        return;
    }

    // Graph dimensions (within viewBox 440x200)
    const margin = { top: 15, right: 15, bottom: 25, left: 35 };
    const width = 440 - margin.left - margin.right;
    const height = 200 - margin.top - margin.bottom;

    const weeklyResetsAt = latestUsageData.seven_day.resets_at;
    const resetTime = new Date(weeklyResetsAt).getTime();
    const weekStart = resetTime - (7 * 24 * 60 * 60 * 1000);
    const now = Date.now();

    // Scale helpers
    function xScale(timestamp) {
        const ratio = (timestamp - weekStart) / (resetTime - weekStart);
        return margin.left + ratio * width;
    }
    function yScale(util) {
        return margin.top + height - (util / 100) * height;
    }

    // Grid lines (horizontal at 25%, 50%, 75%, 100%)
    [25, 50, 75, 100].forEach(val => {
        const line = document.createElementNS(NS, 'line');
        line.setAttribute('x1', margin.left);
        line.setAttribute('x2', margin.left + width);
        line.setAttribute('y1', yScale(val));
        line.setAttribute('y2', yScale(val));
        line.setAttribute('class', 'graph-grid-line');
        svg.appendChild(line);

        const label = document.createElementNS(NS, 'text');
        label.setAttribute('x', margin.left - 5);
        label.setAttribute('y', yScale(val) + 3);
        label.setAttribute('text-anchor', 'end');
        label.setAttribute('class', 'graph-axis-label');
        label.textContent = val + '%';
        svg.appendChild(label);
    });

    // Danger zone (above 80%)
    const dangerRect = document.createElementNS(NS, 'rect');
    dangerRect.setAttribute('x', margin.left);
    dangerRect.setAttribute('y', yScale(100));
    dangerRect.setAttribute('width', width);
    dangerRect.setAttribute('height', yScale(80) - yScale(100));
    dangerRect.setAttribute('class', 'graph-danger-zone');
    svg.appendChild(dangerRect);

    const dangerLine = document.createElementNS(NS, 'line');
    dangerLine.setAttribute('x1', margin.left);
    dangerLine.setAttribute('x2', margin.left + width);
    dangerLine.setAttribute('y1', yScale(80));
    dangerLine.setAttribute('y2', yScale(80));
    dangerLine.setAttribute('class', 'graph-danger-line');
    svg.appendChild(dangerLine);

    // Axes
    const xAxis = document.createElementNS(NS, 'line');
    xAxis.setAttribute('x1', margin.left);
    xAxis.setAttribute('x2', margin.left + width);
    xAxis.setAttribute('y1', margin.top + height);
    xAxis.setAttribute('y2', margin.top + height);
    xAxis.setAttribute('class', 'graph-axis');
    svg.appendChild(xAxis);

    const yAxis = document.createElementNS(NS, 'line');
    yAxis.setAttribute('x1', margin.left);
    yAxis.setAttribute('x2', margin.left);
    yAxis.setAttribute('y1', margin.top);
    yAxis.setAttribute('y2', margin.top + height);
    yAxis.setAttribute('class', 'graph-axis');
    svg.appendChild(yAxis);

    // X-axis day labels
    for (let d = 0; d <= 7; d++) {
        const t = weekStart + d * 24 * 60 * 60 * 1000;
        const label = document.createElementNS(NS, 'text');
        label.setAttribute('x', xScale(t));
        label.setAttribute('y', margin.top + height + 15);
        label.setAttribute('text-anchor', 'middle');
        label.setAttribute('class', 'graph-axis-label');
        label.textContent = d === 0 ? 'Start' : d === 7 ? 'Reset' : 'D' + d;
        svg.appendChild(label);
    }

    // History line — filter to current week only
    const weekHistory = usageHistory.filter(
        e => e.timestamp >= weekStart && e.timestamp <= resetTime
    );

    if (weekHistory.length > 1) {
        const points = weekHistory.map(e =>
            `${xScale(e.timestamp)},${yScale(e.utilization)}`
        ).join(' ');

        const polyline = document.createElementNS(NS, 'polyline');
        polyline.setAttribute('points', points);
        polyline.setAttribute('class', 'graph-history-line');
        svg.appendChild(polyline);
    }

    // Current point
    const currentUtil = latestUsageData.seven_day.utilization || 0;
    const currentX = xScale(Math.min(now, resetTime));
    const currentY = yScale(currentUtil);

    const currentDot = document.createElementNS(NS, 'circle');
    currentDot.setAttribute('cx', currentX);
    currentDot.setAttribute('cy', currentY);
    currentDot.setAttribute('r', '4');
    currentDot.setAttribute('class', 'graph-current-dot');
    svg.appendChild(currentDot);

    const currentLabel = document.createElementNS(NS, 'text');
    currentLabel.setAttribute('x', currentX);
    currentLabel.setAttribute('y', currentY - 10);
    currentLabel.setAttribute('text-anchor', 'middle');
    currentLabel.setAttribute('class', 'graph-current-label');
    currentLabel.textContent = Math.round(currentUtil) + '%';
    svg.appendChild(currentLabel);

    // Projection line
    const burningRatio = calculateBurningRatio();
    let depletionTime = null;

    if (burningRatio && burningRatio > 0 && currentUtil < 100) {
        const hoursTo100 = (100 - currentUtil) / burningRatio;
        depletionTime = now + hoursTo100 * 60 * 60 * 1000;

        const projEndTime = Math.min(depletionTime, resetTime);
        const projEndUtil = Math.min(
            currentUtil + burningRatio * ((projEndTime - now) / (1000 * 60 * 60)),
            100
        );

        const projLine = document.createElementNS(NS, 'line');
        projLine.setAttribute('x1', currentX);
        projLine.setAttribute('y1', currentY);
        projLine.setAttribute('x2', xScale(projEndTime));
        projLine.setAttribute('y2', yScale(projEndUtil));
        projLine.setAttribute('class', 'graph-projection-line');
        svg.appendChild(projLine);

        // Depletion marker if it's before reset
        if (depletionTime < resetTime) {
            const depX = xScale(depletionTime);
            const depY = yScale(100);

            const depDot = document.createElementNS(NS, 'circle');
            depDot.setAttribute('cx', depX);
            depDot.setAttribute('cy', depY);
            depDot.setAttribute('r', '3');
            depDot.setAttribute('class', 'graph-depletion-dot');
            svg.appendChild(depDot);

            const timeUntil = formatShortTime(depletionTime - now);
            const depLabel = document.createElementNS(NS, 'text');
            depLabel.setAttribute('x', depX);
            depLabel.setAttribute('y', depY - 8);
            depLabel.setAttribute('text-anchor', 'middle');
            depLabel.setAttribute('class', 'graph-depletion-label');
            depLabel.textContent = '100% in ' + timeUntil;
            svg.appendChild(depLabel);
        }
    }

    // Stats bar
    const elapsedDays = ((now - weekStart) / (24 * 60 * 60 * 1000)).toFixed(1);
    const ratioText = burningRatio ? (burningRatio.toFixed(1) + '%/h') : 'N/A';
    const depletionText = depletionTime
        ? (depletionTime < resetTime ? formatShortTime(depletionTime - now) : 'Safe')
        : 'N/A';

    elements.graphStats.innerHTML = `
        <span class="graph-stat">Rate: <span class="graph-stat-value">${ratioText}</span></span>
        <span class="graph-stat">Day: <span class="graph-stat-value">${elapsedDays}/7</span></span>
        <span class="graph-stat">Depletion: <span class="graph-stat-value">${depletionText}</span></span>
    `;

    // Legend
    elements.graphLegend.innerHTML = `
        <span class="graph-legend-item">
            <span class="graph-legend-line actual"></span>Actual
        </span>
        <span class="graph-legend-item">
            <span class="graph-legend-line projected"></span>Projected
        </span>
        <span class="graph-legend-item">
            <span class="graph-legend-line danger"></span>Danger zone
        </span>
    `;
}

// UI State Management
function showLoginRequired() {
    elements.loadingContainer.style.display = 'none';
    elements.loginContainer.style.display = 'flex';
    elements.noUsageContainer.style.display = 'none';
    elements.mainContent.style.display = 'none';
    // Reset to step 1
    elements.loginStep1.style.display = 'flex';
    elements.loginStep2.style.display = 'none';
    elements.sessionKeyError.textContent = '';
    elements.sessionKeyInput.value = '';
    stopAutoUpdate();
}

function showNoUsage() {
    elements.loadingContainer.style.display = 'none';
    elements.loginContainer.style.display = 'none';
    elements.noUsageContainer.style.display = 'flex';
    elements.mainContent.style.display = 'none';
}

function showMainContent() {
    elements.loadingContainer.style.display = 'none';
    elements.loginContainer.style.display = 'none';
    elements.noUsageContainer.style.display = 'none';
    elements.mainContent.style.display = 'block';
}

// Auto-update management
function startAutoUpdate() {
    stopAutoUpdate();
    updateInterval = setInterval(() => {
        fetchUsageData();
    }, UPDATE_INTERVAL);
}

function stopAutoUpdate() {
    if (updateInterval) {
        clearInterval(updateInterval);
        updateInterval = null;
    }
}

// Add spinning animation for refresh button
const style = document.createElement('style');
style.textContent = `
    @keyframes spin-refresh {
        from { transform: rotate(0deg); }
        to { transform: rotate(360deg); }
    }

    .refresh-btn.spinning svg {
        animation: spin-refresh 1s linear;
    }
`;
document.head.appendChild(style);

// Start the application
init();

// Cleanup on unload
window.addEventListener('beforeunload', () => {
    stopAutoUpdate();
    if (countdownInterval) clearInterval(countdownInterval);
});
