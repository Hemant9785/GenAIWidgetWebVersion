// ============================================================
// GenUI Widget Platform - LLM-2 Variable Planner
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

    const systemPrompt = `You are the LLM-2 Variable Planner for a plugin-based GenUI widget platform.
Your task is to plan the variables and static assets required to render a user widget.

You must determine:
1. What data (variables) needs to be fetched, and which backend tools should fetch it.
2. What static assets are needed.

To accomplish this, you have access to both:
- DEFAULT TOOLS: List/read available skills and tools in the registry.
- DOMAIN-SPECIFIC TOOLS: Fetch actual domain data (e.g. weather forecast, coordinates).

You should work in an agentic loop:
1. Use default tools (\`list_skills\`, \`read_skill\`, \`read_tool\`) to discover what variables are supported by the "${input.domain}" domain and how the domain tools work.
2. If you need to resolve ambiguous locations (e.g., city names in the query like "Bangalore"), call the geocoding tool (\`tool__weather__geocode\`) during planning to get the coordinates.
3. Once you have all the information, output the final plan.

DO NOT call domain tools to fetch weather data directly if it should be done by the resolver at runtime.
For example, if you need 7-day forecast data, define a variable with a tool-backed source (e.g., \`/tool/weather/forecast\`) with parameters (e.g. latitude: "{{location.latitude}}"). The platform will fetch it.
Only call tools during planning if you need to resolve specific static details (like geocoding a city to coordinates).

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
          "latitude": 12.9716,
          "longitude": 77.5946,
          "name": "Bangalore",
          "timezone": "Asia/Kolkata"
        }
      },
      "importance": "required"
    },
    {
      "variable_name": "dailyForecast",
      "variable_type": "array",
      "semantic_type": "daily_weather_forecast",
      "source": {
        "type": "tool",
        "tool_path": "/tool/weather/forecast",
        "parameters": {
          "latitude": "{{location.latitude}}",
          "longitude": "{{location.longitude}}",
          "forecast_days": 7,
          "daily": ["weather_code", "temperature_2m_max", "temperature_2m_min", "precipitation_probability_max"],
          "timezone": "{{location.timezone}}"
        }
      },
      "importance": "required"
    }
  ],
  "assets": ["/asset/weather/icons"]
}

If the user request is ambiguous and you cannot proceed even after trying tools, set clarification_required = true and provide a clarification_message.

Current Domain: ${input.domain}
User Query: "${input.user_query}"`;

    const messages: Message[] = [
      { role: 'system', content: systemPrompt },
      { role: 'user', content: `Analyze this query and create a variable plan: "${input.user_query}"` }
    ];

    // Combine default tools and domain tools
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
          // Only force JSON if we're not planning to call tools, but GPT-4o manages tool calls + JSON mode dynamically.
          // Wait, OpenAI doesn't allow tools + response_format: json_object in some older models, but in newer ones it does.
          // To be safe, we don't pass response_format when tools are provided, but we instruct it in the system prompt.
          temperature: 0.1,
        });

        // Add assistant response to history
        messages.push({
          role: 'assistant',
          content: response.content,
          tool_calls: response.tool_calls,
        });

        if (!response.tool_calls || response.tool_calls.length === 0) {
          // Final response
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

        // Execute tool calls
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
              // Convert tool__weather__forecast -> /tool/weather/forecast
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
        console.error('LLM-2 Variable Planner iteration error:', err);
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
    // If wrapped in markdown block
    const match = content.match(/```json\s*([\s\S]*?)\s*```/);
    if (match) return match[1];
    
    // Otherwise return as is, but strip leading/trailing whitespace
    return content.trim();
  }
}
