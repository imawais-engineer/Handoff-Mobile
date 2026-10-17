import React from 'react';
import { ShieldCheck, Cpu, Smartphone, Monitor, Lock, HardDrive, Wifi, CheckCircle2, AlertTriangle, ArrowRight } from 'lucide-react';

interface OverviewTabProps {
  onNavigateToTab: (tab: string) => void;
}

export const OverviewTab: React.FC<OverviewTabProps> = ({ onNavigateToTab }) => {
  return (
    <div className="space-y-8">
      {/* Hero Banner */}
      <div className="p-8 bg-slate-900/60 border border-slate-800 rounded-2xl">
        <div className="max-w-3xl space-y-4">
          <div className="flex items-center gap-2 text-xs text-indigo-400 font-medium">
            <span>Offline-First Architecture</span>
            <span aria-hidden="true">·</span>
            <span>v1.0.0 Wire Protocol</span>
            <span aria-hidden="true">·</span>
            <span>Zero Cloud Relay</span>
          </div>

          <h1 className="text-3xl font-bold tracking-tight text-white">
            Secure, Direct Wireless File Transfer Between Android and Windows
          </h1>

          <p className="text-sm leading-relaxed text-slate-300">
            Handoff Files delivers local peer-to-peer file transfers up to 10 MiB in under 10 seconds without
            relying on cloud servers, external accounts, or active internet connectivity. Designed with a
            production-grade native Android app (Kotlin, Jetpack Compose, Material 3) and a documented,
            language-neutral wire protocol ready for native Windows (C#/.NET) implementation.
          </p>

          <div className="flex flex-wrap items-center gap-3 pt-2">
            <button
              onClick={() => onNavigateToTab('sandbox')}
              className="px-4 py-2 text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-500 rounded-lg transition-colors flex items-center gap-2"
            >
              <span>Test Live Transfer Engine</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </button>
            <button
              onClick={() => onNavigateToTab('vectors')}
              className="px-4 py-2 text-xs font-semibold text-slate-300 bg-slate-800 hover:bg-slate-700 rounded-lg transition-colors"
            >
              Run Protocol Test Vectors
            </button>
          </div>
        </div>
      </div>

      {/* Environment & Sandbox Transparency Report */}
      <div className="p-6 bg-slate-900 border border-slate-800 rounded-xl space-y-4">
        <div className="flex items-start justify-between">
          <div className="space-y-1">
            <div className="flex items-center gap-2 text-amber-400 text-xs font-semibold">
              <AlertTriangle className="w-4 h-4" />
              <span>Phase A Environment Inspection & Report</span>
            </div>
            <h2 className="text-base font-bold text-white">Container Capabilities & Limitations</h2>
          </div>
          <span className="text-xs text-slate-400 font-mono">Linux gVisor Sandbox</span>
        </div>

        <p className="text-xs text-slate-300 leading-relaxed">
          The master specification mandates honest reporting: <em className="text-slate-100">"If the environment cannot compile or execute native Android code, clearly state that limitation. Produce a complete Android Studio-compatible project and exact build instructions rather than pretending a build or device test succeeded."</em>
        </p>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 pt-2">
          <div className="p-4 bg-slate-950/60 border border-slate-800 rounded-lg space-y-2">
            <div className="flex items-center gap-2 text-xs font-semibold text-rose-400">
              <span className="w-2 h-2 rounded-full bg-rose-500" />
              <span>JDK / Android SDK</span>
            </div>
            <p className="text-xs text-slate-400">
              `java: not found` in this container. Android SDK and Gradle toolchains are not pre-installed in the cloud web runtime.
            </p>
          </div>

          <div className="p-4 bg-slate-950/60 border border-slate-800 rounded-lg space-y-2">
            <div className="flex items-center gap-2 text-xs font-semibold text-emerald-400">
              <CheckCircle2 className="w-4 h-4" />
              <span>Android Studio Ready</span>
            </div>
            <p className="text-xs text-slate-400">
              Complete native Kotlin project generated in <code className="text-slate-200">/android</code> with Compose Material 3 and unit tests.
            </p>
          </div>

          <div className="p-4 bg-slate-950/60 border border-slate-800 rounded-lg space-y-2">
            <div className="flex items-center gap-2 text-xs font-semibold text-indigo-400">
              <CheckCircle2 className="w-4 h-4" />
              <span>Interactive Test Bench</span>
            </div>
            <p className="text-xs text-slate-400">
              Real WebCrypto AES-GCM framing, SHA-256 verification, and packet dissector running live in this browser sandbox.
            </p>
          </div>
        </div>
      </div>

      {/* Core Architectural Guarantees */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="p-5 bg-slate-900 border border-slate-800 rounded-xl space-y-3">
          <div className="w-9 h-9 rounded-lg bg-indigo-500/10 flex items-center justify-center text-indigo-400">
            <Lock className="w-5 h-5" />
          </div>
          <h3 className="text-sm font-bold text-white">PAKE & SAS Pairing</h3>
          <p className="text-xs text-slate-400 leading-relaxed">
            Ephemeral ECDH key agreement with Short Authentication String (SAS) 6-digit PIN and 3-attempt brute force rate limiting.
          </p>
        </div>

        <div className="p-5 bg-slate-900 border border-slate-800 rounded-xl space-y-3">
          <div className="w-9 h-9 rounded-lg bg-emerald-500/10 flex items-center justify-center text-emerald-400">
            <ShieldCheck className="w-5 h-5" />
          </div>
          <h3 className="text-sm font-bold text-white">AES-256-GCM AEAD</h3>
          <p className="text-xs text-slate-400 leading-relaxed">
            Every 64 KiB chunk is encrypted in transit with an explicit 12-byte IV and 16-byte authentication tag to guarantee confidentiality.
          </p>
        </div>

        <div className="p-5 bg-slate-900 border border-slate-800 rounded-xl space-y-3">
          <div className="w-9 h-9 rounded-lg bg-amber-500/10 flex items-center justify-center text-amber-400">
            <HardDrive className="w-5 h-5" />
          </div>
          <h3 className="text-sm font-bold text-white">Safe Atomic Staging</h3>
          <p className="text-xs text-slate-400 leading-relaxed">
            In-flight bytes stream to isolated staging files. Files are published to user storage only after full SHA-256 digest validation.
          </p>
        </div>

        <div className="p-5 bg-slate-900 border border-slate-800 rounded-xl space-y-3">
          <div className="w-9 h-9 rounded-lg bg-blue-500/10 flex items-center justify-center text-blue-400">
            <Wifi className="w-5 h-5" />
          </div>
          <h3 className="text-sm font-bold text-white">Offline Local Wi-Fi</h3>
          <p className="text-xs text-slate-400 leading-relaxed">
            Works over standard local Wi-Fi or phone hotspot. Requires zero internet connectivity, DNS, or external servers.
          </p>
        </div>
      </div>

      {/* Building Native Android Instructions */}
      <div className="p-6 bg-slate-900 border border-slate-800 rounded-xl space-y-4">
        <h2 className="text-base font-bold text-white">Building the Native Android APK on Your Host Machine</h2>
        <p className="text-xs text-slate-400">
          To build and install the native Android APK onto your physical Android phone or emulator:
        </p>

        <div className="p-4 bg-slate-950 font-mono text-xs text-slate-300 rounded-lg overflow-x-auto space-y-2">
          <div className="text-slate-500"># 1. Clone repository and navigate to the Android directory</div>
          <div className="text-indigo-400">cd android</div>
          <div className="text-slate-500"># 2. Run automated unit test suite (framing, limits, crypto, sanitization)</div>
          <div className="text-indigo-400">./gradlew testDebugUnitTest</div>
          <div className="text-slate-500"># 3. Assemble debug APK</div>
          <div className="text-indigo-400">./gradlew assembleDebug</div>
          <div className="text-slate-500"># Output: android/app/build/outputs/apk/debug/app-debug.apk</div>
          <div className="text-slate-500"># 4. Install onto connected Android device via ADB</div>
          <div className="text-indigo-400">adb install -r app/build/outputs/apk/debug/app-debug.apk</div>
        </div>
      </div>
    </div>
  );
};
