#!/usr/bin/env node

/**
 * Monitor Dev Server & Inspector Launcher
 * 
 * Usage:
 *   npm run dev
 *   node scripts/dev.mjs
 *   node scripts/dev.mjs --no-open
 *   node scripts/dev.mjs --check-only
 */

import { spawn } from 'node:child_process';
import os from 'node:os';
import http from 'node:http';
import fs from 'node:fs';

const PORT = 9480;
const args = process.argv.slice(2);
const NO_OPEN = args.includes('--no-open');
const CHECK_ONLY = args.includes('--check-only');

// ANSI Colors
const RESET = '\x1b[0m';
const BOLD = '\x1b[1m';
const DIM = '\x1b[2m';
const GREEN = '\x1b[32m';
const CYAN = '\x1b[36m';
const YELLOW = '\x1b[33m';
const BLUE = '\x1b[34m';
const MAGENTA = '\x1b[35m';

function getLocalIpAddresses() {
  const interfaces = os.networkInterfaces();
  const ips = [];
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name]) {
      // IPv4 and non-internal
      if (iface.family === 'IPv4' && !iface.internal) {
        ips.push({ name, ip: iface.address });
      }
    }
  }
  return ips;
}

function checkPortOpen(port) {
  return new Promise((resolve) => {
    const req = http.get(`http://127.0.0.1:${port}/api/v1/dev/diagnostics`, { timeout: 1500 }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          const json = JSON.parse(data);
          resolve({ open: true, diagnostics: json });
        } catch {
          resolve({ open: true, diagnostics: null });
        }
      });
    });
    req.on('error', () => resolve({ open: false, diagnostics: null }));
    req.on('timeout', () => {
      req.destroy();
      resolve({ open: false, diagnostics: null });
    });
  });
}

function openBrowser(url) {
  const platform = os.platform();
  if (platform === 'win32') {
    spawn('cmd', ['/c', 'start', '', url], { detached: true, stdio: 'ignore' });
  } else if (platform === 'darwin') {
    spawn('open', [url], { detached: true, stdio: 'ignore' });
  } else {
    spawn('xdg-open', [url], { detached: true, stdio: 'ignore' });
  }
}

