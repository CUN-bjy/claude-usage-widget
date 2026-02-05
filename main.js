const { app, BrowserWindow, ipcMain, Tray, Menu, session, shell } = require('electron');
const path = require('path');
const Store = require('electron-store');
const axios = require('axios');

const store = new Store({
  encryptionKey: 'claude-widget-secure-key-2024'
});

// Chrome User-Agent to prevent Electron detection and desktop app redirects
const CHROME_USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';

// Possible session cookie names (Claude may have changed these)
const SESSION_COOKIE_NAMES = ['sessionKey', '__Secure-next-auth.session-token', 'session', '__cf_bm', 'lastActiveOrg'];

let mainWindow = null;
let loginWindow = null;
let silentLoginWindow = null;
let tray = null;

// Window configuration
const WIDGET_WIDTH = 480;
const WIDGET_HEIGHT = 140;

function createMainWindow() {
  // Load saved position or use defaults
  const savedPosition = store.get('windowPosition');
  const windowOptions = {
    width: WIDGET_WIDTH,
    height: WIDGET_HEIGHT,
    frame: false,
    transparent: true,
    alwaysOnTop: true,
    resizable: false,
    skipTaskbar: false,
    icon: path.join(__dirname, 'assets/icon.ico'),
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      preload: path.join(__dirname, 'preload.js')
    }
  };

  // Apply saved position if it exists
  if (savedPosition) {
    windowOptions.x = savedPosition.x;
    windowOptions.y = savedPosition.y;
  }

  mainWindow = new BrowserWindow(windowOptions);

  mainWindow.loadFile('src/renderer/index.html');

  // Make window draggable
  mainWindow.setAlwaysOnTop(true, 'floating');
  mainWindow.setVisibleOnAllWorkspaces(true);

  // Save position when window is moved
  mainWindow.on('move', () => {
    const position = mainWindow.getBounds();
    store.set('windowPosition', { x: position.x, y: position.y });
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });

  // Development tools
  if (process.env.NODE_ENV === 'development') {
    mainWindow.webContents.openDevTools({ mode: 'detach' });
  }
}

