const path = require('path');
const fs = require('fs');
const os = require('os');
const crypto = require('crypto');
const { execSync } = require('child_process');

// Chromium-based browsers on Windows
const BROWSERS = [
  {
    name: 'Chrome',
    userDataPath: path.join(process.env.LOCALAPPDATA || '', 'Google', 'Chrome', 'User Data')
  },
  {
    name: 'Edge',
    userDataPath: path.join(process.env.LOCALAPPDATA || '', 'Microsoft', 'Edge', 'User Data')
  },
  {
    name: 'Brave',
    userDataPath: path.join(process.env.LOCALAPPDATA || '', 'BraveSoftware', 'Brave-Browser', 'User Data')
  }
];

const PROFILES = ['Default', 'Profile 1', 'Profile 2', 'Profile 3', 'Profile 4', 'Profile 5'];

/**
 * Auto-detect sessionKey from installed Chromium browsers.
 * Windows only: reads Chrome/Edge cookie DB and decrypts via DPAPI.
 */
async function detectSessionKey() {
  if (process.platform !== 'win32') {
    throw new Error('Auto-detect is currently supported on Windows only');
  }

  const errors = [];

  for (const browser of BROWSERS) {
    if (!fs.existsSync(browser.userDataPath)) continue;

    let masterKey;
    try {
      masterKey = getMasterKey(browser.userDataPath);
    } catch (err) {
      errors.push(`${browser.name}: failed to get encryption key - ${err.message}`);
      continue;
    }

    for (const profile of PROFILES) {
      const cookiePath = path.join(browser.userDataPath, profile, 'Network', 'Cookies');
      if (!fs.existsSync(cookiePath)) continue;

      try {
        const sessionKey = readSessionKeyCookie(cookiePath, masterKey);
        if (sessionKey) {
          console.log(`[CookieReader] Found sessionKey in ${browser.name}/${profile}`);
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

  // Use EncodedCommand to avoid quoting issues
  const encoded = Buffer.from(psScript, 'utf16le').toString('base64');
  const result = execSync(
    `powershell -NoProfile -NonInteractive -EncodedCommand ${encoded}`,
    { encoding: 'utf8', timeout: 15000 }
  ).trim();

  return Buffer.from(result, 'base64');
}

/**
 * Copy a locked file using PowerShell FileStream with shared read access.
 * Chrome holds exclusive locks on its cookie DB; this bypasses the lock.
 */
function copyLockedFile(srcPath, destPath) {
  const tmpPs1 = path.join(os.tmpdir(), `claude_copy_${Date.now()}.ps1`);
  const script = [
    `$share = [System.IO.FileShare]::ReadWrite -bor [System.IO.FileShare]::Delete`,
    `$src = [System.IO.File]::Open('${srcPath.replace(/'/g, "''")}', [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, $share)`,
    `$dst = [System.IO.File]::Create('${destPath.replace(/'/g, "''")}')`,
    `$src.CopyTo($dst)`,
    `$dst.Close()`,
    `$src.Close()`
  ].join('\n');

  fs.writeFileSync(tmpPs1, script, 'utf8');
  try {
    execSync(
      `powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "${tmpPs1}"`,
      { timeout: 15000, stdio: ['pipe', 'pipe', 'pipe'] }
    );
  } finally {
    try { fs.unlinkSync(tmpPs1); } catch {}
  }
}

/**
 * Query sessionKey from a better-sqlite3 Database instance.
 */
function queryCookie(db, masterKey) {
  const tables = db.prepare("SELECT name FROM sqlite_master WHERE type='table'").all();
  const tableNames = tables.map(t => t.name);
  console.log(`[CookieReader] Tables: ${tableNames.join(', ') || '(empty)'}`);

  if (!tableNames.includes('cookies')) {
    throw new Error(`cookies table not found (tables: ${tableNames.join(', ') || 'none'})`);
  }

  const row = db.prepare(
    "SELECT encrypted_value FROM cookies WHERE (host_key = '.claude.ai' OR host_key = 'claude.ai') AND name = 'sessionKey' LIMIT 1"
  ).get();

  if (row && row.encrypted_value) {
    return decryptCookieValue(Buffer.from(row.encrypted_value), masterKey);
  }
  return null;
}

/**
 * Read the sessionKey cookie from a Chromium Cookies SQLite database.
 * Strategy 1: Open directly via SQLite VFS (handles shared access natively).
 * Strategy 2: Copy via PowerShell FileStream + open copy.
 */
function readSessionKeyCookie(cookiePath, masterKey) {
  const Database = require('better-sqlite3');

  // Strategy 1: Direct open (SQLite's VFS uses FILE_SHARE_READ|WRITE|DELETE)
  // Works when browser is closed, and sometimes even when running.
  try {
    const db = new Database(cookiePath, { readonly: true });
    try {
      return queryCookie(db, masterKey);
    } finally {
      db.close();
    }
  } catch (directErr) {
    console.log(`[CookieReader] Direct open failed: ${directErr.message}`);
  }

  // Strategy 2: Copy files via PowerShell, then open the copy
  const tmpDir = path.join(os.tmpdir(), `claude_cookies_${Date.now()}`);
  fs.mkdirSync(tmpDir, { recursive: true });
  const tmpDb = path.join(tmpDir, 'Cookies');

  try {
    copyLockedFile(cookiePath, tmpDb);

    // Verify the copy is not empty
    const stats = fs.statSync(tmpDb);
    console.log(`[CookieReader] Copied DB size: ${stats.size} bytes`);
    if (stats.size < 100) {
      throw new Error(`Copied DB too small (${stats.size} bytes), likely empty`);
    }

    // Also copy WAL and SHM for complete data
    const walSrc = cookiePath + '-wal';
    const shmSrc = cookiePath + '-shm';
    if (fs.existsSync(walSrc)) {
      try { copyLockedFile(walSrc, tmpDb + '-wal'); } catch (e) {
        console.log(`[CookieReader] WAL copy failed: ${e.message}`);
      }
    }
    if (fs.existsSync(shmSrc)) {
      try { copyLockedFile(shmSrc, tmpDb + '-shm'); } catch (e) {
        console.log(`[CookieReader] SHM copy failed: ${e.message}`);
      }
    }

    const db = new Database(tmpDb);
    try {
      return queryCookie(db, masterKey);
    } finally {
      db.close();
    }
  } finally {
    try { fs.rmSync(tmpDir, { recursive: true, force: true }); } catch {}
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
