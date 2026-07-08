import React, { useState, useEffect } from 'react';
import { WidgetRenderer } from './components/WidgetRenderer';
import { DebugPanel } from './components/DebugPanel';
import { GenerateResponse } from './types';

const LOADING_MESSAGES = [
  '🔍 Routing capability router (LLM-1)...',
  '🤖 Running agentic variable planner (LLM-2 loop)...',
  '🌦️ Resolving variables and calling Open-Meteo APIs...',
  '🎨 Generating component layout (LLM-3)...',
  '🛡️ Validating widget structure (Strict Validator)...',
  '✨ Finalizing widget render...',
];

function App() {
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [loadingMessageIdx, setLoadingMessageIdx] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<GenerateResponse | null>(null);

  // Cycle loading messages to make the interface feel alive
  useEffect(() => {
    let interval: NodeJS.Timeout;
    if (loading) {
      interval = setInterval(() => {
        setLoadingMessageIdx(prev => (prev + 1) % LOADING_MESSAGES.length);
      }, 2500);
    } else {
      setLoadingMessageIdx(0);
    }
    return () => clearInterval(interval);
  }, [loading]);

  const handleGenerate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!query.trim()) return;

    setLoading(true);
    setError(null);
    setData(null);

    try {
      const response = await fetch('/api/generate', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ query }),
      });

      const result = await response.json();

      if (!response.ok) {
        throw new Error(result.error || `Server responded with status ${response.status}`);
      }

      // Format debug trace structure to make sure tabs display correctly
      const debugTrace = {
        routerOutput: result.debug?.steps?.router?.output,
        routerInput: result.debug?.steps?.router?.input,
        selectedPaths: result.debug?.steps?.router?.output?.selected_paths,
        toolCallChain: result.debug?.steps?.planner?.tool_call_trace || [],
        variablePlan: result.debug?.steps?.planner?.variable_plan,
        toolResults: result.debug?.steps?.resolver?.tool_calls || [],
        resolvedVariables: result.debug?.steps?.resolver?.resolved_variables,
        layoutOutput: result.debug?.steps?.layout?.output?.ui,
        validationResult: result.debug?.steps?.validation?.result,
      };

      setData({
        widget: result.widget,
        debug: debugTrace,
      });
    } catch (err: any) {
      console.error(err);
      setError(err.message || 'Failed to connect to the backend server. Make sure it is running.');
    } finally {
      setLoading(false);
    }
  };

  const handleSuggestQuery = (text: string) => {
    setQuery(text);
  };

  return (
    <div className="app-shell">
      {/* Header */}
      <header className="app-header">
        <div className="app-logo">GENUI PLATFORM</div>
        <h1 className="app-title">Widget Generator</h1>
        <p className="app-subtitle">
          Instantly generate interactive, responsive UI widgets using multi-agent planning.
        </p>
      </header>

      {/* Query input area */}
      <form onSubmit={handleGenerate} className="query-area">
        <input
          type="text"
          value={query}
          onChange={e => setQuery(e.target.value)}
          placeholder="e.g. Create a 7 day weather widget for Bangalore with rain chance"
          className="query-input"
          disabled={loading}
        />
        <button type="submit" className="generate-btn" disabled={loading || !query.trim()}>
          {loading ? 'Generating...' : 'Generate'}
        </button>
      </form>

      {/* Suggestions for testing */}
      {!loading && !data && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', justifyContent: 'center', marginTop: '-12px' }}>
          <button
            onClick={() => handleSuggestQuery('Weather in Delhi today')}
            style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: '12px', padding: '6px 12px', fontSize: '12px', color: '#9898b0', cursor: 'pointer' }}
          >
            ☀️ Delhi Today
          </button>
          <button
            onClick={() => handleSuggestQuery('Show 7 day weather forecast for Bangalore')}
            style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: '12px', padding: '6px 12px', fontSize: '12px', color: '#9898b0', cursor: 'pointer' }}
          >
            📅 7 Day Bangalore
          </button>
          <button
            onClick={() => handleSuggestQuery('Will it rain tomorrow in Mumbai?')}
            style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: '12px', padding: '6px 12px', fontSize: '12px', color: '#9898b0', cursor: 'pointer' }}
          >
            🌧️ Rain in Mumbai
          </button>
          <button
            onClick={() => handleSuggestQuery('Air quality in Hyderabad with temperature')}
            style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: '12px', padding: '6px 12px', fontSize: '12px', color: '#9898b0', cursor: 'pointer' }}
          >
            🌫️ AQI Hyderabad
          </button>
        </div>
      )}

      {/* Error banner */}
      {error && (
        <div className="error-banner">
          <span className="error-icon">⚠️</span>
          <span>{error}</span>
        </div>
      )}

      {/* Loading area */}
      {loading && (
        <div className="loading-area">
          <div className="spinner" />
          <div className="loading-text">{LOADING_MESSAGES[loadingMessageIdx]}</div>
        </div>
      )}

      {/* Widget Preview Area */}
      {!loading && (
        <section className="preview-section">
          <div className="preview-section-label">Widget Preview</div>
          {data?.widget ? (
            <div className="widget-frame">
              <WidgetRenderer node={data.widget} />
            </div>
          ) : (
            <div className="widget-frame" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <div className="empty-state">
                <span className="empty-icon">🎨</span>
                <div className="empty-title">No Widget Generated</div>
                <div className="empty-hint">
                  Enter a query above to orchestrate the generation pipeline and preview the resulting UI widget.
                </div>
              </div>
            </div>
          )}
        </section>
      )}

      {/* Debug panel */}
      {data?.debug && !loading && (
        <div style={{ marginTop: '20px' }}>
          <DebugPanel trace={data.debug} />
        </div>
      )}
    </div>
  );
}

export default App;
