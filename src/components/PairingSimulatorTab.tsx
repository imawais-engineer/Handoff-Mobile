import React, { useState, useEffect } from 'react';
import QRCode from 'qrcode';
import { ShieldAlert, ShieldCheck, RefreshCw, KeyRound, QrCode, Timer, Lock } from 'lucide-react';
import { deriveMockSessionKeys, generateRandomBytes, bytesToHex } from '../utils/crypto';

export const PairingSimulatorTab: React.FC = () => {
  const [hostIp, setHostIp] = useState('192.168.1.105');
  const [port, setPort] = useState(8943);
  const [qrDataUrl, setQrDataUrl] = useState<string>('');
  const [token, setToken] = useState('7f4a9b2c01d4e83f');
  const [sasPin, setSasPin] = useState('492817');
  const [timeLeft, setTimeLeft] = useState(120);

  // Client test input
  const [inputPin, setInputPin] = useState('');
  const [failureCount, setFailureCount] = useState(0);
  const [lockoutRemaining, setLockoutRemaining] = useState(0);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [sessionKey, setSessionKey] = useState<string | null>(null);

  // Generate new ephemeral pairing parameters
  const refreshPairing = async () => {
    const newToken = bytesToHex(generateRandomBytes(8));
    const clientNonce = generateRandomBytes(12);
    const serverNonce = generateRandomBytes(12);
    const { sasPin: newPin, rawKeyHex } = await deriveMockSessionKeys(clientNonce, serverNonce);

    setToken(newToken);
    setSasPin(newPin);
    setTimeLeft(120);
    setFailureCount(0);
    setLockoutRemaining(0);
    setStatusMessage(null);
    setSessionKey(null);
    setInputPin('');

    const qrUri = `handoff://v1?ip=${hostIp}&port=${port}&token=${newToken}&pin=${newPin}`;
    try {
      const url = await QRCode.toDataURL(qrUri, {
        width: 220,
        margin: 1,
        color: { dark: '#FFFFFF', light: '#0F172A' },
      });
      setQrDataUrl(url);
    } catch (e) {
      console.error(e);
    }
  };

  useEffect(() => {
    refreshPairing();
  }, [hostIp, port]);

  // Expiry countdown
  useEffect(() => {
    const interval = setInterval(() => {
      setTimeLeft((prev) => (prev > 0 ? prev - 1 : 0));
    }, 1000);
    return () => clearInterval(interval);
  }, []);

  // Lockout countdown
  useEffect(() => {
    if (lockoutRemaining <= 0) return;
    const interval = setInterval(() => {
      setLockoutRemaining((prev) => (prev > 0 ? prev - 1 : 0));
    }, 1000);
    return () => clearInterval(interval);
  }, [lockoutRemaining]);

  const handleAttemptPairing = async () => {
    if (lockoutRemaining > 0) {
      setStatusMessage(`Rate limit active: connection locked for ${lockoutRemaining}s.`);
      return;
    }
    if (timeLeft <= 0) {
      setStatusMessage('Pairing ticket expired. Please regenerate a new code.');
      return;
    }

    if (inputPin.trim() === sasPin) {
      // Success!
      setFailureCount(0);
      setSessionKey(`0x${bytesToHex(generateRandomBytes(32))}`);
      setStatusMessage('Authentication successful! ECDH key agreement verified via SAS.');
    } else {
      // Failed attempt
      const nextFailures = failureCount + 1;
      setFailureCount(nextFailures);

      if (nextFailures >= 3) {
        setLockoutRemaining(30);
        setStatusMessage('Brute-force lockout triggered! Max 3 attempts reached. Locked for 30 seconds.');
      } else {
        setStatusMessage(`Authentication failed: incorrect 6-digit PIN. (${nextFailures}/3 attempts used).`);
      }
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-bold text-white">Pairing & PAKE / SAS Simulator</h2>
        <p className="text-xs text-slate-400">
          Simulate Android receiver pairing state, dynamic QR generation, 6-digit Short Authentication String, and brute-force rate limiting.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Left: Receiver Card (Displaying QR and PIN) */}
        <div className="p-6 bg-slate-900 border border-slate-800 rounded-2xl space-y-6">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <QrCode className="w-5 h-5 text-indigo-400" />
              <h3 className="text-sm font-bold text-white">Receiver Screen (Android/Peer)</h3>
            </div>
            <button
              onClick={refreshPairing}
              className="flex items-center gap-1.5 px-3 py-1 text-xs text-slate-300 hover:text-white bg-slate-800 hover:bg-slate-700 rounded-lg transition-colors"
            >
              <RefreshCw className="w-3 h-3" />
              <span>Regenerate</span>
            </button>
          </div>

          {/* QR Code and Parameters */}
          <div className="flex flex-col sm:flex-row items-center gap-6 p-4 bg-slate-950 rounded-xl border border-slate-800/80">
            {qrDataUrl ? (
              <img
                src={qrDataUrl}
                alt="Handoff Pairing QR"
                className="w-40 h-40 rounded-lg p-1 bg-slate-900 border border-slate-800"
              />
            ) : (
              <div className="w-40 h-40 bg-slate-900 rounded-lg flex items-center justify-center text-xs text-slate-500">
                Generating QR...
              </div>
            )}

            <div className="space-y-3 text-xs flex-1">
              <div>
                <span className="text-slate-400">Endpoint:</span>
                <div className="font-mono text-white font-semibold">{hostIp}:{port}</div>
              </div>
              <div>
                <span className="text-slate-400">Ephemeral Token:</span>
                <div className="font-mono text-indigo-400 text-[11px] truncate">{token}</div>
              </div>
              <div className="flex items-center gap-2 text-slate-400">
                <Timer className="w-4 h-4 text-amber-400" />
                <span className="tabular-nums font-mono">
                  {timeLeft > 0 ? `Expires in ${timeLeft}s` : 'Expired'}
                </span>
              </div>
            </div>
          </div>

          {/* 6-Digit PIN Banner */}
          <div className="text-center space-y-2 p-4 bg-indigo-500/10 border border-indigo-500/20 rounded-xl">
            <div className="text-[11px] font-bold text-indigo-400 uppercase tracking-widest">
              Manual 6-Digit Verification PIN (SAS)
            </div>
            <div className="text-3xl font-extrabold font-mono text-white tracking-widest">
              {sasPin.slice(0, 3)} {sasPin.slice(3)}
            </div>
            <div className="text-[11px] text-slate-400">
              Low-entropy PIN protected by ECDH key derivation and 3-attempt rate limiter.
            </div>
          </div>
        </div>

        {/* Right: Sender Pairing Interaction & Rate Limiter Tester */}
        <div className="p-6 bg-slate-900 border border-slate-800 rounded-2xl space-y-6">
          <div className="flex items-center gap-2">
            <KeyRound className="w-5 h-5 text-indigo-400" />
            <h3 className="text-sm font-bold text-white">Client Pairing / Brute-Force Test Bench</h3>
          </div>

          <p className="text-xs text-slate-400 leading-relaxed">
            Test PIN submission against the server rate limiter. Enter the correct 6-digit code or intentionally
            submit 3 wrong codes to trigger the automatic 30-second lockout.
          </p>

          <div className="space-y-4">
            <div>
              <label className="block text-xs font-medium text-slate-300 mb-1.5">
                Enter 6-Digit PIN
              </label>
              <div className="flex gap-2">
                <input
                  type="text"
                  maxLength={6}
                  value={inputPin}
                  onChange={(e) => setInputPin(e.target.value.replace(/\D/g, ''))}
                  placeholder="e.g. 492817"
                  disabled={lockoutRemaining > 0}
                  className="flex-1 bg-slate-950 border border-slate-800 rounded-lg px-3.5 py-2 text-white font-mono tracking-widest text-base focus:outline-none focus:border-indigo-500 disabled:opacity-50"
                />
                <button
                  onClick={handleAttemptPairing}
                  disabled={lockoutRemaining > 0 || inputPin.length !== 6}
                  className="px-5 py-2 text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-500 rounded-lg transition-colors disabled:opacity-50"
                >
                  Verify
                </button>
              </div>
            </div>

            {/* Quick Fill Buttons for Testing */}
            <div className="flex flex-wrap gap-2 text-xs">
              <button
                onClick={() => setInputPin(sasPin)}
                className="px-2.5 py-1 text-slate-400 hover:text-white bg-slate-800 rounded"
              >
                Autofill Correct PIN ({sasPin})
              </button>
              <button
                onClick={() => setInputPin('000000')}
                className="px-2.5 py-1 text-slate-400 hover:text-white bg-slate-800 rounded"
              >
                Autofill Wrong PIN (000000)
              </button>
            </div>

            {/* Rate Limiter Status Monitor */}
            <div className="p-4 bg-slate-950 rounded-xl border border-slate-800 space-y-2">
              <div className="flex items-center justify-between text-xs">
                <span className="text-slate-400">Failed Attempts:</span>
                <span className="font-mono font-semibold text-white">
                  {failureCount} / 3
                </span>
              </div>
              <div className="flex items-center justify-between text-xs">
                <span className="text-slate-400">Rate Limiter Lockout:</span>
                <span
                  className={`font-mono font-semibold ${
                    lockoutRemaining > 0 ? 'text-rose-400' : 'text-emerald-400'
                  }`}
                >
                  {lockoutRemaining > 0 ? `LOCKED (${lockoutRemaining}s)` : 'ARMED / ACTIVE'}
                </span>
              </div>
            </div>

            {/* Status Notification */}
            {statusMessage && (
              <div
                className={`p-3.5 rounded-lg border text-xs flex items-center gap-2.5 ${
                  sessionKey
                    ? 'bg-emerald-950/20 border-emerald-900/50 text-emerald-300'
                    : lockoutRemaining > 0
                    ? 'bg-rose-950/30 border-rose-900/50 text-rose-300'
                    : 'bg-amber-950/20 border-amber-900/50 text-amber-300'
                }`}
              >
                {sessionKey ? (
                  <ShieldCheck className="w-4 h-4 text-emerald-400 shrink-0" />
                ) : (
                  <ShieldAlert className="w-4 h-4 text-rose-400 shrink-0" />
                )}
                <span>{statusMessage}</span>
              </div>
            )}

            {/* Established Session Keys */}
            {sessionKey && (
              <div className="p-4 bg-slate-950 rounded-xl border border-emerald-900/40 space-y-2">
                <div className="text-xs font-semibold text-emerald-400">Derived AES-256-GCM Session Key</div>
                <div className="font-mono text-xs text-slate-300 break-all">{sessionKey}</div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
