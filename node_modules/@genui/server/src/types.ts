// ============================================================
// GenUI Widget Platform - Core Type Definitions
// ============================================================

// --- LLM Provider ---

export interface Message {
  role: 'system' | 'user' | 'assistant' | 'tool';
  content: string | null;
  tool_calls?: ToolCall[];
  tool_call_id?: string;
}

export interface ToolCall {
  id: string;
  type: 'function';
  function: {
    name: string;
    arguments: string;
  };
}

export interface ToolDefinition {
  type: 'function';
  function: {
    name: string;
    description: string;
    parameters: Record<string, any>;
  };
}

export interface LLMResponse {
  content: string | null;
  tool_calls?: ToolCall[];
  finish_reason: string;
  usage?: {
    prompt_tokens: number;
    completion_tokens: number;
    total_tokens: number;
  };
}

export interface LLMChatParams {
  model: string;
  messages: Message[];
  tools?: ToolDefinition[];
  response_format?: { type: 'json_object' };
  temperature?: number;
}

export interface LLMProvider {
  chat(params: LLMChatParams): Promise<LLMResponse>;
}

// --- Capability Router (LLM-1) ---

export interface RouterInput {
  user_query: string;
  skills: SkillSummary[];
  tools: ToolSummary[];
  assets: AssetSummary[];
}

export interface RouterOutput {
  selected_paths: {
    skills: string[];
    tools: string[];
    assets: string[];
  };
  confidence: number;
  clarification_required?: boolean;
  reason: string;
  is_decision_query?: boolean;
}

export interface CapabilityRouter {
  route(input: RouterInput): Promise<RouterOutput>;
}

// --- Variable Planner (LLM-2) ---

export interface VariablePlannerInput {
  user_query: string;
  domain: string;
  selected_paths: RouterOutput['selected_paths'];
  default_tools: ToolDefinition[];
  domain_tools: ToolDefinition[];
}

export interface VariableSource {
  type: 'tool' | 'static' | 'derived';
  tool_path?: string;
  parameters?: Record<string, any>;
  value?: any;
  expression?: string;
}

export interface VariableDefinition {
  variable_name: string;
  variable_type: 'string' | 'number' | 'boolean' | 'object' | 'array';
  semantic_type?: string;
  source: VariableSource;
  importance: 'required' | 'optional';
  description?: string;
}

export interface VariablePlannerOutput {
  status: 'finish' | 'error' | 'clarification';
  clarification_required: boolean;
  clarification_message?: string;
  variables: VariableDefinition[];
  assets: string[];
  tool_call_trace: ToolCallTraceEntry[];
  messages?: Message[];
}

export interface ToolCallTraceEntry {
  tool_name: string;
  parameters: Record<string, any>;
  result: any;
  timestamp: string;
}

export interface VariablePlanner {
  plan(input: VariablePlannerInput): Promise<VariablePlannerOutput>;
}

// --- Plugin Registry / Default Tool Handler ---

export interface SkillSummary {
  path: string;
  name: string;
  description: string;
}

export interface ToolSummary {
  path: string;
  name: string;
  description: string;
}

export interface AssetSummary {
  path: string;
  name: string;
  description: string;
}

export interface SkillContent {
  name: string;
  path: string;
  content: string;
}

export interface ToolContent {
  name: string;
  path: string;
  definition: Record<string, any>;
}

export interface AssetContent {
  name: string;
  path: string;
  data: any;
}

export interface DefaultToolHandler {
  listSkills(domain?: string): Promise<SkillSummary[]>;
  listTools(domain?: string): Promise<ToolSummary[]>;
  listAssets(domain?: string): Promise<AssetSummary[]>;
  readSkill(path: string): Promise<SkillContent | SkillContent[]>;
  readTool(path: string): Promise<ToolContent | ToolContent[]>;
  readAsset(path: string): Promise<AssetContent | AssetContent[]>;
}

// --- Domain Tool Executor ---

export interface DomainToolExecutor {
  execute(toolPath: string, params: Record<string, any>): Promise<any>;
}

// --- Variable Resolver ---

export interface VariablePlan {
  variables: VariableDefinition[];
  assets: string[];
}

export interface ResolvedVariables {
  [key: string]: any;
}

export interface VariableResolver {
  resolve(plan: VariablePlan): Promise<ResolvedVariables>;
}

// --- Layout Generator (LLM-3) ---

export interface VariableDefinitionSummary {
  name: string;
  type: string;
  semantic_type?: string;
  description?: string;
  structure?: any;
  source_info?: {
    type: string;
    tool_path?: string;
    parameters?: any;
  };
}

export interface LayoutInput {
  user_query: string;
  variable_definitions: VariableDefinitionSummary[];
  assets: Record<string, any>;
  component_catalog: string;
}

export interface LayoutOutput {
  ui: UIComponent;
  raw_response?: string;
}

export interface UIComponent {
  type: string;
  props?: Record<string, any>;
  children?: UIComponent[];
}

export interface LayoutGenerator {
  generate(input: LayoutInput): Promise<LayoutOutput>;
}

// --- UI Validator ---

export interface ValidationResult {
  valid: boolean;
  errors: ValidationError[];
  warnings: ValidationWarning[];
}

export interface ValidationError {
  path: string;
  message: string;
  component_type?: string;
}

export interface ValidationWarning {
  path: string;
  message: string;
}

export interface UIValidator {
  validate(uiJson: any): ValidationResult;
}

// --- Debug Trace ---

export interface DebugTrace {
  timestamp: string;
  duration_ms: number;
  steps: {
    router?: {
      input: RouterInput;
      output: RouterOutput;
      duration_ms: number;
      messages?: Message[];
    };
    planner?: {
      tool_call_trace: ToolCallTraceEntry[];
      variable_plan: VariableDefinition[];
      assets: string[];
      duration_ms: number;
      messages?: Message[];
    };
    resolver?: {
      tool_calls: Array<{ tool_path: string; params: any; result: any }>;
      resolved_variables: ResolvedVariables;
      duration_ms: number;
    };
    layout?: {
      input_summary: {
        variable_count: number;
        asset_count: number;
      };
      output: LayoutOutput;
      duration_ms: number;
      messages?: Message[];
    };
    validation?: {
      result: ValidationResult;
    };
  };
}

// --- Pipeline Result ---

export interface PipelineResult {
  success: boolean;
  ui?: UIComponent;
  error?: string;
  debug: DebugTrace;
}

// --- Plugin Manifest ---

export interface PluginManifestEntry {
  path: string;
  name: string;
  file: string;
  description: string;
}

export interface PluginManifest {
  domain: string;
  name: string;
  version: string;
  description: string;
  skills: PluginManifestEntry[];
  tools: PluginManifestEntry[];
  assets: PluginManifestEntry[];
}