function createLoginWindow() {
  loginWindow = new BrowserWindow({
    width: 800,
    height: 700,
    parent: mainWindow,
    modal: true,
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      // Spoof User-Agent to look like regular Chrome browser
      userAgent: CHROME_USER_AGENT
    }
  });

  // Set User-Agent at session level to prevent Electron detection
  loginWindow.webContents.setUserAgent(CHROME_USER_AGENT);

  // Block navigation to external protocols (claude://, etc.) that trigger desktop app
  loginWindow.webContents.on('will-navigate', (event, url) => {
    console.log('[Login] will-navigate:', url);

    // Block about:blank redirects (infinite loop trigger)
    if (url === 'about:blank') {
      console.log('[Login] Blocking about:blank redirect');
      event.preventDefault();
      return;
    }

    // Block custom protocol handlers that open desktop app
    if (url.startsWith('claude://') || url.startsWith('anthropic://')) {
      console.log('[Login] Blocking desktop app protocol:', url);
      event.preventDefault();
      return;
    }

    // Only allow https claude.ai URLs
    if (!url.startsWith('https://claude.ai') &&
        !url.startsWith('https://accounts.google.com') &&
        !url.startsWith('https://appleid.apple.com') &&
        !url.startsWith('https://www.google.com') &&
        !url.startsWith('https://accounts.anthropic.com')) {
      console.log('[Login] Blocking external URL:', url);
      event.preventDefault();
      return;
    }
  });

  // Block new windows/popups that might redirect to desktop app
  loginWindow.webContents.setWindowOpenHandler(({ url }) => {
    console.log('[Login] Attempted to open new window:', url);

    // Block desktop app protocols
    if (url.startsWith('claude://') || url.startsWith('anthropic://') || url === 'about:blank') {
      console.log('[Login] Blocking new window for:', url);
      return { action: 'deny' };
    }

    // Allow OAuth popups but open in same window
    if (url.startsWith('https://accounts.google.com') ||
        url.startsWith('https://appleid.apple.com') ||
        url.startsWith('https://accounts.anthropic.com')) {
      loginWindow.loadURL(url);
      return { action: 'deny' };
    }

    return { action: 'deny' };
  });

  loginWindow.loadURL('https://claude.ai/login');

  let loginCheckInterval = null;
  let hasLoggedIn = false;

  // Function to check login status - tries multiple possible cookie names
  async function checkLoginStatus() {
    if (hasLoggedIn || !loginWindow) return;

    try {
      // Try to find session cookie with different possible names
      let sessionKey = null;

      for (const cookieName of SESSION_COOKIE_NAMES) {
        const cookies = await session.defaultSession.cookies.get({
          url: 'https://claude.ai',
          name: cookieName
        });

        if (cookies.length > 0) {
          // For sessionKey, use the value directly
          if (cookieName === 'sessionKey') {
            sessionKey = cookies[0].value;
            console.log(`[Login] Found cookie '${cookieName}':`, sessionKey.substring(0, 20) + '...');
            break;
          }
        }
      }

      // Also try getting all cookies if sessionKey wasn't found
      if (!sessionKey) {
        const allCookies = await session.defaultSession.cookies.get({ url: 'https://claude.ai' });
        console.log('[Login] All cookies:', allCookies.map(c => c.name));

        // Look for sessionKey in all cookies
        const sessionCookie = allCookies.find(c => c.name === 'sessionKey');
        if (sessionCookie) {
          sessionKey = sessionCookie.value;
          console.log('[Login] Found sessionKey in all cookies');
        }
      }

      if (sessionKey) {
        console.log('Session key found, attempting to get org ID...');

        // Fetch org ID from API
        let orgId = null;
        try {
          const response = await axios.get('https://claude.ai/api/organizations', {
            headers: {
              'Cookie': `sessionKey=${sessionKey}`,
              'User-Agent': CHROME_USER_AGENT
            }
          });

          if (response.data && Array.isArray(response.data) && response.data.length > 0) {
            orgId = response.data[0].uuid || response.data[0].id;
            console.log('Org ID fetched from API:', orgId);
          }
        } catch (err) {
          console.log('API not ready yet:', err.message);
        }

        if (sessionKey && orgId) {
          hasLoggedIn = true;
          if (loginCheckInterval) {
            clearInterval(loginCheckInterval);
            loginCheckInterval = null;
          }

          console.log('Sending login-success to main window...');
          store.set('sessionKey', sessionKey);
          store.set('organizationId', orgId);

          if (mainWindow) {
            mainWindow.webContents.send('login-success', { sessionKey, organizationId: orgId });
            console.log('login-success sent');
          } else {
            console.error('mainWindow is null, cannot send login-success');
          }

          loginWindow.close();
        }
      }
    } catch (error) {
      console.error('Error in login check:', error);
    }
  }

  // Handle did-fail-load to detect blocked redirects
  loginWindow.webContents.on('did-fail-load', (event, errorCode, errorDescription, validatedURL) => {
    console.log('[Login] did-fail-load:', errorCode, errorDescription, validatedURL);
    // If it failed due to blocked redirect, stay on current page
    if (validatedURL === 'about:blank' || validatedURL.startsWith('claude://')) {
      console.log('[Login] Ignoring failed load for blocked URL');
    }
  });

  // Check on page load
  loginWindow.webContents.on('did-finish-load', async () => {
    const url = loginWindow.webContents.getURL();
    console.log('Login page loaded:', url);

    if (url.includes('claude.ai')) {
      await checkLoginStatus();
    }
  });

  // Also check on navigation (URL changes)
  loginWindow.webContents.on('did-navigate', async (event, url) => {
    console.log('Navigated to:', url);
    if (url.includes('claude.ai')) {
      await checkLoginStatus();
    }
  });

  // Poll periodically in case the session becomes ready without a page navigation
  loginCheckInterval = setInterval(async () => {
    if (!hasLoggedIn && loginWindow) {
      await checkLoginStatus();
    } else if (loginCheckInterval) {
      clearInterval(loginCheckInterval);
      loginCheckInterval = null;
    }
  }, 2000);

  loginWindow.on('closed', () => {
    if (loginCheckInterval) {
      clearInterval(loginCheckInterval);
      loginCheckInterval = null;
    }
    loginWindow = null;
  });
}

