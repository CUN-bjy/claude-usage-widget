const { app, BrowserWindow, ipcMain, Tray, Menu, shell } = require('electron');
const path = require('path');
const Store = require('electron-store');
const axios = require('axios');

const store = new Store({
  encryptionKey: 'claude-widget-secure-key-2024'
});

const CHROME_USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';

let mainWindow = null;
let tray = null;

// Window configuration
const WIDGET_WIDTH = 480;
const WIDGET_HEIGHT = 140;

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
        click: () => {
          store.delete('sessionKey');
          store.delete('organizationId');
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
  return true;
});

// Validate a sessionKey by fetching org ID from Claude API
ipcMain.handle('validate-session-key', async (event, sessionKey) => {
  console.log('[Main] Validating session key:', sessionKey.substring(0, 20) + '...');
  try {
    const response = await axios.get('https://claude.ai/api/organizations', {
      headers: {
        'Cookie': `sessionKey=${sessionKey}`,
        'User-Agent': CHROME_USER_AGENT,
        'Accept': 'application/json',
        'Accept-Language': 'en-US,en;q=0.9',
        'Referer': 'https://claude.ai/',
        'Origin': 'https://claude.ai'
      }
    });

    console.log('[Main] Validate response status:', response.status);
    console.log('[Main] Validate response data:', JSON.stringify(response.data).substring(0, 200));

    if (response.data && Array.isArray(response.data) && response.data.length > 0) {
      const orgId = response.data[0].uuid || response.data[0].id;
      console.log('[Main] Session key validated, org ID:', orgId);
      return { success: true, organizationId: orgId };
    }

    return { success: false, error: 'No organization found' };
  } catch (error) {
    console.error('[Main] Session key validation failed:', error.message);
    if (error.response) {
      console.error('[Main] Response status:', error.response.status);
      console.error('[Main] Response headers:', JSON.stringify(error.response.headers));
      console.error('[Main] Response data:', JSON.stringify(error.response.data).substring(0, 500));
    }
    if (error.response && (error.response.status === 401 || error.response.status === 403)) {
      return { success: false, error: 'Invalid or expired session key (HTTP ' + error.response.status + ')' };
    }
    return { success: false, error: 'Connection failed: ' + error.message };
  }
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
  const sessionKey = store.get('sessionKey');
  const organizationId = store.get('organizationId');

  if (!sessionKey || !organizationId) {
    throw new Error('Missing credentials');
  }

  try {
    const response = await axios.get(
      `https://claude.ai/api/organizations/${organizationId}/usage`,
      {
        headers: {
          'Cookie': `sessionKey=${sessionKey}`,
          'User-Agent': CHROME_USER_AGENT,
          'Accept': 'application/json',
          'Accept-Language': 'en-US,en;q=0.9',
          'Referer': 'https://claude.ai/',
          'Origin': 'https://claude.ai'
        }
      }
    );
    return response.data;
  } catch (error) {
    console.error('[Main] API request failed:', error.message);
    if (error.response && (error.response.status === 401 || error.response.status === 403)) {
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
app.whenReady().then(() => {
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
