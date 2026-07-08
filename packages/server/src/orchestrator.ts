// ============================================================
// GenUI Widget Platform - Pipeline Orchestrator
// Coordinates LLM-1, LLM-2, Resolver, LLM-3, and Validator
// ============================================================

import type {
  PipelineResult,
  DebugTrace,
  LLMProvider,
  ToolDefinition,
  UIComponent,
} from './types.js';
import { PluginRegistry } from './registry.js';
import { LLM1Router } from './llm1-router.js';
import { LLM2Planner } from './llm2-planner.js';
import { OpenMeteoExecutor } from './tool-executor.js';
import { TopoVariableResolver, PlaceholderSubstituter } from './variable-resolver.js';
import { COMPONENT_CATALOG } from './component-catalog.js';
import { LLM3Layout } from './llm3-layout.js';
import { StrictUIValidator } from './validator.js';
import { logPipeline } from './logger.js';


const DEFAULT_TOOLS: ToolDefinition[] = [
  {
    type: 'function',
    function: {
      name: 'list_skills',
      description: 'List available skills in the platform, optionally filtered by domain',
      parameters: {
        type: 'object',
        properties: {
          domain: { type: 'string', description: 'Domain to filter by, e.g. "weather"' },
        },
      },
    },
  },
  {
    type: 'function',
    function: {
      name: 'list_tools',
      description: 'List available tools in the platform, optionally filtered by domain',
      parameters: {
        type: 'object',
        properties: {
          domain: { type: 'string', description: 'Domain to filter by, e.g. "weather"' },
        },
      },
    },
  },
  {
    type: 'function',
    function: {
      name: 'list_assets',
      description: 'List available assets in the platform, optionally filtered by domain',
      parameters: {
        type: 'object',
        properties: {
          domain: { type: 'string', description: 'Domain to filter by, e.g. "weather"' },
        },
      },
    },
  },
  {
    type: 'function',
    function: {
      name: 'read_skill',
      description: 'Read the contents of a skill. Takes a path like "/skill/weather/weather" (single skill) or "/skill/weather" (all weather skills)',
      parameters: {
        type: 'object',
        properties: {
          path: { type: 'string', description: 'Path to read' },
        },
        required: ['path'],
      },
    },
  },
  {
    type: 'function',
    function: {
      name: 'read_tool',
      description: 'Read the definition of a tool. Takes a path like "/tool/weather/forecast" (single tool) or "/tool/weather" (all weather tools)',
      parameters: {
        type: 'object',
        properties: {
          path: { type: 'string', description: 'Path to read' },
        },
        required: ['path'],
      },
    },
  },
];

export class WidgetOrchestrator {
  private provider: LLMProvider;
  private registry: PluginRegistry;
  private executor: OpenMeteoExecutor;
  private resolver: TopoVariableResolver;
  private router: LLM1Router;
  private planner: LLM2Planner;
  private layoutGen: LLM3Layout;
  private validator: StrictUIValidator;

  constructor(provider: LLMProvider) {
    this.provider = provider;
    this.registry = new PluginRegistry();
    this.executor = new OpenMeteoExecutor();
    this.resolver = new TopoVariableResolver(this.executor);
    
    this.router = new LLM1Router(this.provider);
    this.planner = new LLM2Planner(this.provider, this.registry, this.executor);
    this.layoutGen = new LLM3Layout(this.provider);
    this.validator = new StrictUIValidator();
  }

  async init(): Promise<void> {
    await this.registry.load();
  }

