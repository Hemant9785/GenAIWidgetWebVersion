// ============================================================
// GenUI Widget Platform - OpenAI LLM Provider
// Wraps the OpenAI SDK behind the LLMProvider interface
// ============================================================

import OpenAI from 'openai';
import type { LLMProvider, LLMChatParams, LLMResponse } from './types.js';

export class OpenAIProvider implements LLMProvider {
  private client: OpenAI;

  constructor(apiKey?: string) {
    this.client = new OpenAI({
      apiKey: apiKey || process.env.OPENAI_API_KEY,
    });
  }

  async chat(params: LLMChatParams): Promise<LLMResponse> {
    const requestParams: OpenAI.Chat.ChatCompletionCreateParamsNonStreaming = {
      model: params.model,
      messages: params.messages.map(msg => {
        if (msg.role === 'tool') {
          return {
            role: 'tool' as const,
            content: msg.content || '',
            tool_call_id: msg.tool_call_id!,
          };
        }

        if (msg.role === 'assistant' && msg.tool_calls && msg.tool_calls.length > 0) {
          return {
            role: 'assistant' as const,
            content: msg.content,
            tool_calls: msg.tool_calls.map(tc => ({
              id: tc.id,
              type: 'function' as const,
              function: {
                name: tc.function.name,
                arguments: tc.function.arguments,
              },
            })),
          };
        }

        return {
          role: msg.role as 'system' | 'user' | 'assistant',
          content: msg.content || '',
        };
      }),
    };

    if (params.tools && params.tools.length > 0) {
      requestParams.tools = params.tools.map(t => ({
        type: 'function' as const,
        function: {
          name: t.function.name,
          description: t.function.description,
          parameters: t.function.parameters,
        },
      }));
    }

    if (params.response_format) {
      requestParams.response_format = params.response_format;
    }

    if (params.temperature !== undefined) {
      requestParams.temperature = params.temperature;
    }

    const response = await this.client.chat.completions.create(requestParams);
    const choice = response.choices[0];

    return {
      content: choice.message.content,
      tool_calls: choice.message.tool_calls?.map(tc => ({
        id: tc.id,
        type: 'function' as const,
        function: {
          name: tc.function.name,
          arguments: tc.function.arguments,
        },
      })),
      finish_reason: choice.finish_reason || 'stop',
      usage: response.usage
        ? {
            prompt_tokens: response.usage.prompt_tokens,
            completion_tokens: response.usage.completion_tokens,
            total_tokens: response.usage.total_tokens,
          }
        : undefined,
    };
  }
}
