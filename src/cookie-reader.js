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
 * Windows only: copies cookie DB to temp, decrypts via DPAPI + AES-256-GCM.
 */
function detectSessionKey() {
  if (process.platform !== 'win32') {
    throw new Error('Auto-detect is currently supported on Windows only');
  }

  const errors = [];
  let hadLockError = false;

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
        const msg = err.message || '';
        if (msg.includes('locked') || msg.includes('SQLITE_BUSY') || msg.includes('EBUSY')) {
          hadLockError = true;
        }
        errors.push(`${browser.name}/${profile}: ${msg}`);
      }
    }
  }

  const detail = errors.length > 0 ? '\n' + errors.join('\n') : '';

  if (hadLockError) {
    throw new Error(
      'Cookie DB locked. Fully quit browser (check system tray too).' + detail
    );
  }

  throw new Error(
    'No sessionKey found. Log into claude.ai in Chrome/Edge first.' + detail
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

  if (encryptedKeyFull.slice(0, 5).toString('utf8') !== 'DPAPI') {
    throw new Error('Unexpected key prefix (expected DPAPI)');
  }
  const dpapiBlob = encryptedKeyFull.slice(5);

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
 * Copy a file, falling back to PowerShell FileStream if Node.js fails (EBUSY).
 * PowerShell can open with FileShare.ReadWrite|Delete to bypass browser locks.
 */
function copyFileWithFallback(src, dest) {
  try {
    fs.copyFileSync(src, dest);
  } catch (err) {
    if (err.code !== 'EBUSY' && err.code !== 'EPERM') throw err;

    // Fallback: PowerShell FileStream with shared read access
    const psSrc = src.replace(/'/g, "''");
    const psDest = dest.replace(/'/g, "''");
    const psScript = [
      '$share = [System.IO.FileShare]::ReadWrite -bor [System.IO.FileShare]::Delete;',
      `$src = [System.IO.File]::Open('${psSrc}', 'Open', 'Read', $share);`,
      `$dest = [System.IO.File]::Create('${psDest}');`,
      '$src.CopyTo($dest);',
      '$dest.Close();',
      '$src.Close();'
    ].join(' ');

    const tempPs1 = path.join(os.tmpdir(), `claude-copy-${Date.now()}.ps1`);
    fs.writeFileSync(tempPs1, psScript);
    try {
      execSync(
        `powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "${tempPs1}"`,
        { timeout: 10000 }
      );
    } finally {
      try { fs.unlinkSync(tempPs1); } catch (e) { /* ignore */ }
    }
  }
}

/**
 * Read the sessionKey cookie from a Chromium Cookies SQLite database.
 * Copies DB files to temp dir first to avoid lock conflicts with running browser.
 */
function readSessionKeyCookie(cookiePath, masterKey) {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'claude-cookie-'));
  const tempDbPath = path.join(tempDir, 'Cookies');

  try {
    // Copy main DB file (try Node.js first, PowerShell fallback for locked files)
    copyFileWithFallback(cookiePath, tempDbPath);

    // Verify copy is not empty
    const stat = fs.statSync(tempDbPath);
    if (stat.size === 0) {
      throw new Error('Cookie database copy is empty (0 bytes)');
    }

    // Copy WAL and SHM files if they exist (needed for uncommitted data)
    const walPath = cookiePath + '-wal';
    const shmPath = cookiePath + '-shm';
    if (fs.existsSync(walPath)) {
      copyFileWithFallback(walPath, tempDbPath + '-wal');
    }
    if (fs.existsSync(shmPath)) {
      copyFileWithFallback(shmPath, tempDbPath + '-shm');
    }

    // Load better-sqlite3 - may fail if native module not unpacked from asar
    let Database;
    try {
      Database = require('better-sqlite3');
    } catch (loadErr) {
      throw new Error('Failed to load SQLite module: ' + loadErr.message);
    }

    const db = new Database(tempDbPath);

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
  } finally {
    // Clean up temp files
    try {
      fs.rmSync(tempDir, { recursive: true, force: true });
    } catch (e) {
      // Ignore cleanup errors
    }
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
