import React from 'react';
import { Download } from 'lucide-react';

interface NavbarProps {
  activeTab: string;
  onTabChange: (tab: string) => void;
  onDownloadSpecs: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({ activeTab, onTabChange, onDownloadSpecs }) => {
  const tabs = [
    { id: 'overview', label: 'Overview' },
    { id: 'inspector', label: 'Protocol Inspector' },
    { id: 'pairing', label: 'Pairing Simulator' },
    { id: 'sandbox', label: 'Transfer Engine' },
    { id: 'vectors', label: 'Test Vectors' },
    { id: 'android', label: 'Android Codebase' },
  ];

  return (
    <header className="flex items-center justify-between gap-8 px-6 py-4 bg-[#0F172A] border-b border-slate-800">
      {/* Zone 1: Single text element wordmark */}
      <div className="flex items-center gap-3 shrink-0">
        <span className="text-lg font-bold tracking-tight text-white whitespace-nowrap">
          Handoff Files
        </span>
        <span className="text-xs text-slate-400 font-medium">Protocol Workbench</span>
      </div>

      {/* Zone 2: Concise single-line navigation tabs */}
      <nav className="hidden lg:flex items-center gap-1 shrink-0">
        {tabs.map((tab) => {
          const isActive = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              onClick={() => onTabChange(tab.id)}
              className={`px-3 py-1.5 text-xs font-medium rounded-lg transition-colors whitespace-nowrap shrink-0 ${
                isActive
                  ? 'bg-slate-800 text-white shadow-sm'
                  : 'text-slate-400 hover:text-white hover:bg-slate-800/50'
              }`}
            >
              {tab.label}
            </button>
          );
        })}
      </nav>

      {/* Zone 3: 1 primary action */}
      <div className="flex items-center gap-3 shrink-0">
        <button
          onClick={onDownloadSpecs}
          className="flex items-center gap-2 px-4 py-2 text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-500 rounded-lg transition-colors whitespace-nowrap shrink-0"
        >
          <Download className="w-3.5 h-3.5" />
          <span>Download Project ZIP</span>
        </button>
      </div>
    </header>
  );
};