// Attempt silent login in a hidden browser window
async function attemptSilentLogin() {
  console.log('[Main] Attempting silent login...');

  // Notify renderer that we're trying to auto-login
  if (mainWindow) {
    mainWindow.webContents.send('silent-login-started');
  }

  return new Promise((resolve) => {
    silentLoginWindow = new BrowserWindow({
      width: 800,
      height: 700,
      show: false, // Hidden window
      webPreferences: {
        nodeIntegration: false,
        contextIsolation: true,
        userAgent: CHROME_USER_AGENT
      }
    });

    // Set User-Agent to prevent Electron detection
    silentLoginWindow.webContents.setUserAgent(CHROME_USER_AGENT);

    // Block navigation to external protocols (claude://, etc.) that trigger desktop app
    silentLoginWindow.webContents.on('will-navigate', (event, url) => {
      console.log('[SilentLogin] will-navigate:', url);

      if (url === 'about:blank' || url.startsWith('claude://') || url.startsWith('anthropic://')) {
        console.log('[SilentLogin] Blocking redirect:', url);
        event.preventDefault();
        return;
      }

      if (!url.startsWith('https://claude.ai') &&
          !url.startsWith('https://accounts.google.com') &&
          !url.startsWith('https://appleid.apple.com') &&
          !url.startsWith('https://www.google.com') &&
          !url.startsWith('https://accounts.anthropic.com')) {
        console.log('[SilentLogin] Blocking external URL:', url);
        event.preventDefault();
        return;
      }
    });

    // Block new windows
    silentLoginWindow.webContents.setWindowOpenHandler(({ url }) => {
      console.log('[SilentLogin] Blocking new window:', url);
      return { action: 'deny' };
    });

    silentLoginWindow.loadURL('https://claude.ai/login');

    let loginCheckInterval = null;
    let hasLoggedIn = false;
    const SILENT_LOGIN_TIMEOUT = 15000; // 15 seconds timeout

    // Function to check login status - tries multiple possible cookie names
    async function checkLoginStatus() {
      if (hasLoggedIn || !silentLoginWindow) return;

      try {
        // Try to find session cookie with different possible names
        let sessionKey = null;

        for (const cookieName of SESSION_COOKIE_NAMES) {
          const cookies = await session.defaultSession.cookies.get({
            url: 'https://claude.ai',
            name: cookieName
          });

          if (cookies.length > 0 && cookieName === 'sessionKey') {
            sessionKey = cookies[0].value;
            console.log(`[SilentLogin] Found cookie '${cookieName}'`);
            break;
          }
        }

        // Also try getting all cookies if sessionKey wasn't found
        if (!sessionKey) {
          const allCookies = await session.defaultSession.cookies.get({ url: 'https://claude.ai' });
          const sessionCookie = allCookies.find(c => c.name === 'sessionKey');
          if (sessionCookie) {
            sessionKey = sessionCookie.value;
            console.log('[SilentLogin] Found sessionKey in all cookies');
          }
        }

        if (sessionKey) {
          console.log('[Main] Silent login: Session key found, attempting to get org ID...');

          // Fetch org ID from API
          let orgId = null;
          try {
            const response = await axios.get('https://claude.ai/api/organizations', {
              headers: {
                'Cookie': `sessionKey=${sessionKey}`,
                'User-Agent': CHROME_USER_AGENT
              }
            });

            if (response.data && Array.isArray(response.data) && response.data.length > 0) {
              orgId = response.data[0].uuid || response.data[0].id;
              console.log('[Main] Silent login: Org ID fetched from API:', orgId);
            }
          } catch (err) {
            console.log('[Main] Silent login: API not ready yet:', err.message);
          }

          if (sessionKey && orgId) {
            hasLoggedIn = true;
            if (loginCheckInterval) {
              clearInterval(loginCheckInterval);
              loginCheckInterval = null;
            }

            console.log('[Main] Silent login successful!');
            store.set('sessionKey', sessionKey);
            store.set('organizationId', orgId);

            if (mainWindow) {
              mainWindow.webContents.send('login-success', { sessionKey, organizationId: orgId });
            }

            silentLoginWindow.close();
            resolve(true);
          }
        }
      } catch (error) {
        console.error('[Main] Silent login check error:', error);
      }
    }

    // Check on page load
    silentLoginWindow.webContents.on('did-finish-load', async () => {
      const url = silentLoginWindow.webContents.getURL();
      console.log('[Main] Silent login page loaded:', url);

      if (url.includes('claude.ai')) {
        await checkLoginStatus();
      }
    });

    // Also check on navigation
    silentLoginWindow.webContents.on('did-navigate', async (event, url) => {
      console.log('[Main] Silent login navigated to:', url);
      if (url.includes('claude.ai')) {
        await checkLoginStatus();
      }
    });

    // Poll periodically
    loginCheckInterval = setInterval(async () => {
      if (!hasLoggedIn && silentLoginWindow) {
        await checkLoginStatus();
      } else if (loginCheckInterval) {
        clearInterval(loginCheckInterval);
        loginCheckInterval = null;
      }
    }, 1000);

    // Timeout - if silent login doesn't work, fall back to visible login
    setTimeout(() => {
      if (!hasLoggedIn) {
        console.log('[Main] Silent login timeout, falling back to visible login...');
        if (loginCheckInterval) {
          clearInterval(loginCheckInterval);
          loginCheckInterval = null;
        }
        if (silentLoginWindow) {
          silentLoginWindow.close();
        }

        // Notify renderer that silent login failed
        if (mainWindow) {
          mainWindow.webContents.send('silent-login-failed');
        }

        // Open visible login window
        createLoginWindow();
        resolve(false);
      }
    }, SILENT_LOGIN_TIMEOUT);

    silentLoginWindow.on('closed', () => {
      if (loginCheckInterval) {
        clearInterval(loginCheckInterval);
        loginCheckInterval = null;
      }
      silentLoginWindow = null;
    });
  });
}

