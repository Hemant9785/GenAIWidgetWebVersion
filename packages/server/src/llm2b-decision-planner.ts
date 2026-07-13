// ============================================================
// GenUI Widget Platform - Decision Agent (LLM-2B)
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

export class LLM2BDecisionPlanner implements VariablePlanner {
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
    const model = process.env.LLM2B_MODEL || this.defaultModel;
    const tool_call_trace: ToolCallTraceEntry[] = [];

    const systemPrompt = `You are the Decision Agent (LLM-2B) for a plugin-based dynamic widget platform.
Your task is to analyze advice, recommendation, summary, or suggestion queries for the "${input.domain}" domain, fetch the required raw data, reason on the numbers, and output a set of static variables containing the recommendation.

To accomplish this:
1. You have access to both DEFAULT TOOLS (list/read registry) and DOMAIN-SPECIFIC TOOLS (fetch weather, history, etc.).
2. You MUST run an agentic loop:
   - Call default tools to discover domain capabilities.
   - CALL DOMAIN TOOLS DIRECTLY INSIDE THIS PLANNING PHASE to fetch the real data. Since the query requires reasoning/advice, you cannot delegate data fetching to the resolver later.
   - Inspect the returned raw JSON data, perform reasoning, and form your recommendation.
3. Once you have made the decision, output the final plan in JSON format.
4. Your output variables MUST be of type "static" (value contains the raw advice details). This ensures the resolved values are immediately available to the layout engine without any resolver queries.
5. VIRTUAL DOMAIN: If the target domain is "general", you DO NOT need to call any domain-specific tools or default tools. Instead, immediately answer the query directly using your internal pre-trained knowledge base. Define a static variable (usually named "generalInfo") containing detailed, structured answer attributes. IMPORTANT: If there are lists, recommendations, or tables of items (such as multiple countries, facts, steps, or features), you MUST represent them as arrays of objects (e.g., countries: [{ name: "Italy", description: "..." }] or facts: [{ title: "...", detail: "..." }]) instead of raw key-value objects. This allows the layout engine to map a List component over them cleanly.

EXPECTED JSON OUTPUT STRUCTURE:
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
          "name": "Bangalore",
          "latitude": 12.9716,
          "longitude": 77.5946
        }
      },
      "importance": "required"
    },
    {
      "variable_name": "decisionAdvice",
      "variable_type": "object",
      "semantic_type": "recommendation_advice",
      "source": {
        "type": "static",
        "value": {
          "recommendation": "Yes, carry an umbrella!",
          "reason": " Bangalore has an 85% probability of rain today with moderate showers expected.",
          "severity": "medium",
          "color": "#ff9f43"
        }
      },
      "importance": "required"
    }
  ],
  "assets": []
}

# Few-Shot Agentic Loop Trace Example
User Query: "should I carry an umbrella today in Bangalore?"

1. ASSISTANT calls list_tools(domain: "weather") -> returns tool definitions.
2. ASSISTANT calls read_tool(path: "/tool/weather/forecast") -> reads parameter options.
3. ASSISTANT calls tool__weather__geocode(name: "Bangalore") -> returns lat/lng.
4. ASSISTANT calls tool__weather__forecast(latitude: 12.97, longitude: 77.59, daily: ["weather_code", "precipitation_probability_max"]) -> returns rain probability of 85%.
5. ASSISTANT reasons: "The weather forecast reports an 85% rain probability today. Therefore, the user should carry an umbrella."
6. ASSISTANT outputs finish status JSON with static variable location and static variable umbrellaAdvice holding the recommendation.

Current Domain: ${input.domain}
User Query: "${input.user_query}"`;

    const messages: Message[] = [
      { role: 'system', content: systemPrompt },
      { role: 'user', content: `Analyze this query and generate the decision plan: "${input.user_query}"` }
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
        console.error('Decision Agent iteration error:', err);
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
      clarification_message: 'Max iterations reached in agentic decision loop.',
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