  async generateWidget(userQuery: string): Promise<PipelineResult> {
    const startTime = Date.now();
    const debug: Partial<DebugTrace> = {
      timestamp: new Date().toISOString(),
      duration_ms: 0,
      steps: {},
    };

    try {
      // 1. Capability Router (LLM-1)
      const routerStart = Date.now();
      const skillsList = await this.registry.listSkills();
      const toolsList = await this.registry.listTools();
      const assetsList = await this.registry.listAssets();

      const routerInput = {
        user_query: userQuery,
        skills: skillsList,
        tools: toolsList,
        assets: assetsList,
      };

      const routerOutput = await this.router.route(routerInput);
      const routerDuration = Date.now() - routerStart;
      debug.steps!.router = {
        input: routerInput,
        output: routerOutput,
        duration_ms: routerDuration,
        messages: [
          { role: 'user', content: `Route this query: "${userQuery}"` },
          { role: 'assistant', content: JSON.stringify(routerOutput) }
        ]
      };

      if (routerOutput.clarification_required) {
        const result = {
          success: false,
          error: routerOutput.reason || 'Clarification required.',
          debug: debug as DebugTrace,
        };
        logPipeline(userQuery, false, Date.now() - startTime, result.error, debug);
        return result;
      }

      // 2. Variable Planner (LLM-2)
      const plannerStart = Date.now();
      const domainToolDefs = this.registry.getDomainToolDefinitions(routerOutput.selected_paths.tools);
      
      const plannerOutput = await this.planner.plan({
        user_query: userQuery,
        domain: routerOutput.domain,
        selected_paths: routerOutput.selected_paths,
        default_tools: DEFAULT_TOOLS,
        domain_tools: domainToolDefs,
      });

      const plannerDuration = Date.now() - plannerStart;
      debug.steps!.planner = {
        tool_call_trace: plannerOutput.tool_call_trace,
        variable_plan: plannerOutput.variables,
        assets: plannerOutput.assets,
        duration_ms: plannerDuration,
        messages: plannerOutput.messages,
      };

      if (plannerOutput.clarification_required) {
        const result = {
          success: false,
          error: plannerOutput.clarification_message || 'Clarification required by planner.',
          debug: debug as DebugTrace,
        };
        logPipeline(userQuery, false, Date.now() - startTime, result.error, debug);
        return result;
      }

      // 3. Load Assets
      const assetData = await this.registry.loadAssets(plannerOutput.assets);

      // 4. Layout Generator (LLM-3) - Generate Templated Layout with Placeholders
      let layoutDuration = 0;
      let layoutOutput: any = null;
      let validationResult: any = null;
      let retries = 0;
      const MAX_RETRIES = 2;

      const variableDefs = plannerOutput.variables.map(v => ({
        name: v.variable_name,
        type: v.variable_type,
        description: v.description,
      }));

      let layoutInput = {
        user_query: userQuery,
        variable_definitions: variableDefs,
        assets: assetData,
        component_catalog: COMPONENT_CATALOG,
      };

      // 5. Variable Resolver (fetch data)
      const resolverStart = Date.now();
      const resolvedVariables = await this.resolver.resolve({
        variables: plannerOutput.variables,
        assets: plannerOutput.assets,
      });
      const resolverDuration = Date.now() - resolverStart;
      const executedCalls = (resolvedVariables as any).__executed_calls__ || [];

      debug.steps!.resolver = {
        tool_calls: executedCalls,
        resolved_variables: resolvedVariables,
        duration_ms: resolverDuration,
      };

      const substituter = new PlaceholderSubstituter();
      let resolvedUI: any = null;

      while (retries <= MAX_RETRIES) {
        const layoutStart = Date.now();

        if (retries > 0) {
          // Repair prompt
          layoutInput.user_query = `REPAIR REQUEST: Previous UI generation failed validation with errors:
${JSON.stringify(validationResult.errors, null, 2)}

Please fix these errors. Re-generate a valid templated UI JSON using placeholders.`;
        }

        layoutOutput = await this.layoutGen.generate(layoutInput);
        layoutDuration += Date.now() - layoutStart;

        // Substitute placeholders inside layout using resolved variables
        try {
          resolvedUI = substituter.substitute(layoutOutput.ui, resolvedVariables);
        } catch (subErr: any) {
          validationResult = {
            valid: false,
            errors: [{ path: 'substitution', message: `Substitution error: ${subErr.message}` }],
            warnings: []
          };
          retries++;
          continue;
        }

        // Validate the resolved UI
        validationResult = this.validator.validate(resolvedUI);
        
        if (validationResult.valid) {
          break;
        }

        retries++;
        console.warn(`Validation failed, retry ${retries}/${MAX_RETRIES}. Errors:`, validationResult.errors);
      }

      debug.steps!.layout = {
        input_summary: {
          variable_count: Object.keys(resolvedVariables).length,
          asset_count: Object.keys(assetData).length,
        },
        output: layoutOutput,
        duration_ms: layoutDuration,
        messages: [
          { role: 'user', content: `Generate layout template for: "${userQuery}" with variables: ${JSON.stringify(variableDefs)}` },
          { role: 'assistant', content: layoutOutput.raw_response || JSON.stringify(layoutOutput.ui) }
        ]
      };

      debug.steps!.validation = {
        result: validationResult,
      };

      debug.duration_ms = Date.now() - startTime;

      if (!validationResult.valid) {
        const result = {
          success: false,
          error: 'Failed to generate a valid UI after repair retries. Errors: ' + 
            validationResult.errors.map((e: any) => e.message).join(', '),
          debug: debug as DebugTrace,
        };
        logPipeline(userQuery, false, Date.now() - startTime, result.error, debug);
        return result;
      }

      const result = {
        success: true,
        ui: resolvedUI,
        debug: debug as DebugTrace,
      };
      logPipeline(userQuery, true, Date.now() - startTime, undefined, debug);
      return result;
    } catch (err: any) {
      console.error('Pipeline orchestrator general error:', err);
      debug.duration_ms = Date.now() - startTime;
      const result = {
        success: false,
        error: err.message || 'Unknown orchestrator pipeline error.',
        debug: debug as DebugTrace,
      };
      logPipeline(userQuery, false, debug.duration_ms, result.error, debug);
      return result;
    }
  }
}
