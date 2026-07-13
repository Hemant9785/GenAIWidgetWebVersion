// ============================================================
// GenUI Widget Platform - UI JSON Validator
// Checks schema, component types, and props constraints
// ============================================================

import type { UIValidator, ValidationResult, ValidationError, UIComponent } from './types.js';

const VALID_COMPONENTS = new Set([
  'Card',
  'Column',
  'Row',
  'Text',
  'Image',
  'List',
  'Badge',
  'Metric',
  'ProgressBar',
  'MiniChart',
  'WeatherIcon',
  'Spacer',
]);

export class StrictUIValidator implements UIValidator {
  validate(uiJson: any): ValidationResult {
    const errors: ValidationError[] = [];

    if (!uiJson || typeof uiJson !== 'object') {
      errors.push({
        path: 'root',
        message: 'UI JSON must be a non-null object',
      });
      return { valid: false, errors, warnings: [] };
    }

    this.normalize(uiJson);

    this.validateNode(uiJson, 'root', errors);

    return {
      valid: errors.length === 0,
      errors,
      warnings: [],
    };
  }

  private normalize(node: any): void {
    if (!node || typeof node !== 'object') return;

    if (node.children !== undefined && node.children !== null) {
      if (!Array.isArray(node.children)) {
        if (typeof node.children === 'object') {
          node.children = [node.children];
        } else {
          node.children = [];
        }
      }
    }

    if (node.props === undefined || node.props === null) {
      node.props = {};
    }

    if (Array.isArray(node.children)) {
      node.children.forEach((child: any) => this.normalize(child));
    }
  }

  private validateNode(node: any, path: string, errors: ValidationError[]): void {
    if (!node || typeof node !== 'object') {
      errors.push({
        path,
        message: 'Node must be an object',
      });
      return;
    }

    if (typeof node.type !== 'string') {
      errors.push({
        path,
        message: 'Node must have a string "type"',
      });
      return;
    }

    const type = node.type;
    if (!VALID_COMPONENTS.has(type)) {
      errors.push({
        path,
        message: `Unknown component type: "${type}"`,
        component_type: type,
      });
      return;
    }

    // Validate props
    const props = node.props || {};
    if (typeof props !== 'object' || Array.isArray(props)) {
      errors.push({
        path: `${path}.props`,
        message: 'Props must be an object',
        component_type: type,
      });
    } else {
      this.validatePropsForType(type, props, `${path}.props`, errors);
    }

    // Validate children
    if (node.children !== undefined) {
      if (!Array.isArray(node.children)) {
        errors.push({
          path: `${path}.children`,
          message: 'Children must be an array',
          component_type: type,
        });
      } else {
        node.children.forEach((child: any, idx: number) => {
          this.validateNode(child, `${path}.children[${idx}]`, errors);
        });
      }
    }
  }
  private isPlaceholder(val: any): boolean {
    if (typeof val !== 'string') return false;
    return val.includes('{{') || val.includes('$item') || val.includes('$index');
  }