function createTray() {
  try {
    tray = new Tray(path.join(__dirname, 'assets/tray-icon.png'));

    const contextMenu = Menu.buildFromTemplate([
      {
        label: 'Show Widget',
        click: () => {
          if (mainWindow) {
            mainWindow.show();
          } else {
            createMainWindow();
          }
        }
      },
      {
        label: 'Refresh',
        click: () => {
          if (mainWindow) {
            mainWindow.webContents.send('refresh-usage');
          }
        }
      },
      { type: 'separator' },
      {
        label: 'Settings',
        click: () => {
          // TODO: Open settings window
        }
      },
      {
        label: 'Re-login',
        click: () => {
          store.delete('sessionKey');
          store.delete('organizationId');
          createLoginWindow();
        }
      },
      { type: 'separator' },
      {
        label: 'Exit',
        click: () => {
          app.quit();
        }
      }
    ]);

    tray.setToolTip('Claude Usage Widget');
    tray.setContextMenu(contextMenu);

    tray.on('click', () => {
      if (mainWindow) {
        mainWindow.isVisible() ? mainWindow.hide() : mainWindow.show();
      }
    });
  } catch (error) {
    console.error('Failed to create tray:', error);
  }
}

// IPC Handlers
ipcMain.handle('get-credentials', () => {
  return {
    sessionKey: store.get('sessionKey'),
    organizationId: store.get('organizationId')
  };
});

