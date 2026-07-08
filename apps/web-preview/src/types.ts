/* ────────────────────────────────────────────────
   types.ts  —  Frontend types for GenUI preview
   ──────────────────────────────────────────────── */

/** A single node in the canonical UI JSON tree */
export interface ComponentNode {
  type: string;
  props?: Record<string, unknown>;
  children?: ComponentNode[];
}

/** Shape of the debug trace returned by the backend */
export interface DebugTrace {
  routerOutput?: unknown;
  selectedPaths?: unknown;
  toolCallChain?: unknown[];
  variablePlan?: unknown;
  toolResults?: unknown;
  resolvedVariables?: unknown;
  layoutOutput?: unknown;
  validationResult?: unknown;
}

/** Full response from POST /api/generate */
export interface GenerateResponse {
  debug: DebugTrace;
  widget: ComponentNode;
}
