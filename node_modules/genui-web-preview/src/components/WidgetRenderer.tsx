import React from 'react';
import * as UI from './ui';
import { ComponentNode } from '../types';

// Map string keys to React components
const COMPONENT_REGISTRY: Record<string, React.ComponentType<any>> = {
  Card: UI.Card,
  Column: UI.Column,
  Row: UI.Row,
  Text: UI.Text,
  Image: UI.Image,
  List: UI.List,
  Badge: UI.Badge,
  Metric: UI.Metric,
  ProgressBar: UI.ProgressBar,
  MiniChart: UI.MiniChart,
  WeatherIcon: UI.WeatherIcon,
  Spacer: UI.Spacer,
};

interface WidgetRendererProps {
  node: ComponentNode;
}

export const WidgetRenderer: React.FC<WidgetRendererProps> = ({ node }) => {
  if (!node) return null;

  const Component = COMPONENT_REGISTRY[node.type];

  if (!Component) {
    console.warn(`WidgetRenderer: Unknown component type "${node.type}"`);
    return (
      <div style={{ padding: '8px', border: '1px dashed #ef4444', color: '#ef4444', fontSize: '12px' }}>
        Unknown component: {node.type}
      </div>
    );
  }

  // Special Handling for List component
  if (node.type === 'List') {
    const items = node.props?.items;
    const arrayItems = Array.isArray(items) ? items : [];
    
    // List expects children to be templates that are replicated for each item
    return (
      <Component {...(node.props || {})}>
        {arrayItems.map((item, index) => {
          if (!node.children || node.children.length === 0) return null;
          
          // Replicate each template child node for this item
          return node.children.map((child, childIdx) => {
            const resolvedChild = resolveItemPlaceholders(child, item, index);
            return (
              <WidgetRenderer
                key={`${index}-${childIdx}`}
                node={resolvedChild}
              />
            );
          });
        })}
      </Component>
    );
  }

  // Standard recursive rendering for other components
  return (
    <Component {...(node.props || {})}>
      {node.children?.map((child, index) => (
        <WidgetRenderer key={index} node={child} />
      ))}
    </Component>
  );
};

// --- Helper Functions for List Templating ---

function getValueByPath(obj: any, path: string): any {
  if (obj === null || obj === undefined) return '';
  const parts = path.split('.');
  let current = obj;
  for (const part of parts) {
    if (current === null || current === undefined) return '';
    current = current[part];
  }
  return current;
}

function resolveItemPlaceholders(node: ComponentNode, item: any, index: number): ComponentNode {
  if (!node) return node;

  const resolveValue = (val: any): any => {
    if (typeof val === 'string') {
      if (val === '$item') return item;
      if (val === '$index') return index;

      if (val.startsWith('$item.')) {
        const path = val.substring(6); // strip "$item."
        return getValueByPath(item, path);
      }

      // Inside string replacement (e.g. "Day $index" or "Temp: $item.temp")
      return val
        .replace(/\$item\.([a-zA-Z0-9_.]+)/g, (_, path) => {
          const res = getValueByPath(item, path);
          return res !== undefined && res !== null ? String(res) : '';
        })
        .replace(/\$index/g, String(index));
    } else if (Array.isArray(val)) {
      return val.map(resolveValue);
    } else if (val !== null && typeof val === 'object') {
      const copy: Record<string, any> = {};
      for (const [k, v] of Object.entries(val)) {
        copy[k] = resolveValue(v);
      }
      return copy;
    }
    return val;
  };

  return {
    type: node.type,
    props: node.props ? resolveValue(node.props) : undefined,
    children: node.children
      ? node.children.map(c => resolveItemPlaceholders(c, item, index))
      : undefined,
  };
}
