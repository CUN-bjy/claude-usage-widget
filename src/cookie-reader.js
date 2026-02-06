const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const { execSync } = require('child_process');

// Chromium-based browsers on Windows
const BROWSERS = [
  {
    name: 'Chrome',
    processName: 'chrome.exe',
    userDataPath: path.join(process.env.LOCALAPPDATA || '', 'Google', 'Chrome', 'User Data')
  },
  {
    name: 'Edge',
    processName: 'msedge.exe',
    userDataPath: path.join(process.env.LOCALAPPDATA || '', 'Microsoft', 'Edge', 'User Data')
  },
  {
    name: 'Brave',
    processName: 'brave.exe',
    userDataPath: path.join(process.env.LOCALAPPDATA || '', 'BraveSoftware', 'Brave-Browser', 'User Data')
  }
];

const PROFILES = ['Default', 'Profile 1', 'Profile 2', 'Profile 3', 'Profile 4', 'Profile 5'];

/**
 * Check if a Windows process is running.
 */
function isProcessRunning(processName) {
  try {
    const result = execSync(
      `tasklist /FI "IMAGENAME eq ${processName}" /NH`,
      { encoding: 'utf8', timeout: 5000, stdio: ['pipe', 'pipe', 'pipe'] }
    );
    return result.toLowerCase().includes(processName.toLowerCase());
  } catch {
    return false;
  }
}

/**
 * Auto-detect sessionKey from installed Chromium browsers.
 * Windows only: reads Chrome/Edge cookie DB and decrypts via DPAPI.
 * Browser must be closed so the cookie DB is not locked.
 */
function detectSessionKey() {
  if (process.platform !== 'win32') {
    throw new Error('Auto-detect is currently supported on Windows only');
  }

  // Check which browsers are running
  const runningBrowsers = BROWSERS
    .filter(b => fs.existsSync(b.userDataPath) && isProcessRunning(b.processName))
    .map(b => b.name);

  if (runningBrowsers.length > 0) {
    throw new Error(
      `Close ${runningBrowsers.join(' and ')} first, then try again.\n` +
      'The browser locks its cookie database while running.'
    );
  }

  const errors = [];

  for (const browser of BROWSERS) {
    if (!fs.existsSync(browser.userDataPath)) continue;

    let masterKey;
    try {
      masterKey = getMasterKey(browser.userDataPath);
    } catch (err) {
      errors.push(`${browser.name}: ${err.message}`);
      continue;
    }

    for (const profile of PROFILES) {
      const cookiePath = path.join(browser.userDataPath, profile, 'Network', 'Cookies');
      if (!fs.existsSync(cookiePath)) continue;

      try {
        const sessionKey = readSessionKeyCookie(cookiePath, masterKey);
        if (sessionKey) {
          return {
            success: true,
            sessionKey,
            browser: browser.name,
            profile
          };
        }
      } catch (err) {
        errors.push(`${browser.name}/${profile}: ${err.message}`);
      }
    }
  }

  const detail = errors.length > 0 ? '\n' + errors.join('\n') : '';
  throw new Error(
    'No sessionKey found. Make sure you are logged into claude.ai in Chrome or Edge.' + detail
  );
}

/**
 * Extract and decrypt the Chromium master encryption key using DPAPI.
 */
function getMasterKey(userDataPath) {
  const localStatePath = path.join(userDataPath, 'Local State');
  const localState = JSON.parse(fs.readFileSync(localStatePath, 'utf8'));

  const encryptedKeyB64 = localState.os_crypt && localState.os_crypt.encrypted_key;
  if (!encryptedKeyB64) {
    throw new Error('No os_crypt.encrypted_key in Local State');
  }

  const encryptedKeyFull = Buffer.from(encryptedKeyB64, 'base64');

  // Strip "DPAPI" prefix (5 bytes)
  if (encryptedKeyFull.slice(0, 5).toString('utf8') !== 'DPAPI') {
    throw new Error('Unexpected key prefix (expected DPAPI)');
  }
  const dpapiBlob = encryptedKeyFull.slice(5);

  // Decrypt via PowerShell DPAPI
  const inputB64 = dpapiBlob.toString('base64');
  const psScript = [
    'Add-Type -AssemblyName System.Security;',
    `$bytes = [Convert]::FromBase64String('${inputB64}');`,
    '$dec = [System.Security.Cryptography.ProtectedData]::Unprotect($bytes, $null, [System.Security.Cryptography.DataProtectionScope]::CurrentUser);',
    '[Convert]::ToBase64String($dec)'
  ].join(' ');

  const encoded = Buffer.from(psScript, 'utf16le').toString('base64');
  const result = execSync(
    `powershell -NoProfile -NonInteractive -EncodedCommand ${encoded}`,
    { encoding: 'utf8', timeout: 15000 }
  ).trim();

  return Buffer.from(result, 'base64');
}

/**
 * Read the sessionKey cookie from a Chromium Cookies SQLite database.
 * Browser must be closed so the DB file is not locked and WAL is checkpointed.
 */
function readSessionKeyCookie(cookiePath, masterKey) {
  const Database = require('better-sqlite3');
  const db = new Database(cookiePath, { readonly: true });

  try {
    const row = db.prepare(
      "SELECT encrypted_value FROM cookies WHERE (host_key = '.claude.ai' OR host_key = 'claude.ai') AND name = 'sessionKey' LIMIT 1"
    ).get();

    if (row && row.encrypted_value) {
      return decryptCookieValue(Buffer.from(row.encrypted_value), masterKey);
    }
    return null;
  } finally {
    db.close();
  }
}

/**
 * Decrypt a Chromium cookie value (AES-256-GCM, v10/v20 prefix).
 */
function decryptCookieValue(encryptedValue, masterKey) {
  const prefix = encryptedValue.slice(0, 3).toString('utf8');

  if (prefix === 'v10' || prefix === 'v20') {
    const nonce = encryptedValue.slice(3, 3 + 12);
    const ciphertextWithTag = encryptedValue.slice(3 + 12);

    // Last 16 bytes are the GCM auth tag
    const tag = ciphertextWithTag.slice(ciphertextWithTag.length - 16);
    const ciphertext = ciphertextWithTag.slice(0, ciphertextWithTag.length - 16);

    const decipher = crypto.createDecipheriv('aes-256-gcm', masterKey, nonce);
    decipher.setAuthTag(tag);

    let decrypted = decipher.update(ciphertext);
    decrypted = Buffer.concat([decrypted, decipher.final()]);

    return decrypted.toString('utf8');
  }

  throw new Error('Unsupported cookie encryption version: ' + prefix);
}

module.exports = { detectSessionKey };
