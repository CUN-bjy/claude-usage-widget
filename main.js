const { app, BrowserWindow, ipcMain, Tray, Menu, session, shell } = require('electron');
const path = require('path');
const Store = require('electron-store');
const { detectSessionKey } = require('./src/cookie-reader');

const store = new Store({
  encryptionKey: 'claude-widget-secure-key-2024'
});

const CHROME_USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';

let mainWindow = null;
let tray = null;

const WIDGET_WIDTH = 480;
const WIDGET_HEIGHT = 140;

// Set session-level User-Agent to avoid Electron detection
app.on('ready', () => {
  session.defaultSession.setUserAgent(CHROME_USER_AGENT);
});

// Fetch a JSON API endpoint using a hidden BrowserWindow (bypasses Cloudflare)
function fetchViaWindow(url) {
  return new Promise((resolve, reject) => {
    const win = new BrowserWindow({
      width: 800,
      height: 600,
      show: false,
      webPreferences: {
        nodeIntegration: false,
        contextIsolation: true
      }
    });

    const timeout = setTimeout(() => {
      win.close();
      reject(new Error('Request timeout'));
    }, 30000);

    win.webContents.on('did-finish-load', async () => {
      try {
        // Extract the text content of the page (API returns JSON as text)
        const bodyText = await win.webContents.executeJavaScript(
          'document.body.innerText || document.body.textContent'
        );
        clearTimeout(timeout);
        win.close();

        try {
          const data = JSON.parse(bodyText);
          resolve(data);
        } catch (parseErr) {
          reject(new Error('Invalid JSON: ' + bodyText.substring(0, 200)));
        }
      } catch (err) {
        clearTimeout(timeout);
        win.close();
        reject(err);
      }
    });

    win.webContents.on('did-fail-load', (event, errorCode, errorDescription) => {
      clearTimeout(timeout);
      win.close();
      reject(new Error(`Load failed: ${errorCode} ${errorDescription}`));
    });

    win.loadURL(url);
  });
}

// Set sessionKey as a cookie in Electron's session
async function setSessionCookie(sessionKey) {
  await session.defaultSession.cookies.set({
    url: 'https://claude.ai',
    name: 'sessionKey',
    value: sessionKey,
    domain: '.claude.ai',
    path: '/',
    secure: true,
    httpOnly: true
  });
  console.log('[Main] sessionKey cookie set in Electron session');
}

function createMainWindow() {
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

  if (savedPosition) {
    windowOptions.x = savedPosition.x;
    windowOptions.y = savedPosition.y;
  }

  mainWindow = new BrowserWindow(windowOptions);
  mainWindow.loadFile('src/renderer/index.html');

  mainWindow.setAlwaysOnTop(true, 'floating');
  mainWindow.setVisibleOnAllWorkspaces(true);

  mainWindow.on('move', () => {
    const position = mainWindow.getBounds();
    store.set('windowPosition', { x: position.x, y: position.y });
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });

  if (process.env.NODE_ENV === 'development') {
    mainWindow.webContents.openDevTools({ mode: 'detach' });
  }
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
        label: 'Log Out',
        click: async () => {
          store.delete('sessionKey');
          store.delete('organizationId');
          // Clear session cookies
          const cookies = await session.defaultSession.cookies.get({ url: 'https://claude.ai' });
          for (const cookie of cookies) {
            await session.defaultSession.cookies.remove('https://claude.ai', cookie.name);
          }
          if (mainWindow) {
            mainWindow.webContents.send('session-expired');
          }
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

ipcMain.handle('save-credentials', async (event, { sessionKey, organizationId }) => {
  store.set('sessionKey', sessionKey);
  if (organizationId) {
    store.set('organizationId', organizationId);
  }
  // Also set cookie in Electron session for window-based fetching
  await setSessionCookie(sessionKey);
  return true;
});

ipcMain.handle('delete-credentials', async () => {
  store.delete('sessionKey');
  store.delete('organizationId');
  const cookies = await session.defaultSession.cookies.get({ url: 'https://claude.ai' });
  for (const cookie of cookies) {
    await session.defaultSession.cookies.remove('https://claude.ai', cookie.name);
  }
  return true;
});

// Validate a sessionKey by fetching org ID via hidden BrowserWindow
ipcMain.handle('validate-session-key', async (event, sessionKey) => {
  console.log('[Main] Validating session key:', sessionKey.substring(0, 20) + '...');
  try {
    // Set the cookie in Electron's session first
    await setSessionCookie(sessionKey);

    // Fetch organizations using hidden BrowserWindow (bypasses Cloudflare)
    const data = await fetchViaWindow('https://claude.ai/api/organizations');

    if (data && Array.isArray(data) && data.length > 0) {
      const orgId = data[0].uuid || data[0].id;
      console.log('[Main] Session key validated, org ID:', orgId);
      return { success: true, organizationId: orgId };
    }

    // Check if it's an error response
    if (data && data.error) {
      return { success: false, error: data.error.message || data.error };
    }

    return { success: false, error: 'No organization found. Response: ' + JSON.stringify(data).substring(0, 100) };
  } catch (error) {
    console.error('[Main] Session key validation failed:', error.message);
    // Clean up the invalid cookie
    await session.defaultSession.cookies.remove('https://claude.ai', 'sessionKey');
    return { success: false, error: error.message };
  }
});

ipcMain.on('minimize-window', () => {
  if (mainWindow) mainWindow.hide();
});

ipcMain.on('close-window', () => {
  app.quit();
});

ipcMain.on('resize-window', (event, height) => {
  if (mainWindow) {
    mainWindow.setContentSize(WIDGET_WIDTH, height);
  }
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

ipcMain.handle('detect-session-key', async () => {
  try {
    const result = await detectSessionKey();
    return result;
  } catch (error) {
    console.error('[Main] Cookie auto-detect failed:', error.message);
    return { success: false, error: error.message };
  }
});

ipcMain.handle('fetch-usage-data', async () => {
  const sessionKey = store.get('sessionKey');
  const organizationId = store.get('organizationId');

  if (!sessionKey || !organizationId) {
    throw new Error('Missing credentials');
  }

  // Ensure cookie is set
  await setSessionCookie(sessionKey);

  try {
    const data = await fetchViaWindow(
      `https://claude.ai/api/organizations/${organizationId}/usage`
    );
    return data;
  } catch (error) {
    console.error('[Main] API request failed:', error.message);
    // Check if the error indicates auth failure
    if (error.message.includes('Invalid JSON') && error.message.includes('Just a moment')) {
      // Cloudflare blocked - session may be expired
      store.delete('sessionKey');
      store.delete('organizationId');
      if (mainWindow) {
        mainWindow.webContents.send('session-expired');
      }
      throw new Error('SessionExpired');
    }
    throw error;
  }
});

// App lifecycle
app.whenReady().then(async () => {
  // Restore session cookie if we have stored credentials
  const sessionKey = store.get('sessionKey');
  if (sessionKey) {
    await setSessionCookie(sessionKey);
  }

  createMainWindow();
  createTray();
});

app.on('window-all-closed', () => {
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
