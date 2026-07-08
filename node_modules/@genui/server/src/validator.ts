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

  private validatePropsForType(
    type: string,
    props: Record<string, any>,
    path: string,
    errors: ValidationError[]
  ): void {
    const checkType = (key: string, expected: string) => {
      const val = props[key];
      if (val === undefined) return;
      if (expected === 'array' && !Array.isArray(val)) {
        errors.push({ path: `${path}.${key}`, message: `Prop "${key}" must be an array`, component_type: type });
      } else if (expected !== 'array' && typeof val !== expected) {
        errors.push({ path: `${path}.${key}`, message: `Prop "${key}" must be of type ${expected}`, component_type: type });
      }
    };

    switch (type) {
      case 'Card':
        checkType('padding', 'number');
        checkType('radius', 'number');
        checkType('background', 'string');
        checkType('border', 'string');
        checkType('shadow', 'string');
        break;

      case 'Column':
      case 'Row':
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
          errors.push({ path: `${path}.content`, message: 'Prop "content" is required for Text component', component_type: type });
        } else {
          // Can be string, number or boolean (coerced)
          if (typeof props.content === 'object') {
            errors.push({ path: `${path}.content`, message: 'Prop "content" cannot be an object', component_type: type });
          }
        }
        checkType('color', 'string');
        checkType('opacity', 'number');
        if (props.size && !['xs', 'sm', 'md', 'lg', 'xl', '2xl', '3xl'].includes(props.size)) {
          errors.push({ path: `${path}.size`, message: `Invalid size value: ${props.size}`, component_type: type });
        }
        if (props.weight && !['normal', 'medium', 'semibold', 'bold'].includes(props.weight)) {
          errors.push({ path: `${path}.weight`, message: `Invalid weight value: ${props.weight}`, component_type: type });
        }
        if (props.align && !['left', 'center', 'right'].includes(props.align)) {
          errors.push({ path: `${path}.align`, message: `Invalid align value: ${props.align}`, component_type: type });
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
          errors.push({ path: `${path}.text`, message: 'Prop "text" is required for Badge component', component_type: type });
        }
        checkType('color', 'string');
        checkType('background', 'string');
        if (props.size && !['sm', 'md'].includes(props.size)) {
          errors.push({ path: `${path}.size`, message: `Invalid size value: ${props.size}`, component_type: type });
        }
        break;

      case 'Metric':
        if (props.value === undefined) {
          errors.push({ path: `${path}.value`, message: 'Prop "value" is required for Metric component', component_type: type });
        }
        if (props.label === undefined) {
          errors.push({ path: `${path}.label`, message: 'Prop "label" is required for Metric component', component_type: type });
        }
        checkType('unit', 'string');
        checkType('valueColor', 'string');
        if (props.size && !['sm', 'md', 'lg'].includes(props.size)) {
          errors.push({ path: `${path}.size`, message: `Invalid size value: ${props.size}`, component_type: type });
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
        if (props.code === undefined) {
          errors.push({ path: `${path}.code`, message: 'Prop "code" is required for WeatherIcon component', component_type: type });
        }
        // code can be a number or string placeholder (placeholder starts with {{)
        if (typeof props.code !== 'number' && !(typeof props.code === 'string' && props.code.startsWith('{{'))) {
          errors.push({ path: `${path}.code`, message: 'Prop "code" must be a number', component_type: type });
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
