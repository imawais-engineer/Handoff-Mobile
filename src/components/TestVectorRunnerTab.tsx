import React, { useState } from 'react';
import { TEST_VECTORS } from '../utils/testVectors';
import { Play, CheckCircle2, XCircle, Clock, ShieldCheck } from 'lucide-react';

interface ResultState {
  status: 'idle' | 'running' | 'passed' | 'failed';
  durationMs?: number;
  details?: string;
}

export const TestVectorRunnerTab: React.FC = () => {
  const [results, setResults] = useState<Record<string, ResultState>>({});
  const [isRunningAll, setIsRunningAll] = useState(false);

  const runTest = async (id: string) => {
    const vector = TEST_VECTORS.find((v) => v.id === id);
    if (!vector) return;

    setResults((prev) => ({
      ...prev,
      [id]: { status: 'running' },
    }));

    const start = performance.now();
    try {
      const res = await vector.execute();
      const elapsed = Math.round(performance.now() - start);

      setResults((prev) => ({
        ...prev,
        [id]: {
          status: res.success ? 'passed' : 'failed',
          durationMs: elapsed,
          details: res.details,
        },
      }));
    } catch (e: any) {
      setResults((prev) => ({
        ...prev,
        [id]: {
          status: 'failed',
          durationMs: Math.round(performance.now() - start),
          details: `Uncaught exception: ${e.message}`,
        },
      }));
    }
  };

  const runAllTests = async () => {
    setIsRunningAll(true);
    for (const vector of TEST_VECTORS) {
      await runTest(vector.id);
    }
    setIsRunningAll(false);
  };

  const totalCount = TEST_VECTORS.length;
  const passedCount = Object.values(results).filter((r) => r.status === 'passed').length;
  const failedCount = Object.values(results).filter((r) => r.status === 'failed').length;

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-white">Deterministic Protocol Test Vectors</h2>
          <p className="text-xs text-slate-400">
            Verify wire framing, Big-Endian uint32 packing, boundary limits, AES-256-GCM AEAD tamper resistance, and path sanitization.
          </p>
        </div>

        <button
          onClick={runAllTests}
          disabled={isRunningAll}
          className="px-4 py-2 text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-500 rounded-lg transition-colors flex items-center gap-2 self-start disabled:opacity-50"
        >
          <Play className="w-3.5 h-3.5" />
          <span>{isRunningAll ? 'Running Suite...' : 'Run All Vectors'}</span>
        </button>
      </div>

      {/* Summary Scorecard */}
      <div className="grid grid-cols-3 gap-4">
        <div className="p-4 bg-slate-900 border border-slate-800 rounded-xl space-y-1">
          <div className="text-xs text-slate-400">Total Vectors</div>
          <div className="text-2xl font-bold font-mono text-white tabular-nums">{totalCount}</div>
        </div>
        <div className="p-4 bg-slate-900 border border-slate-800 rounded-xl space-y-1">
          <div className="text-xs text-emerald-400">Passed</div>
          <div className="text-2xl font-bold font-mono text-emerald-400 tabular-nums">{passedCount}</div>
        </div>
        <div className="p-4 bg-slate-900 border border-slate-800 rounded-xl space-y-1">
          <div className="text-xs text-rose-400">Failed</div>
          <div className="text-2xl font-bold font-mono text-rose-400 tabular-nums">{failedCount}</div>
        </div>
      </div>

      {/* Test Vector List */}
      <div className="space-y-3">
        {TEST_VECTORS.map((vector) => {
          const res = results[vector.id] || { status: 'idle' };

          return (
            <div
              key={vector.id}
              className="p-5 bg-slate-900 border border-slate-800 rounded-xl space-y-3"
            >
              <div className="flex items-start justify-between gap-4">
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-xs font-bold text-indigo-400">{vector.id}</span>
                    <h3 className="text-sm font-semibold text-white">{vector.name}</h3>
                  </div>
                  <p className="text-xs text-slate-400">{vector.description}</p>
                </div>

                <div className="flex items-center gap-3 shrink-0">
                  {res.status === 'passed' && (
                    <span className="flex items-center gap-1.5 px-2.5 py-1 text-xs font-semibold text-emerald-300 bg-emerald-950/60 rounded-md">
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      <span>PASS ({res.durationMs}ms)</span>
                    </span>
                  )}
                  {res.status === 'failed' && (
                    <span className="flex items-center gap-1.5 px-2.5 py-1 text-xs font-semibold text-rose-300 bg-rose-950/60 rounded-md">
                      <XCircle className="w-3.5 h-3.5" />
                      <span>FAIL ({res.durationMs}ms)</span>
                    </span>
                  )}
                  {res.status === 'running' && (
                    <span className="flex items-center gap-1.5 px-2.5 py-1 text-xs font-semibold text-amber-300 bg-amber-950/60 rounded-md">
                      <Clock className="w-3.5 h-3.5 animate-spin" />
                      <span>RUNNING</span>
                    </span>
                  )}

                  <button
                    onClick={() => runTest(vector.id)}
                    disabled={res.status === 'running'}
                    className="px-3 py-1.5 text-xs font-medium text-slate-300 hover:text-white bg-slate-800 hover:bg-slate-700 rounded-lg transition-colors"
                  >
                    Execute
                  </button>
                </div>
              </div>

              {res.details && (
                <div className="p-3 bg-slate-950 rounded-lg border border-slate-800/80 font-mono text-xs text-slate-300">
                  {res.details}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
};
