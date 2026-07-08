// ============================================================
// GenUI Widget Platform - LLM-3 Layout Generator
// ============================================================

import type { LLMProvider, LayoutGenerator, LayoutInput, LayoutOutput } from './types.js';

export class LLM3Layout implements LayoutGenerator {
  private provider: LLMProvider;
  private defaultModel: string;

  constructor(provider: LLMProvider, defaultModel: string = 'gpt-4o') {
    this.provider = provider;
    this.defaultModel = defaultModel;
  }

  async generate(input: LayoutInput): Promise<LayoutOutput> {
    const model = process.env.LLM3_MODEL || this.defaultModel;

    const systemPrompt = `You are the LLM-3 Layout Generator for a plugin-based GenUI widget platform.
Your task is to take the available variable definitions, selected static assets, and compose them into a beautiful, structured UI layout JSON using ONLY the components defined in the catalog.

COMPONENT CATALOG:
${input.component_catalog}

AVAILABLE VARIABLE SCHEMAS (Metadata only):
${JSON.stringify(input.variable_definitions, null, 2)}

SELECTED STATIC ASSETS:
${JSON.stringify(input.assets, null, 2)}

Rules for Layout Generation:
1. You DO NOT have access to the real variable values. Instead, you MUST write variable placeholders using double curly braces: \`{{variable_name.property}}\` or \`{{variable_name.property[index]}}\`.
   - E.g., for location name: \`{{location.name}}\`
   - E.g., for current temperature: \`{{currentWeather.current.temperature_2m}}\`
   - E.g., for WMO weather code: \`{{currentWeather.current.weather_code}}\`
   - E.g., for specific array indices: \`{{dailyForecast.daily.temperature_2m_max[0]}}\` (day 1 max temp) or \`{{dailyForecast.daily.temperature_2m_min[1]}}\` (day 2 min temp).
2. For lists (like hourly forecast or 7-day forecast), you can EITHER:
   - Generate static components for specific days using index access, e.g., \`{{dailyForecast.daily.temperature_2m_max[0]}}\` for today, \`[1]\` for tomorrow, etc. (Highly recommended for small, specific layouts).
   - Or use the "List" component and specify the "items" array reference (e.g. \`items: "{{dailyForecast.daily}}"\` or \`items: "{{hourlyForecast.hourly}}"\`). Design a template child using \`$item.property\` (e.g., \`$item.time\`, \`$item.temperature_2m_max\`, \`$item.weather_code\`).
   Note: The variable resolver automatically normalizes Open-Meteo's object-of-arrays (e.g. {time: [...], temp: [...]}) into a standard array-of-objects (e.g. [{time: ..., temp: ...}, ...]) when resolving.
3. Map weather codes in the WeatherIcon component using placeholder values, e.g. \`"code": "{{currentWeather.current.weather_code}}"\` or \`"code": "$item.weather_code"\`.
4. Output ONLY the JSON structure. No markdown, no comments, no explanations.
5. The top-level component in your JSON output MUST be a "Card".

Your response MUST be a JSON object containing the layout:
{
  "ui": {
    "type": "Card",
    "props": { ... },
    "children": [
      ...
    ]
  }
}`;

    const messages = [
      { role: 'system' as const, content: systemPrompt },
      { role: 'user' as const, content: `Generate a beautiful widget layout for the query: "${input.user_query}"` }
    ];

    try {
      const response = await this.provider.chat({
        model,
        messages,
        response_format: { type: 'json_object' },
        temperature: 0.2,
      });

      const content = response.content || '{}';
      const cleanJson = this.extractJson(content);
      const parsed = JSON.parse(cleanJson);

      if (!parsed.ui) {
        throw new Error('LLM-3 response does not contain a top-level "ui" property');
      }

      return {
        ui: parsed.ui,
        raw_response: content,
      };
    } catch (err) {
      console.error('LLM-3 Layout Generator error:', err);
      // Fallback widget
      return {
        ui: {
          type: 'Card',
          props: { padding: 24, radius: 16 },
          children: [
            {
              type: 'Column',
              props: { gap: 12, align: 'center' },
              children: [
                { type: 'Text', props: { content: 'Widget Generation Failed', size: 'lg', weight: 'bold', color: '#ff4d4d' } },
                { type: 'Text', props: { content: err instanceof Error ? err.message : 'Unknown layout generation error', size: 'sm', color: '#9898b0' } }
              ]
            }
          ]
        },
        raw_response: err instanceof Error ? err.message : 'Unknown error',
      };
    }
  }

  private extractJson(content: string): string {
    const match = content.match(/```json\s*([\s\S]*?)\s*```/);
    if (match) return match[1];
    return content.trim();
  }
}
