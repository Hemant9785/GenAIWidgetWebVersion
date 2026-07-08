// ============================================================
// GenUI Widget Platform - LLM-1 Capability Router
// ============================================================

import type { LLMProvider, RouterInput, RouterOutput } from './types.js';

export class LLM1Router {
  private provider: LLMProvider;
  private defaultModel: string;

  constructor(provider: LLMProvider, defaultModel: string = 'gpt-4o-mini') {
    this.provider = provider;
    this.defaultModel = defaultModel;
  }

  async route(input: RouterInput): Promise<RouterOutput> {
    const model = process.env.LLM1_MODEL || this.defaultModel;

    const systemPrompt = `You are the LLM-1 Capability Router for a plugin-based GenUI widget platform.
Your task is to analyze the user's query and route it to the correct domain, intent, and select the minimal set of skill, tool, and asset paths required to build the widget.

Available Domain Plugins are described below in terms of their skills, tools, and assets:

1. SKILLS (Widget layout descriptions and domain rules):
${input.skills.map(s => `- Path: "${s.path}"\n  Name: "${s.name}"\n  Description: ${s.description}`).join('\n')}

2. TOOLS (Data fetching API definitions):
${input.tools.map(t => `- Path: "${t.path}"\n  Name: "${t.name}"\n  Description: ${t.description}`).join('\n')}

3. ASSETS (Static assets like icons):
${input.assets.map(a => `- Path: "${a.path}"\n  Name: "${a.name}"\n  Description: ${a.description}`).join('\n')}

Rules for Selection:
- Select ONLY paths listed above. DO NOT invent paths.
- Be precise and selective. Only route to paths that are highly relevant to the query.
- If the query is completely outside any supported domain (e.g. not weather-related, and not location-related), set clarification_required = true.
- If the query is weather-related, route it to the weather domain. Always include the location skill/tool if location resolution is required.

You MUST respond with a JSON object containing these keys:
{
  "selected_paths": {
    "skills": ["/skill/weather/weather", ...],
    "tools": ["/tool/weather/forecast", ...],
    "assets": ["/asset/weather/icons"]
  },
  "domain": "weather" (or "unknown"),
  "sub_intent": "forecast" | "current" | "air-quality" | "historical" | "unknown",
  "confidence": 0.0 to 1.0,
  "clarification_required": boolean,
  "reason": "explanation of routing choice"
}`;

    const messages = [
      { role: 'system' as const, content: systemPrompt },
      { role: 'user' as const, content: `User Query: "${input.user_query}"` }
    ];

    try {
      const response = await this.provider.chat({
        model,
        messages,
        response_format: { type: 'json_object' },
        temperature: 0.1,
      });

      const parsed: RouterOutput = JSON.parse(response.content || '{}');
      
      // Basic sanitization/validation
      if (!parsed.selected_paths) {
        parsed.selected_paths = { skills: [], tools: [], assets: [] };
      }
      if (!parsed.selected_paths.skills) parsed.selected_paths.skills = [];
      if (!parsed.selected_paths.tools) parsed.selected_paths.tools = [];
      if (!parsed.selected_paths.assets) parsed.selected_paths.assets = [];
      
      return parsed;
    } catch (err) {
      console.error('LLM-1 Capability Router error:', err);
      // Fallback
      return {
        selected_paths: { skills: [], tools: [], assets: [] },
        domain: 'unknown',
        sub_intent: 'unknown',
        confidence: 0,
        clarification_required: true,
        reason: 'Failed to execute or parse routing request.',
      };
    }
  }
}
