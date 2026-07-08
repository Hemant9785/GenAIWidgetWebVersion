// ============================================================
// GenUI Widget Platform - UI Component Catalog
// Defines canonical UI JSON components for layout generation
// ============================================================

export const COMPONENT_CATALOG = `GENUI WIDGET COMPONENT CATALOG

You are only allowed to use components from this catalog. Do not introduce any custom components, and do not use generic HTML tags. All interactive or rendering structures must be composed of these building blocks.

1. Card
   - Description: Container with background, padding, rounded corners, and shadow. Used to wrap sections or the entire widget.
   - Props:
     - padding: number (padding in px. Default: 16)
     - radius: number (border radius in px. Default: 16)
     - background: string (hex/rgba color or gradient. Default: "rgba(26, 26, 46, 0.8)")
     - border: string (CSS border style, e.g. "1px solid rgba(255,255,255,0.06)")
     - shadow: string (CSS box-shadow)
   - Children: Yes

2. Column
   - Description: Vertical flex container.
   - Props:
     - gap: number (space between children in px. Default: 8)
     - align: "start" | "center" | "end" | "stretch" (Default: "stretch")
     - justify: "start" | "center" | "end" | "between" | "around" (Default: "start")
     - padding: number (padding in px. Default: 0)
   - Children: Yes

3. Row
   - Description: Horizontal flex container.
   - Props:
     - gap: number (space between children in px. Default: 8)
     - align: "start" | "center" | "end" | "stretch" (Default: "center")
     - justify: "start" | "center" | "end" | "between" | "around" (Default: "start")
     - wrap: boolean (allow wrap. Default: false)
     - padding: number (padding in px. Default: 0)
   - Children: Yes

4. Text
   - Description: Display a text label.
   - Props:
     - content: string (the text to show. REQUIRED)
     - size: "xs" | "sm" | "md" | "lg" | "xl" | "2xl" | "3xl" (Font size key. Default: "md")
     - weight: "normal" | "medium" | "semibold" | "bold" (Font weight key. Default: "normal")
     - color: string (CSS color, e.g., "#e8e8f0", "#9898b0". Default: "#e8e8f0")
     - align: "left" | "center" | "right" (Default: "left")
     - opacity: number (0.0 to 1.0. Default: 1.0)
   - Children: No

5. Image
   - Description: Displays an image from a URL.
   - Props:
     - src: string (image URL. REQUIRED)
     - width: number | string (width in px or percentage)
     - height: number | string (height in px or percentage)
     - fit: "cover" | "contain" | "fill" (CSS object-fit. Default: "cover")
     - radius: number (border radius in px)
   - Children: No

6. List
   - Description: Renders a list of items using a template component.
   - Props:
     - items: array (unresolved array of objects. Use variable references like "{{dailyForecast.time}}". REQUIRED)
     - direction: "vertical" | "horizontal" (Default: "vertical")
     - gap: number (space between items in px. Default: 8)
   - Children: Yes (A single child node representing the template. The template can reference list item values using "$item" or index variables like "$index", or dotted access "$item.temperature" if the items are objects).

7. Badge
   - Description: Small pill-shaped tag/label.
   - Props:
     - text: string (REQUIRED)
     - color: string (text color. Default: "#ffffff")
     - background: string (badge background color. Default: "#7c3aed")
     - size: "sm" | "md" (Default: "md")
   - Children: No

8. Metric
   - Description: Displays a large value/metric with a descriptive label below it.
   - Props:
     - value: string | number (large value, e.g., "28°". REQUIRED)
     - unit: string (optional unit displayed smaller next to value, e.g., "%", "km/h")
     - label: string (descriptive label below. REQUIRED)
     - size: "sm" | "md" | "lg" (Default: "md")
     - valueColor: string (color of the large value text)
   - Children: No

9. ProgressBar
   - Description: Horizontal progress indicator.
   - Props:
     - value: number (current progress. REQUIRED)
     - max: number (max progress value. Default: 100)
     - color: string (bar color. Default: "#7c3aed")
     - height: number (height of the progress bar in px. Default: 8)
     - label: string (optional label to display next to or above bar)
     - showValue: boolean (display percentage text. Default: false)
   - Children: No

10. MiniChart
    - Description: Simple sparkline or column chart.
    - Props:
      - data: number[] (array of numbers to chart. REQUIRED)
      - type: "line" | "bar" (Default: "bar")
      - color: string (chart color. Default: "#3b82f6")
      - height: number (height of the chart container in px. Default: 40)
      - labels: string[] (optional labels for X axis)
    - Children: No

11. WeatherIcon
    - Description: Special dynamic component that maps WMO weather codes to a weather emoji or icon.
    - Props:
      - code: number (WMO weather code, e.g. 0 for clear, 3 for overcast. REQUIRED)
      - size: number (size in px. Default: 24)
      - isDay: boolean (if day or night variant should be displayed. Default: true)
    - Children: No

12. Spacer
    - Description: Blank space for custom layouts.
    - Props:
      - size: number (height/width in px. Default: 16)
    - Children: No

JSON EXAMPLES:

Example 1: Current Weather Card
{
  "type": "Card",
  "props": { "padding": 20, "radius": 24 },
  "children": [
    {
      "type": "Column",
      "props": { "gap": 12, "align": "center" },
      "children": [
        { "type": "Text", "props": { "content": "{{location.name}}", "size": "xl", "weight": "bold" } },
        { "type": "Row", "props": { "gap": 8, "align": "center" }, "children": [
          { "type": "WeatherIcon", "props": { "code": "{{currentWeather.current.weather_code}}", "size": 48 } },
          { "type": "Metric", "props": { "value": "{{currentWeather.current.temperature_2m}}", "unit": "°C", "label": "Temperature" } }
        ] }
      ]
    }
  ]
}
`;
