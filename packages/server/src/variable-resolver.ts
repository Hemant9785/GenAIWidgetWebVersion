// ============================================================
// GenUI Widget Platform - Variable Resolver & Substituter
// Resolves variables and substitutes placeholders in layout
// ============================================================

import type {
  VariableResolver,
  VariablePlan,
  ResolvedVariables,
  DomainToolExecutor,
} from './types.js';

export class TopoVariableResolver implements VariableResolver {
  private executor: DomainToolExecutor;

  constructor(executor: DomainToolExecutor) {
    this.executor = executor;
  }

  async resolve(plan: VariablePlan): Promise<ResolvedVariables> {
    const resolved: ResolvedVariables = {};
    const unresolved = [...plan.variables];
    const maxIterations = unresolved.length * 2;
    let iterations = 0;

    const executedCalls: Array<{ tool_path: string; params: any; result: any }> = [];

    while (unresolved.length > 0 && iterations < maxIterations) {
      iterations++;
      let progress = false;

      for (let i = 0; i < unresolved.length; i++) {
        const v = unresolved[i];
        
        const deps = this.findDependencies(v.source);
        const allDepsMet = deps.every(dep => dep in resolved);

        if (allDepsMet) {
          console.log(`Resolving variable: ${v.variable_name} (deps: ${deps.join(', ') || 'none'})`);
          
          try {
            const resolvedSource = this.resolvePlaceholders(v.source, resolved);
            
            if (resolvedSource.type === 'static') {
              resolved[v.variable_name] = resolvedSource.value;
            } else if (resolvedSource.type === 'tool') {
              const toolPath = resolvedSource.tool_path;
              if (!toolPath) throw new Error(`Missing tool_path for variable ${v.variable_name}`);
              
              const result = await this.executor.execute(toolPath, resolvedSource.parameters || {});
              
              let finalVal = result;
              if (toolPath.endsWith('/geocode') && result.results && result.results.length > 0) {
                const first = result.results[0];
                finalVal = {
                  latitude: first.latitude,
                  longitude: first.longitude,
                  name: first.name,
                  country: first.country,
                  timezone: first.timezone,
                };
              }

              resolved[v.variable_name] = finalVal;
              executedCalls.push({
                tool_path: toolPath,
                params: resolvedSource.parameters,
                result: finalVal,
              });
            } else if (resolvedSource.type === 'derived') {
              resolved[v.variable_name] = resolvedSource.value || resolvedSource.expression;
            } else {
              resolved[v.variable_name] = null;
            }
          } catch (err) {
            console.error(`Failed to resolve variable ${v.variable_name}:`, err);
            resolved[v.variable_name] = null;
          }

          unresolved.splice(i, 1);
          i--;
          progress = true;
        }
      }

      if (!progress) {
        throw new Error('Circular dependency or unresolved variables in plan: ' + 
          unresolved.map(u => u.variable_name).join(', ')
        );
      }
    }

    if (unresolved.length > 0) {
      throw new Error('Failed to resolve all variables within iteration limit.');
    }

    Object.defineProperty(resolved, '__executed_calls__', {
      value: executedCalls,
      enumerable: false,
      writable: true,
    });

    return resolved;
  }

  private findDependencies(obj: any): string[] {
    const deps = new Set<string>();

    const scan = (item: any) => {
      if (typeof item === 'string') {
        const matches = item.matchAll(/\{\{([^}]+)\}\}/g);
        for (const match of matches) {
          const path = match[1].trim();
          const rootVar = path.split('.')[0];
          deps.add(rootVar);
        }
      } else if (Array.isArray(item)) {
        item.forEach(scan);
      } else if (item !== null && typeof item === 'object') {
        Object.values(item).forEach(scan);
      }
    };