  private validatePropsForType(
    type: string,
    props: Record<string, any>,
    path: string,
    errors: ValidationError[]
  ): void {
    const checkType = (key: string, expected: string) => {
      let val = props[key];
      if (val === undefined || val === null) return;
      if (this.isPlaceholder(val)) return;

      // Coerce numeric strings
      if (expected === 'number' && typeof val === 'string') {
        const num = Number(val);
        if (!isNaN(num)) {
          props[key] = num;
          val = num;
        }
      }

      // Coerce boolean strings
      if (expected === 'boolean' && typeof val === 'string') {
        const lower = val.toLowerCase();
        if (lower === 'true') {
          props[key] = true;
          val = true;
        } else if (lower === 'false') {
          props[key] = false;
          val = false;
        }
      }

      if (expected === 'array' && !Array.isArray(val)) {
        errors.push({ path: `${path}.${key}`, message: `Prop "${key}" must be an array`, component_type: type });
      } else if (expected !== 'array' && typeof val !== expected) {
        errors.push({ path: `${path}.${key}`, message: `Prop "${key}" must be of type ${expected}`, component_type: type });
      }
    };

    switch (type) {
      case 'Card':
        if (props.padding === undefined) props.padding = 16;
        if (props.radius === undefined) props.radius = 16;
        checkType('padding', 'number');
        checkType('radius', 'number');
        checkType('background', 'string');
        checkType('border', 'string');
        checkType('shadow', 'string');
        break;

      case 'Column':
      case 'Row':
        if (props.gap === undefined) props.gap = 8;
        checkType('gap', 'number');
        checkType('padding', 'number');
        if (props.align && !['start', 'center', 'end', 'stretch'].includes(props.align)) {
          errors.push({ path: `${path}.align`, message: `Invalid align value: ${props.align}`, component_type: type });
        }
        if (props.justify && !['start', 'center', 'end', 'between', 'around'].includes(props.justify)) {
          errors.push({ path: `${path}.justify`, message: `Invalid justify value: ${props.justify}`, component_type: type });
        }
        if (type === 'Row') {
          checkType('wrap', 'boolean');
        }
        break;

      case 'Text':
        if (props.content === undefined) {
          props.content = '';
        } else {
          if (typeof props.content === 'object') {
            errors.push({ path: `${path}.content`, message: 'Prop "content" cannot be an object', component_type: type });
          }
        }
        checkType('color', 'string');
        checkType('opacity', 'number');
        if (props.size && !['xs', 'sm', 'md', 'lg', 'xl', '2xl', '3xl'].includes(props.size)) {
          props.size = 'md';
        }
        if (props.weight && !['normal', 'medium', 'semibold', 'bold'].includes(props.weight)) {
          props.weight = 'normal';
        }
        if (props.align && !['left', 'center', 'right'].includes(props.align)) {
          props.align = 'left';
        }
        break;

      case 'Image':
        if (!props.src) {
          errors.push({ path: `${path}.src`, message: 'Prop "src" is required for Image component', component_type: type });
        }
        checkType('radius', 'number');
        if (props.fit && !['cover', 'contain', 'fill'].includes(props.fit)) {
          errors.push({ path: `${path}.fit`, message: `Invalid fit value: ${props.fit}`, component_type: type });
        }
        break;

      case 'List':
        if (props.items === undefined) {
          errors.push({ path: `${path}.items`, message: 'Prop "items" is required for List component', component_type: type });
        }
        checkType('gap', 'number');
        if (props.direction && !['vertical', 'horizontal'].includes(props.direction)) {
          errors.push({ path: `${path}.direction`, message: `Invalid direction value: ${props.direction}`, component_type: type });
        }
        break;

      case 'Badge':
        if (props.text === undefined) {
          props.text = '';
        }
        checkType('color', 'string');
        checkType('background', 'string');
        if (props.size && !['sm', 'md'].includes(props.size)) {
          props.size = 'sm';
        }
        break;

      case 'Metric':
        if (props.value === undefined) {
          errors.push({ path: `${path}.value`, message: 'Prop "value" is required for Metric component', component_type: type });
        }
        if (props.label === undefined) {
          props.label = '';
        }
        checkType('unit', 'string');
        checkType('valueColor', 'string');
        if (props.size && !['sm', 'md', 'lg'].includes(props.size)) {
          props.size = 'md';
        }
        break;

      case 'ProgressBar':
        if (props.value === undefined) {
          errors.push({ path: `${path}.value`, message: 'Prop "value" is required for ProgressBar component', component_type: type });
        }
        checkType('value', 'number');
        checkType('max', 'number');
        checkType('color', 'string');
        checkType('height', 'number');
        checkType('label', 'string');
        checkType('showValue', 'boolean');
        break;

      case 'MiniChart':
        if (props.data === undefined) {
          errors.push({ path: `${path}.data`, message: 'Prop "data" is required for MiniChart component', component_type: type });
        }
        checkType('data', 'array');
        checkType('color', 'string');
        checkType('height', 'number');
        checkType('labels', 'array');
        if (props.type && !['line', 'bar'].includes(props.type)) {
          errors.push({ path: `${path}.type`, message: `Invalid type value: ${props.type}`, component_type: type });
        }
        break;

      case 'WeatherIcon':
        if (props.code === undefined || props.code === null) {
          props.code = 0;
        } else if (typeof props.code === 'string' && !this.isPlaceholder(props.code)) {
          const num = Number(props.code);
          if (!isNaN(num)) {
            props.code = num;
          }
        }
        
        if (!this.isPlaceholder(props.code) && typeof props.code !== 'number') {
          props.code = 0;
        }

        if (props.isDay === undefined || props.isDay === null) {
          props.isDay = true;
        } else if (typeof props.isDay === 'string' && !this.isPlaceholder(props.isDay)) {
          const lower = props.isDay.toLowerCase();
          if (lower === 'true') props.isDay = true;
          else if (lower === 'false') props.isDay = false;
        }

        if (!this.isPlaceholder(props.isDay) && typeof props.isDay !== 'boolean') {
          props.isDay = true;
        }

        checkType('size', 'number');
        checkType('isDay', 'boolean');
        break;

      case 'Spacer':
        checkType('size', 'number');
        break;
    }
  }
}
