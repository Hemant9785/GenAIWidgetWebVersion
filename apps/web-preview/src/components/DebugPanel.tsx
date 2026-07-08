import React, { useState } from 'react';
import { DebugTrace } from '../types';
import './DebugPanel.css';

interface DebugPanelProps {
  trace: DebugTrace;
}

type TabType = 'router' | 'toolCalls' | 'plan' | 'resolver' | 'layout' | 'validation';

export const DebugPanel: React.FC<DebugPanelProps> = ({ trace }) => {
  const [collapsed, setCollapsed] = useState(false);
  const [activeTab, setActiveTab] = useState<TabType>('router');

  if (!trace) return null;

  const tabs: Array<{ id: TabType; label: string; exists: boolean }> = [
    { id: 'router', label: '1. Router (LLM-1)', exists: !!trace.routerOutput },
    { id: 'toolCalls', label: '2. Tool Calls (LLM-2 Loop)', exists: Array.isArray(trace.toolCallChain) && trace.toolCallChain.length > 0 },
    { id: 'plan', label: '3. Variable Plan', exists: !!trace.variablePlan },
    { id: 'resolver', label: '4. Variable Resolution', exists: !!trace.resolvedVariables },
    { id: 'layout', label: '5. Layout (LLM-3)', exists: !!trace.layoutOutput },
    { id: 'validation', label: '6. Validation', exists: !!trace.validationResult },
  ];

  const formatJson = (data: any) => {
    if (data === undefined || data === null) return <span className="json-empty">No data available</span>;
    return (
      <pre style={{ margin: 0, color: '#c4b5fd', overflowX: 'auto', fontFamily: 'monospace' }}>
        {JSON.stringify(data, null, 2)}
      </pre>
    );
  };

  const renderContent = () => {
    switch (activeTab) {
      case 'router':
        return (
          <div>
            <div className="tool-section-title">Router Input (Compact registry paths)</div>
            <div className="tool-json">{formatJson(trace.routerOutput ? (trace as any).routerInput : null)}</div>
            <div className="tool-section-title">Router Output (LLM-1 Selection)</div>
            <div className="tool-json">{formatJson(trace.routerOutput)}</div>
          </div>
        );

      case 'toolCalls':
        const toolCalls = trace.toolCallChain || [];
        return (
          <div className="tool-chain-list">
            {toolCalls.map((step: any, idx: number) => (
              <div className="tool-chain-step" key={idx}>
                <div className="tool-chain-step-header">
                  <span>Step {idx + 1}: <span className="tool-name-badge">{step.tool_name}</span></span>
                  <span className="tool-timestamp">{new Date(step.timestamp).toLocaleTimeString()}</span>
                </div>
                <div className="tool-chain-step-body">
                  <div className="tool-section-title">Arguments Passed</div>
                  <div className="tool-json">{formatJson(step.parameters)}</div>
                  <div className="tool-section-title">API Response</div>
                  <div className="tool-json">{formatJson(step.result)}</div>
                </div>
              </div>
            ))}
          </div>
        );

      case 'plan':
        return (
          <div>
            <div className="tool-section-title">Planner Output (Variables & Assets planned)</div>
            <div className="tool-json">{formatJson(trace.variablePlan)}</div>
          </div>
        );

      case 'resolver':
        return (
          <div>
            <div className="tool-section-title">Tool Calls executed at resolution</div>
            <div className="tool-json">{formatJson(trace.toolResults || (trace as any).steps?.resolver?.tool_calls)}</div>
            <div className="tool-section-title">Resolved Variables (Available to layout)</div>
            <div className="tool-json">{formatJson(trace.resolvedVariables)}</div>
          </div>
        );

      case 'layout':
        return (
          <div>
            <div className="tool-section-title">Layout Output (Raw LLM-3 response)</div>
            <div className="tool-json">{formatJson(trace.layoutOutput)}</div>
          </div>
        );

      case 'validation':
        const validRes: any = trace.validationResult;
        return (
          <div>
            <div className="tool-section-title">Validator Result</div>
            <div className="tool-json" style={{ color: validRes?.valid ? '#34d399' : '#f87171' }}>
              {formatJson(validRes)}
            </div>
          </div>
        );

      default:
        return null;
    }
  };

  return (
    <div className="debug-panel-container">
      <div className="debug-panel-header" onClick={() => setCollapsed(!collapsed)}>
        <div className="debug-panel-title">
          <span>GenUI Execution Trace</span>
          <span className="debug-indicator">PROTOTYPE DEBUGGER</span>
        </div>
        <div className={`debug-collapse-icon ${collapsed ? 'collapsed' : ''}`}>▼</div>
      </div>
      
      {!collapsed && (
        <>
          <div className="debug-tabs">
            {tabs.map(tab => (
              <button
                key={tab.id}
                className={`debug-tab-btn ${activeTab === tab.id ? 'active' : ''}`}
                onClick={() => setActiveTab(tab.id)}
                disabled={!tab.exists}
                style={{ opacity: tab.exists ? 1 : 0.4 }}
              >
                {tab.label}
              </button>
            ))}
          </div>
          <div className="debug-content">{renderContent()}</div>
        </>
      )}
    </div>
  );
};
