// ============================================================
// GenUI Widget Platform - Plugin Registry
// Loads plugin manifests and implements DefaultToolHandler
// ============================================================

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import type {
  DefaultToolHandler,
  PluginManifest,
  SkillSummary,
  ToolSummary,
  AssetSummary,
  SkillContent,
  ToolContent,
  AssetContent,
  ToolDefinition,
} from './types.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export class PluginRegistry implements DefaultToolHandler {
  private plugins: Map<string, PluginManifest> = new Map();
  private pluginsDir: string;

  constructor(pluginsDir?: string) {
    // Default: d:\widget\v2\plugins
    this.pluginsDir = pluginsDir || path.resolve(__dirname, '..', '..', '..', 'plugins');
  }

  async load(): Promise<void> {
    if (!fs.existsSync(this.pluginsDir)) {
      console.warn(`Plugins directory not found: ${this.pluginsDir}`);
      return;
    }

    const entries = fs.readdirSync(this.pluginsDir, { withFileTypes: true });

    for (const entry of entries) {
      if (!entry.isDirectory()) continue;

      const manifestPath = path.join(this.pluginsDir, entry.name, 'manifest.json');
      if (!fs.existsSync(manifestPath)) {
        console.warn(`No manifest.json found in plugin: ${entry.name}`);
        continue;
      }

      try {
        const raw = fs.readFileSync(manifestPath, 'utf-8');
        const manifest: PluginManifest = JSON.parse(raw);
        this.plugins.set(manifest.domain, manifest);
        console.log(`Loaded plugin: ${manifest.domain} (${manifest.name})`);
      } catch (err) {
        console.error(`Failed to load plugin ${entry.name}:`, err);
      }
    }
  }

  getPlugins(): PluginManifest[] {
    return Array.from(this.plugins.values());
  }

  getPlugin(domain: string): PluginManifest | undefined {
    return this.plugins.get(domain);
  }

  // ---- DefaultToolHandler Implementation ----

  async listSkills(domain?: string): Promise<SkillSummary[]> {
    const results: SkillSummary[] = [];

    for (const [d, manifest] of this.plugins) {
      if (domain && d !== domain) continue;
      for (const skill of manifest.skills) {
        results.push({
          path: skill.path,
          name: skill.name,
          description: skill.description,
        });
      }
    }

    return results;
  }

  async listTools(domain?: string): Promise<ToolSummary[]> {
    const results: ToolSummary[] = [];

    for (const [d, manifest] of this.plugins) {
      if (domain && d !== domain) continue;
      for (const tool of manifest.tools) {
        results.push({
          path: tool.path,
          name: tool.name,
          description: tool.description,
        });
      }
    }

    return results;
  }

  async listAssets(domain?: string): Promise<AssetSummary[]> {
    const results: AssetSummary[] = [];

    for (const [d, manifest] of this.plugins) {
      if (domain && d !== domain) continue;
      for (const asset of manifest.assets) {
        results.push({
          path: asset.path,
          name: asset.name,
          description: asset.description,
        });
      }
    }

    return results;
  }

  async readSkill(skillPath: string): Promise<SkillContent | SkillContent[]> {
    // Path format: /skill/{domain}/{skillName?}
    const parts = skillPath.replace(/^\//, '').split('/');
    // parts[0] = 'skill', parts[1] = domain, parts[2] = skillName (optional)
    const domain = parts[1];
    const skillName = parts[2];

    const manifest = this.plugins.get(domain);
    if (!manifest) {
      throw new Error(`Domain not found: ${domain}`);
    }

    if (skillName) {
      // Return single skill
      const entry = manifest.skills.find(s => s.name === skillName);
      if (!entry) {
        throw new Error(`Skill not found: ${skillPath}`);
      }
      const filePath = path.join(this.pluginsDir, domain, entry.file);
      const content = fs.readFileSync(filePath, 'utf-8');
      return { name: entry.name, path: entry.path, content };
    } else {
      // Return all skills in domain
      const results: SkillContent[] = [];
      for (const entry of manifest.skills) {
        const filePath = path.join(this.pluginsDir, domain, entry.file);
        const content = fs.readFileSync(filePath, 'utf-8');
        results.push({ name: entry.name, path: entry.path, content });
      }
      return results;
    }
  }

  async readTool(toolPath: string): Promise<ToolContent | ToolContent[]> {
    // Path format: /tool/{domain}/{toolName?}
    const parts = toolPath.replace(/^\//, '').split('/');
    const domain = parts[1];
    const toolName = parts[2];

    const manifest = this.plugins.get(domain);
    if (!manifest) {
      throw new Error(`Domain not found: ${domain}`);
    }

    if (toolName) {
      // Return single tool
      const entry = manifest.tools.find(t => t.name === toolName);
      if (!entry) {
        throw new Error(`Tool not found: ${toolPath}`);
      }
      const filePath = path.join(this.pluginsDir, domain, entry.file);
      const raw = fs.readFileSync(filePath, 'utf-8');
      const definition = JSON.parse(raw);
      return { name: entry.name, path: entry.path, definition };
    } else {
      // Return all tools in domain
      const results: ToolContent[] = [];
      for (const entry of manifest.tools) {
        const filePath = path.join(this.pluginsDir, domain, entry.file);
        const raw = fs.readFileSync(filePath, 'utf-8');
        const definition = JSON.parse(raw);
        results.push({ name: entry.name, path: entry.path, definition });
      }
      return results;
    }
  }

  async readAsset(assetPath: string): Promise<AssetContent | AssetContent[]> {
    // Path format: /asset/{domain}/{assetName?}
    const parts = assetPath.replace(/^\//, '').split('/');
    const domain = parts[1];
    const assetName = parts[2];

    const manifest = this.plugins.get(domain);
    if (!manifest) {
      throw new Error(`Domain not found: ${domain}`);
    }

    if (assetName) {
      const entry = manifest.assets.find(a => a.name === assetName);
      if (!entry) {
        throw new Error(`Asset not found: ${assetPath}`);
      }
      const filePath = path.join(this.pluginsDir, domain, entry.file);
      const raw = fs.readFileSync(filePath, 'utf-8');
      const data = JSON.parse(raw);
      return { name: entry.name, path: entry.path, data };
    } else {
      const results: AssetContent[] = [];
      for (const entry of manifest.assets) {
        const filePath = path.join(this.pluginsDir, domain, entry.file);
        const raw = fs.readFileSync(filePath, 'utf-8');
        const data = JSON.parse(raw);
        results.push({ name: entry.name, path: entry.path, data });
      }
      return results;
    }
  }

  // ---- Helper: Get domain tool definitions for OpenAI function calling ----

  getDomainToolDefinitions(toolPaths: string[]): ToolDefinition[] {
    const definitions: ToolDefinition[] = [];

    for (const toolPath of toolPaths) {
      const parts = toolPath.replace(/^\//, '').split('/');
      const domain = parts[1];
      const toolName = parts[2];

      const manifest = this.plugins.get(domain);
      if (!manifest) continue;

      if (toolName) {
        // Single tool
        const entry = manifest.tools.find(t => t.name === toolName);
        if (!entry) continue;

        const filePath = path.join(this.pluginsDir, domain, entry.file);
        try {
          const raw = fs.readFileSync(filePath, 'utf-8');
          const toolDef = JSON.parse(raw);

          // Convert to OpenAI function calling format
          // Use the full path as the function name (replacing / with __)
          const funcName = entry.path.replace(/^\//, '').replace(/\//g, '__');
          definitions.push({
            type: 'function',
            function: {
              name: funcName,
              description: toolDef.description || entry.description,
              parameters: toolDef.parameters || { type: 'object', properties: {} },
            },
          });
        } catch (err) {
          console.error(`Failed to load tool definition: ${toolPath}`, err);
        }
      } else {
        // All tools in domain
        for (const entry of manifest.tools) {
          const filePath = path.join(this.pluginsDir, domain, entry.file);
          try {
            const raw = fs.readFileSync(filePath, 'utf-8');
            const toolDef = JSON.parse(raw);

            const funcName = entry.path.replace(/^\//, '').replace(/\//g, '__');
            definitions.push({
              type: 'function',
              function: {
                name: funcName,
                description: toolDef.description || entry.description,
                parameters: toolDef.parameters || { type: 'object', properties: {} },
              },
            });
          } catch (err) {
            console.error(`Failed to load tool definition: ${entry.path}`, err);
          }
        }
      }
    }

    return definitions;
  }

  // ---- Helper: Load asset data by path ----

  async loadAssets(assetPaths: string[]): Promise<Record<string, any>> {
    const assets: Record<string, any> = {};

    for (const assetPath of assetPaths) {
      try {
        const result = await this.readAsset(assetPath);
        if (Array.isArray(result)) {
          for (const a of result) {
            assets[a.path] = a.data;
          }
        } else {
          assets[result.path] = result.data;
        }
      } catch (err) {
        console.error(`Failed to load asset: ${assetPath}`, err);
      }
    }

    return assets;
  }
}
