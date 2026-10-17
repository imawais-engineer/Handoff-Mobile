import React, { useState } from 'react';
import { Navbar } from './components/Navbar';
import { OverviewTab } from './components/OverviewTab';
import { ProtocolInspectorTab } from './components/ProtocolInspectorTab';
import { PairingSimulatorTab } from './components/PairingSimulatorTab';
import { TransferEngineTab } from './components/TransferEngineTab';
import { TestVectorRunnerTab } from './components/TestVectorRunnerTab';
import { AndroidExplorerTab } from './components/AndroidExplorerTab';

import { generateAndroidProjectZip } from './utils/zipExporter';

export default function App() {
  const [activeTab, setActiveTab] = useState('overview');
  const [isExporting, setIsExporting] = useState(false);

  const handleDownloadSpecs = async () => {
    setIsExporting(true);
    try {
      const blob = await generateAndroidProjectZip();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'HandoffFiles-Android-Project.zip';
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      console.error('Failed to generate project zip', e);
    } finally {
      setIsExporting(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#0B0F19] text-slate-100 flex flex-col font-sans">
      <Navbar
        activeTab={activeTab}
        onTabChange={setActiveTab}
        onDownloadSpecs={handleDownloadSpecs}
      />

      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {activeTab === 'overview' && <OverviewTab onNavigateToTab={setActiveTab} />}
        {activeTab === 'inspector' && <ProtocolInspectorTab />}
        {activeTab === 'pairing' && <PairingSimulatorTab />}
        {activeTab === 'sandbox' && <TransferEngineTab />}
        {activeTab === 'vectors' && <TestVectorRunnerTab />}
        {activeTab === 'android' && <AndroidExplorerTab />}
      </main>

      <footer className="border-t border-slate-800/80 bg-[#0F172A] py-6 px-6 text-xs text-slate-500 text-center">
        <span>Handoff Files · Offline-first wireless file transfer architecture for Android & Windows</span>
        <span className="mx-2" aria-hidden="true">·</span>
        <span>Version 1.0.0</span>
      </footer>
    </div>
  );
}
