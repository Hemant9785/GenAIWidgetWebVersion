// ============================================================
// GenUI Widget Platform - Variable Planner
// ============================================================

import type {
  LLMProvider,
  VariablePlanner,
  VariablePlannerInput,
  VariablePlannerOutput,
  Message,
  ToolDefinition,
  ToolCallTraceEntry,
  DefaultToolHandler,
  DomainToolExecutor,
} from './types.js';

export class LLM2Planner implements VariablePlanner {
  private provider: LLMProvider;
  private defaultToolHandler: DefaultToolHandler;
  private domainToolExecutor: DomainToolExecutor;
  private defaultModel: string;

  constructor(
    provider: LLMProvider,
    defaultToolHandler: DefaultToolHandler,
    domainToolExecutor: DomainToolExecutor,
    defaultModel: string = 'gpt-4o'
  ) {
    this.provider = provider;
    this.defaultToolHandler = defaultToolHandler;
    this.domainToolExecutor = domainToolExecutor;
    this.defaultModel = defaultModel;
  }

  async plan(input: VariablePlannerInput): Promise<VariablePlannerOutput> {
    const model = process.env.LLM2_MODEL || this.defaultModel;
    const tool_call_trace: ToolCallTraceEntry[] = [];

    const systemPrompt = `You are the Variable Planner for a plugin-based dynamic widget platform.
Your task is to plan the variables and static assets required to render a user widget.

You must determine:
1. What data (variables) needs to be fetched, and which backend tools should fetch it.
2. What static assets are needed.

To accomplish this, you have access to both:
- DEFAULT TOOLS: List/read available skills and tools in the registry.
- DOMAIN-SPECIFIC TOOLS: Fetch actual domain data (e.g. coordinates, events).

You should work in an agentic loop:
1. Use default tools (\`list_skills\`, \`list_tools\`, \`read_skill\`, \`read_tool\`) to discover what specific files and APIs are supported by the "${input.domain}" domain and how they work.
2. If you need to resolve ambiguous query parameters (e.g. city names, ticker symbols) to get coordinates or internal IDs, call the geocode/search tool inside this planning phase immediately so the resolved parameters can be embedded as static.
3. Once you have all the information, output the final plan.

DO NOT call domain tools to fetch final transactional/large data directly if it should be done by the resolver at runtime.
Define variables with a tool-backed source (e.g. \`/tool/weather/forecast\`) with parameters (e.g. latitude: "{{location.latitude}}"). The resolver will fetch it at runtime.

When you are ready to finish, respond in JSON format with the final plan:
{
  "status": "finish",
  "clarification_required": false,
  "variables": [
    {
      "variable_name": "location",
      "variable_type": "object",
      "semantic_type": "location_coordinates",
      "source": {
        "type": "static",
        "value": {
          "latitude": 42.3601,
          "longitude": -71.0589,
          "name": "Boston",
          "timezone": "America/New_York"
        }
      },
      "importance": "required"
    },
    {
      "variable_name": "eventList",
      "variable_type": "array",
      "semantic_type": "upcoming_events",
      "source": {
        "type": "tool",
        "tool_path": "/tool/entertainment/events",
        "parameters": {
          "latitude": "{{location.latitude}}",
          "longitude": "{{location.longitude}}",
          "category": "music",
          "timezone": "{{location.timezone}}"
        }
      },
      "importance": "required"
    }
  ],
  "assets": ["/asset/entertainment/icons"]
}

# Few-Shot Agentic Loop Trace Example
User Query: "show me upcoming music events in Boston"

1. ASSISTANT calls \`list_skills(domain: "entertainment")\` -> returns \`[{"path":"/skill/entertainment/events"}]\`
2. ASSISTANT calls \`list_tools(domain: "entertainment")\` -> returns \`[{"path":"/tool/entertainment/events"}, {"path":"/tool/entertainment/geocode"}]\`
3. ASSISTANT calls \`read_tool(path: "/tool/entertainment/geocode")\` -> returns parameters schema: \`name\` (required)
4. ASSISTANT calls \`tool__entertainment__geocode(name: "Boston")\` -> returns coordinates \`{ latitude: 42.3601, longitude: -71.0589, name: "Boston", timezone: "America/New_York" }\`
5. ASSISTANT calls \`read_tool(path: "/tool/entertainment/events")\` -> returns parameters schema: \`latitude\`, \`longitude\`, \`category\`, \`timezone\`
6. ASSISTANT outputs final plan as JSON (finish status, static location variable, and tool-backed eventList variable with category "music").

Current Domain: ${input.domain}
User Query: "${input.user_query}"`;

    const messages: Message[] = [
      { role: 'system', content: systemPrompt },
      { role: 'user', content: `Analyze this query and create a variable plan: "${input.user_query}"` }
    ];

    const tools: ToolDefinition[] = [...input.default_tools, ...input.domain_tools];

    const MAX_ITERATIONS = 10;
    let iteration = 0;

    while (iteration < MAX_ITERATIONS) {
      iteration++;
      try {
        const response = await this.provider.chat({
          model,
          messages,
          tools: tools.length > 0 ? tools : undefined,
          temperature: 0.1,
        });

        messages.push({
          role: 'assistant',
          content: response.content,
          tool_calls: response.tool_calls,
        });

        if (!response.tool_calls || response.tool_calls.length === 0) {
          const content = response.content || '{}';
          const cleanJson = this.extractJson(content);
          const parsed = JSON.parse(cleanJson);

          return {
            status: parsed.status || 'finish',
            clarification_required: parsed.clarification_required || false,
            clarification_message: parsed.clarification_message,
            variables: parsed.variables || [],
            assets: parsed.assets || [],
            tool_call_trace,
            messages,
          };
        }

        for (const toolCall of response.tool_calls) {
          const fnName = toolCall.function.name;
          const fnArgs = JSON.parse(toolCall.function.arguments || '{}');
          
          let result: any;
          const timestamp = new Date().toISOString();

          try {
            if (fnName === 'list_skills') {
              result = await this.defaultToolHandler.listSkills(fnArgs.domain);
            } else if (fnName === 'list_tools') {
              result = await this.defaultToolHandler.listTools(fnArgs.domain);
            } else if (fnName === 'list_assets') {
              result = await this.defaultToolHandler.listAssets(fnArgs.domain);
            } else if (fnName === 'read_skill') {
              result = await this.defaultToolHandler.readSkill(fnArgs.path);
            } else if (fnName === 'read_tool') {
              result = await this.defaultToolHandler.readTool(fnArgs.path);
            } else if (fnName.startsWith('tool__')) {
              const toolPath = '/' + fnName.replace(/__/g, '/');
              result = await this.domainToolExecutor.execute(toolPath, fnArgs);
            } else {
              throw new Error(`Unknown tool: ${fnName}`);
            }
          } catch (err: any) {
            result = { error: err.message };
          }

          tool_call_trace.push({
            tool_name: fnName,
            parameters: fnArgs,
            result,
            timestamp,
          });

          messages.push({
            role: 'tool',
            tool_call_id: toolCall.id,
            content: JSON.stringify(result),
          });
        }
      } catch (err) {
        console.error('Variable Planner iteration error:', err);
        return {
          status: 'error',
          clarification_required: false,
          variables: [],
          assets: [],
          tool_call_trace,
          messages,
        };
      }
    }

    return {
      status: 'error',
      clarification_required: true,
      clarification_message: 'Max iterations reached in agentic planning loop.',
      variables: [],
      assets: [],
      tool_call_trace,
      messages,
    };
  }

  private extractJson(content: string): string {
    const match = content.match(/```json\s*([\s\S]*?)\s*```/);
    if (match) return match[1];
    return content.trim();
  }
}