ipcMain.handle('save-credentials', (event, { sessionKey, organizationId }) => {
  store.set('sessionKey', sessionKey);
  if (organizationId) {
    store.set('organizationId', organizationId);
  }
  return true;
});

ipcMain.handle('delete-credentials', async () => {
  store.delete('sessionKey');
  store.delete('organizationId');

  // Clear all Claude.ai cookies to ensure actual logout
  try {
    // Get all cookies for claude.ai and remove them
    const cookies = await session.defaultSession.cookies.get({ url: 'https://claude.ai' });
    for (const cookie of cookies) {
      await session.defaultSession.cookies.remove('https://claude.ai', cookie.name);
      console.log('[Logout] Removed cookie:', cookie.name);
    }

    // Also clear local storage and session storage for claude.ai
    await session.defaultSession.clearStorageData({
      origin: 'https://claude.ai',
      storages: ['cookies', 'localstorage', 'sessionstorage']
    });
    console.log('[Logout] Cleared all storage data for claude.ai');
  } catch (error) {
    console.error('Failed to clear cookies:', error);
  }

  return true;
});

ipcMain.on('open-login', () => {
  createLoginWindow();
});

ipcMain.on('minimize-window', () => {
  if (mainWindow) mainWindow.hide();
});

ipcMain.on('close-window', () => {
  app.quit();
});

ipcMain.handle('get-window-position', () => {
  if (mainWindow) {
    return mainWindow.getBounds();
  }
  return null;
});

ipcMain.handle('set-window-position', (event, { x, y }) => {
  if (mainWindow) {
    mainWindow.setPosition(x, y);
    return true;
  }
  return false;
});

ipcMain.on('open-external', (event, url) => {
  shell.openExternal(url);
});

ipcMain.handle('fetch-usage-data', async () => {
  console.log('[Main] fetch-usage-data handler called');
  const sessionKey = store.get('sessionKey');
  const organizationId = store.get('organizationId');

  console.log('[Main] Credentials:', {
    hasSessionKey: !!sessionKey,
    organizationId
  });

  if (!sessionKey || !organizationId) {
    throw new Error('Missing credentials');
  }

  try {
    console.log('[Main] Making API request to:', `https://claude.ai/api/organizations/${organizationId}/usage`);
    const response = await axios.get(
      `https://claude.ai/api/organizations/${organizationId}/usage`,
      {
        headers: {
          'Cookie': `sessionKey=${sessionKey}`,
          'User-Agent': CHROME_USER_AGENT
        }
      }
    );
    console.log('[Main] API request successful, status:', response.status);
    return response.data;
  } catch (error) {
    console.error('[Main] API request failed:', error.message);
    if (error.response) {
      console.error('[Main] Response status:', error.response.status);
      if (error.response.status === 401 || error.response.status === 403) {
        // Session expired - attempt silent re-login
        console.log('[Main] Session expired, attempting silent re-login...');
        store.delete('sessionKey');
        store.delete('organizationId');

        // Don't clear cookies - we need them for silent login to work with OAuth
        // The silent login will use existing Google/OAuth session if available

        // Attempt silent login (will notify renderer appropriately)
        attemptSilentLogin();

        throw new Error('SessionExpired');
      }
    }
    throw error;
  }
});

// App lifecycle
app.whenReady().then(() => {
  createMainWindow();
  createTray();

  // Check if we have credentials
  // const hasCredentials = store.get('sessionKey') && store.get('organizationId');
  // if (!hasCredentials) {
  //   setTimeout(() => {
  //     createLoginWindow();
  //   }, 1000);
  // }
});

app.on('window-all-closed', () => {
  // Don't quit on macOS
  if (process.platform !== 'darwin') {
    // Keep running in tray
  }
});

app.on('activate', () => {
  if (mainWindow === null) {
    createMainWindow();
  }
});

// Prevent multiple instances
const gotTheLock = app.requestSingleInstanceLock();
if (!gotTheLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.focus();
    }
  });
}
