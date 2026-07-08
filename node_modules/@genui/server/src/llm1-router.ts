// ============================================================
// GenUI Widget Platform - Capability Router
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

    const systemPrompt = `You are the Capability Router for a plugin-based dynamic widget platform.
Your task is to analyze the user's query and route it to the correct domain by selecting the coarse, domain-level skill, tool, and asset paths.

Available Domain Plugins are listed below:

1. SKILLS (Domain-specific layout rules):
${input.skills.map(s => `- Coarse Path: "${s.path}"\n  Domain: "${s.name}"\n  Description: ${s.description}`).join('\n')}

2. TOOLS (Domain-specific API capabilities):
${input.tools.map(t => `- Coarse Path: "${t.path}"\n  Domain: "${t.name}"\n  Description: ${t.description}`).join('\n')}

3. ASSETS (Domain-specific static assets):
${input.assets.map(a => `- Coarse Path: "${a.path}"\n  Domain: "${a.name}"\n  Description: ${a.description}`).join('\n')}

Rules for Selection:
- Select ONLY domain-level coarse paths listed above. Do NOT select specific files.
- Only route to paths that are highly relevant to the query.
- FALLBACK VIRTUAL DOMAIN: If the query does not match any of the available registry domain plugins listed above, you MUST route the query to the virtual "general" domain:
  - selected_paths.skills = ["/skill/general"]
  - selected_paths.tools = ["/tool/general"]
  - selected_paths.assets = []
  - Set "is_decision_query" = true
- Determine if the query requires analysis, recommendations, advice, or suggestions based on data (e.g., "should I carry an umbrella today?", "is it a good day to buy Apple stocks?"). Set "is_decision_query" = true if it falls under these categories, else false.

You MUST respond with a JSON object containing these keys:
{
  "selected_paths": {
    "skills": ["/skill/weather"],
    "tools": ["/tool/weather"],
    "assets": ["/asset/weather"]
  },
  "confidence": 0.0 to 1.0,
  "reason": "explanation of routing choice",
  "is_decision_query": boolean
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
      console.error('Capability Router routing error:', err);
      // Fallback
      return {
        selected_paths: { skills: ['/skill/general'], tools: ['/tool/general'], assets: [] },
        confidence: 0,
        reason: 'Failed to execute or parse routing request. Falling back to general domain.',
        is_decision_query: true,
      };
    }
  }
}