async function main() {
  console.log(`\n${BOLD}${CYAN}╔═══════════════════════════════════════════════════════════════════╗${RESET}`);
  console.log(`${BOLD}${CYAN}║             MONITOR // DEV SERVER & NETWORK INSPECTOR             ║${RESET}`);
  console.log(`${BOLD}${CYAN}╚═══════════════════════════════════════════════════════════════════╝${RESET}\n`);

  const localIps = getLocalIpAddresses();
  const primaryIp = localIps.length > 0 ? localIps[0].ip : '127.0.0.1';

  console.log(`${DIM}Checking Monitor Desktop backend on port ${PORT}…${RESET}`);
  let check = await checkPortOpen(PORT);

  let backendProcess = null;

  if (!check.open) {
    if (CHECK_ONLY) {
      console.log(`${YELLOW}⚠️ Monitor Desktop service is not currently running on port ${PORT}.${RESET}\n`);
      return;
    }

    console.log(`${YELLOW}⚡ Starting Monitor Desktop engine (cargo run -p monitor-tray)…${RESET}`);
    backendProcess = spawn('cargo', ['run', '--manifest-path', 'desktop/Cargo.toml', '-p', 'monitor-tray', '--bin', 'monitor'], {
      stdio: 'inherit',
      shell: true,
    });

    backendProcess.on('error', (err) => {
      console.error(`${YELLOW}Failed to spawn desktop engine:${RESET}`, err.message);
    });

    // Poll until ready
    let attempts = 0;
    while (attempts < 30) {
      await new Promise(r => setTimeout(r, 1000));
      check = await checkPortOpen(PORT);
      if (check.open) break;
      attempts++;
      process.stdout.write('.');
    }
    console.log('\n');
  }

  const diag = check.diagnostics;
  const hostname = diag ? diag.hostname : os.hostname();
  const noiseKey = diag && diag.noise_public_key ? diag.noise_public_key.substring(0, 24) + '…' : 'Available on connection';
  const pairingCode = diag && diag.pairing_code ? diag.pairing_code.code : 'None (click in UI to generate)';

  console.log(`${BOLD}${GREEN}✔ Monitor Dev Engine Active${RESET} on ${BOLD}http://127.0.0.1:${PORT}${RESET}\n`);
  
  console.log(`${BOLD}🔗 WEB INSPECTOR ACCESS URLS:${RESET}`);
  console.log(`   ${CYAN}🖥️  Desktop Inspector:${RESET}  ${BOLD}http://localhost:${PORT}/dev${RESET}`);
  console.log(`   ${MAGENTA}📱  Mobile / Android:${RESET}   ${BOLD}http://${primaryIp}:${PORT}/dev${RESET}`);
  if (localIps.length > 1) {
    for (let i = 1; i < localIps.length; i++) {
      console.log(`       ${DIM}Alternative IP:${RESET}      http://${localIps[i].ip}:${PORT}/dev (${localIps[i].name})`);
    }
  }

  console.log(`\n${BOLD}📡 ACTIVE 4-ROUTE NETWORK MATRIX:${RESET}`);
  console.log(`   ${GREEN}1. Direct LAN V2:${RESET}       ws://${primaryIp}:${PORT}/api/v2/stream ${DIM}(Noise IK: MONITOR_V3_2_LAN_V2, <5ms latency)${RESET}`);
  console.log(`   ${GREEN}2. WebRTC P2P:${RESET}          DataChannel: 'monitor-v2' ${DIM}(mDNS Unmasked, Direct UDP host candidates)${RESET}`);
  console.log(`   ${YELLOW}3. WebRTC TURN Relay:${RESET}   Disabled by default (strict_free) ${DIM}(Optional self-hosted TURN fallback)${RESET}`);
  console.log(`   ${BLUE}4. Cloud Relay WSS:${RESET}     /api/v2/rendezvous/{host,viewer} ${DIM}(Out-of-home remote access via HostRoom)${RESET}`);

  console.log(`\n${BOLD}🔑 SECURITY & PAIRING INFO:${RESET}`);
  console.log(`   ${DIM}Host Noise Key:${RESET}         ${noiseKey}`);
  console.log(`   ${DIM}Active Pairing Code:${RESET}    ${pairingCode}`);

  console.log(`\n${DIM}───────────────────────────────────────────────────────────────────${RESET}`);
  console.log(`  ${BOLD}Press [Ctrl+C] to stop the dev server.${RESET}`);
  console.log(`  • Trigger test alerts anytime from the Web Inspector at ${CYAN}http://localhost:${PORT}/dev${RESET}`);
  console.log(`  • View real-time Android logs and inspect dropped/handled events.\n`);

  if (!NO_OPEN && !CHECK_ONLY) {
    console.log(`${CYAN}🌐 Launching Dev Inspector in your browser…${RESET}\n`);
    openBrowser(`http://localhost:${PORT}/dev`);
  }

  // Connect to live WebSocket stream to print real-time events in the terminal
  if (typeof WebSocket !== 'undefined') {
    connectCliWebSocket(`ws://127.0.0.1:${PORT}/api/v1/dev/stream`);
  }

  // Handle clean exit
  const cleanup = () => {
    console.log(`\n${DIM}Shutting down dev launcher…${RESET}`);
    if (backendProcess) {
      backendProcess.kill();
    }
    process.exit(0);
  };
  process.on('SIGINT', cleanup);
  process.on('SIGTERM', cleanup);
}

function connectCliWebSocket(url) {
  try {
    const ws = new WebSocket(url);
    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data);
        if (msg.type === 'EVENT') {
          const ev = msg.event;
          const data = msg.data;
          const time = new Date().toTimeString().split(' ')[0];
          if (ev === 'AlertTriggered') {
            const d = data.data || data;
            console.log(`\x1b[43m\x1b[30m 🚨 WATCHDOG ALERT [${(d.severity || 'WARN').toUpperCase()}] \x1b[0m ${time} - ${d.rule_name || 'Alert'}: ${d.message || ''}`);
          } else if (ev === 'OperationProgress') {
            const d = data.data || data;
            console.log(`${MAGENTA}⚙️  [PROGRESS]${RESET} ${time} - [${d.stage || 'exec'}] ${d.message || ''}`);
          } else if (ev === 'ProjectUpdated') {
            console.log(`${GREEN}🔄 [SYNC]${RESET} ${time} - Project repository state updated`);
          } else if (ev === 'ResyncRequired') {
            console.log(`${YELLOW}⚠️ [RESYNC]${RESET} ${time} - Resync required`);
          }
        }
      } catch {}
    };
    ws.onerror = () => {};
    ws.onclose = () => {
      setTimeout(() => connectCliWebSocket(url), 5000);
    };
  } catch {}
}

main().catch(err => {
  console.error('Dev server error:', err);
  process.exit(1);
});