    scan(obj);
    return Array.from(deps);
  }

  private resolvePlaceholders(obj: any, resolved: ResolvedVariables): any {
    const resolveVal = (val: any): any => {
      if (typeof val === 'string') {
        const exactMatch = val.match(/^\{\{([^}]+)\}\}$/);
        if (exactMatch) {
          const path = exactMatch[1].trim();
          return this.getValueByPath(resolved, path);
        }

        return val.replace(/\{\{([^}]+)\}\}/g, (_, path) => {
          const retrieved = this.getValueByPath(resolved, path.trim());
          if (typeof retrieved === 'object') {
            return JSON.stringify(retrieved);
          }
          return retrieved !== undefined ? String(retrieved) : '';
        });
      } else if (Array.isArray(val)) {
        return val.map(resolveVal);
      } else if (val !== null && typeof val === 'object') {
        const newObj: Record<string, any> = {};
        for (const [k, v] of Object.entries(val)) {
          newObj[k] = resolveVal(v);
        }
        return newObj;
      }
      return val;
    };

    return resolveVal(obj);
  }

  private getValueByPath(obj: any, path: string): any {
    const regex = /^([^[\]]+)(?:\[(\d+)\])?$/;
    const parts = path.split('.');
    let current = obj;
    for (const part of parts) {
      if (current === null || current === undefined) return undefined;
      
      const match = part.match(regex);
      if (!match) return undefined;
      
      const key = match[1];
      const index = match[2];
      
      current = current[key];
      
      if (index !== undefined) {
        if (current === null || current === undefined || !Array.isArray(current)) {
          return undefined;
        }
        current = current[parseInt(index, 10)];
      }
    }
    return current;
  }
}

export class PlaceholderSubstituter {
  substitute(ui: any, resolved: ResolvedVariables): any {
    if (!ui || typeof ui !== 'object') return ui;

    const resolveVal = (val: any): any => {
      if (typeof val === 'string') {
        const exactMatch = val.match(/^\{\{([^}]+)\}\}$/);
        if (exactMatch) {
          const path = exactMatch[1].trim();
          let retrieved = this.getValueByPath(resolved, path);
          
          // If the resolved property is an object of arrays, normalize it to an array of objects
          if (retrieved && typeof retrieved === 'object' && !Array.isArray(retrieved)) {
            retrieved = this.normalizeObjectOfArrays(retrieved);
          }
          return retrieved;
        }

        return val.replace(/\{\{([^}]+)\}\}/g, (_, path) => {
          const retrieved = this.getValueByPath(resolved, path.trim());
          if (typeof retrieved === 'object') {
            return JSON.stringify(retrieved);
          }
          return retrieved !== undefined ? String(retrieved) : '';
        });
      } else if (Array.isArray(val)) {
        return val.map(resolveVal);
      } else if (val !== null && typeof val === 'object') {
        const copy: Record<string, any> = {};
        for (const [k, v] of Object.entries(val)) {
          copy[k] = resolveVal(v);
        }
        return copy;
      }
      return val;
    };

    const copyNode = {
      ...ui,
      props: ui.props ? resolveVal(ui.props) : undefined,
      children: ui.children ? ui.children.map((c: any) => this.substitute(c, resolved)) : undefined
    };

    return copyNode;
  }

  private normalizeObjectOfArrays(obj: any): any[] {
    if (!obj || typeof obj !== 'object' || Array.isArray(obj)) {
      return Array.isArray(obj) ? obj : [];
    }

    const keys = Object.keys(obj);
    const arrayKey = keys.find(k => Array.isArray(obj[k]));
    if (!arrayKey) return [];

    const length = obj[arrayKey].length;
    const normalizedList: any[] = [];
    for (let i = 0; i < length; i++) {
      const item: Record<string, any> = {};
      for (const key of keys) {
        item[key] = Array.isArray(obj[key]) ? obj[key][i] : obj[key];
      }
      normalizedList.push(item);
    }
    return normalizedList;
  }

  private getValueByPath(obj: any, path: string): any {
    const regex = /^([^[\]]+)(?:\[(\d+)\])?$/;
    const parts = path.split('.');
    let current = obj;
    for (let i = 0; i < parts.length; i++) {
      if (current === null || current === undefined) return undefined;
      
      const part = parts[i];
      const match = part.match(regex);
      if (!match) return undefined;
      
      const key = match[1];
      const index = match[2];
      
      // Check if we have a mismatch like daily[0].weather_code where daily is actually an object containing arrays
      const nextPart = parts[i + 1];
      if (index !== undefined && nextPart !== undefined) {
        const nested = current[key];
        if (nested && typeof nested === 'object' && !Array.isArray(nested)) {
          // If daily is an object and has daily.weather_code array
          const nextMatch = nextPart.match(regex);
          const nextKey = nextMatch ? nextMatch[1] : nextPart;
          const targetArray = nested[nextKey];
          if (Array.isArray(targetArray)) {
            const resolvedValue = targetArray[parseInt(index, 10)];
            // Skip the next segment since we processed it here
            i++;
            current = resolvedValue;
            continue;
          }
        }
      }
      
      current = current[key];
      
      if (index !== undefined) {
        if (current === null || current === undefined || !Array.isArray(current)) {
          return undefined;
        }
        current = current[parseInt(index, 10)];
      }
    }
    return current;
  }
}
