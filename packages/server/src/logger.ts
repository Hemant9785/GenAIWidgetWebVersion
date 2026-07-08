// ============================================================
// GenUI Widget Platform - Detailed Logger Utility
// Appends comprehensive debug traces to logs.txt in the root
// ============================================================

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const LOG_FILE_PATH = path.resolve(__dirname, '..', '..', '..', 'logs.txt');

export function logPipeline(
  query: string,
  success: boolean,
  durationMs: number,
  error?: string,
  debugTrace?: any
): void {
  const timestamp = new Date().toISOString();
  let logContent = `\n`;
  logContent += `================================================================================\n`;
  logContent += `GENUI PIPELINE EXECUTION TRACE - ${timestamp}\n`;
  logContent += `================================================================================\n`;
  logContent += `USER QUERY : "${query}"\n`;
  logContent += `STATUS     : ${success ? 'SUCCESS' : 'FAILED'}\n`;
  logContent += `DURATION   : ${durationMs}ms\n`;
  if (error) {
    logContent += `ERROR      : ${error}\n`;
  }
  logContent += `\n`;

  if (debugTrace && debugTrace.steps) {
    const steps = debugTrace.steps;

    // --- LLM-1 Router Step ---
    logContent += `--------------------------------------------------------------------------------\n`;
    logContent += `PHASE 1: CAPABILITY ROUTER (LLM-1)\n`;
    logContent += `--------------------------------------------------------------------------------\n`;
    if (steps.router) {
      logContent += `Duration: ${steps.router.duration_ms}ms\n`;
      if (steps.router.messages) {
        logContent += `[CHAT MESSAGES]\n`;
        steps.router.messages.forEach((msg: any) => {
          logContent += `  [${msg.role.toUpperCase()}]\n`;
          logContent += `${indentText(msg.content || '', 4)}\n`;
        });
      }
      logContent += `[ROUTER OUTPUT]\n`;
      logContent += `${indentText(JSON.stringify(steps.router.output, null, 2), 2)}\n`;
    } else {
      logContent += `Step not executed.\n`;
    }
    logContent += `\n`;

    // --- LLM-2 Planner Step ---
    logContent += `--------------------------------------------------------------------------------\n`;
    logContent += `PHASE 2: VARIABLE PLANNER & AGENTIC LOOP (LLM-2)\n`;
    logContent += `--------------------------------------------------------------------------------\n`;
    if (steps.planner) {
      logContent += `Duration: ${steps.planner.duration_ms}ms\n`;
      if (steps.planner.messages) {
        logContent += `[AGENT CHAT CONVERSATION HISTORY]\n`;
        steps.planner.messages.forEach((msg: any, idx: number) => {
          logContent += `  [MESSAGE ${idx + 1} - ${msg.role.toUpperCase()}]\n`;
          if (msg.content) {
            logContent += `${indentText(msg.content, 4)}\n`;
          }
          if (msg.tool_calls) {
            logContent += `    [Requested Tool Calls]:\n`;
            msg.tool_calls.forEach((tc: any) => {
              logContent += `      - ID: ${tc.id}\n`;
              logContent += `        Function: ${tc.function.name}\n`;
              logContent += `        Arguments: ${tc.function.arguments}\n`;
            });
          }
          if (msg.tool_call_id) {
            logContent += `    [Associated Tool Call ID]: ${msg.tool_call_id}\n`;
          }
        });
      }
      logContent += `[PLANNER FINAL VARIABLE PLAN]\n`;
      logContent += `${indentText(JSON.stringify(steps.planner.variable_plan, null, 2), 2)}\n`;
      logContent += `[PLANNER ASSETS]\n`;
      logContent += `${indentText(JSON.stringify(steps.planner.assets, null, 2), 2)}\n`;
    } else {
      logContent += `Step not executed.\n`;
    }
    logContent += `\n`;

    // --- Variable Resolver Step ---
    logContent += `--------------------------------------------------------------------------------\n`;
    logContent += `PHASE 3: BACKEND VARIABLE RESOLVER\n`;
    logContent += `--------------------------------------------------------------------------------\n`;
    if (steps.resolver) {
      logContent += `Duration: ${steps.resolver.duration_ms}ms\n`;
      logContent += `[EXECUTED API TOOL CALLS]\n`;
      const calls = steps.resolver.tool_calls || [];
      if (calls.length === 0) {
        logContent += `  No dynamic tool calls executed during resolution.\n`;
      } else {
        calls.forEach((tc: any, i: number) => {
          logContent += `  [API Call ${i + 1}] Path: ${tc.tool_path}\n`;
          logContent += `    Parameters:\n${indentText(JSON.stringify(tc.params, null, 2), 6)}\n`;
          logContent += `    Response:\n${indentText(JSON.stringify(tc.result, null, 2), 6)}\n`;
        });
      }
      logContent += `[RESOLVED VARIABLES MAP]\n`;
      logContent += `${indentText(JSON.stringify(steps.resolver.resolved_variables, null, 2), 2)}\n`;
    } else {
      logContent += `Step not executed.\n`;
    }
    logContent += `\n`;

    // --- LLM-3 Layout Step ---
    logContent += `--------------------------------------------------------------------------------\n`;
    logContent += `PHASE 4: LAYOUT GENERATOR (LLM-3)\n`;
    logContent += `--------------------------------------------------------------------------------\n`;
    if (steps.layout) {
      logContent += `Duration: ${steps.layout.duration_ms}ms\n`;
      if (steps.layout.messages) {
        logContent += `[CHAT MESSAGES]\n`;
        steps.layout.messages.forEach((msg: any) => {
          logContent += `  [${msg.role.toUpperCase()}]\n`;
          logContent += `${indentText(msg.content || '', 4)}\n`;
        });
      }
      logContent += `[LAYOUT OUTPUT UI JSON]\n`;
      logContent += `${indentText(JSON.stringify(steps.layout.output, null, 2), 2)}\n`;
    } else {
      logContent += `Step not executed.\n`;
    }
    logContent += `\n`;

    // --- Validator Step ---
    logContent += `--------------------------------------------------------------------------------\n`;
    logContent += `PHASE 5: UI SCHEMA VALIDATOR\n`;
    logContent += `--------------------------------------------------------------------------------\n`;
    if (steps.validation) {
      logContent += `[VALIDATION RESULT]\n`;
      logContent += `${indentText(JSON.stringify(steps.validation.result, null, 2), 2)}\n`;
    } else {
      logContent += `Step not executed.\n`;
    }
  }

  logContent += `================================================================================\n`;
  logContent += `END OF PIPELINE TRACE\n`;
  logContent += `================================================================================\n`;

  try {
    fs.appendFileSync(LOG_FILE_PATH, logContent, 'utf-8');
    console.log(`Successfully logged extremely detailed pipeline trace to: logs.txt`);
  } catch (err) {
    console.error('Failed to append to logs.txt:', err);
  }
}

function indentText(text: string, spaces: number): string {
  const indent = ' '.repeat(spaces);
  return text
    .split('\n')
    .map(line => indent + line)
    .join('\n');
}
