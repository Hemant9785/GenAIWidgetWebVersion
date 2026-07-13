// ============================================================
// GenUI Widget Platform - Express Server Entry Point
// ============================================================

import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { OpenAIProvider } from './llm-provider.js';
import { WidgetOrchestrator } from './orchestrator.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Load environment variables from the server package when running locally.
// In production, the hosting platform injects environment variables directly.
dotenv.config({ path: path.resolve(__dirname, '../.env') });

const app = express();
const port = Number(process.env.PORT) || 3001;
const frontendDist = path.resolve(__dirname, '../../../apps/web-preview/dist');

app.use(cors());
app.use(express.json());
app.use(express.static(frontendDist));

// Initialize Orchestrator and LLM Provider
const apiKey = process.env.OPENAI_API_KEY;
if (!apiKey) {
  console.warn('WARNING: OPENAI_API_KEY is not defined in the environment. LLM calls will fail.');
}

const llmProvider = new OpenAIProvider(apiKey);
const orchestrator = new WidgetOrchestrator(llmProvider);

// Perform registry discovery at startup
console.log('Initializing widget orchestrator...');
orchestrator.init()
  .then(() => console.log('Orchestrator initialized and plugins loaded.'))
  .catch(err => console.error('Failed to initialize orchestrator:', err));

// --- API Endpoints ---

// Health check
app.get('/api/health', (req, res) => {
  res.json({
    status: 'ok',
    timestamp: new Date().toISOString(),
    pluginsLoaded: true, // Mock indicator
  });
});

// Generation endpoint
app.post('/api/generate', async (req, res) => {
  const { query } = req.body;

  if (!query || typeof query !== 'string' || query.trim() === '') {
    res.status(400).json({ error: 'Query parameter is required and must be a non-empty string.' });
    return;
  }

  console.log(`Received widget generation query: "${query}"`);

  try {
    const result = await orchestrator.generateWidget(query);
    
    if (!result.success) {
      res.status(422).json({
        error: result.error,
        debug: result.debug,
      });
      return;
    }

    res.json({
      widget: result.ui,
      debug: result.debug,
    });
  } catch (err: any) {
    console.error('API execution error:', err);
    res.status(500).json({
      error: err.message || 'An internal server error occurred while processing your request.',
    });
  }
});

// Serve the Vite single-page application in production. Keep unknown API
// routes as API 404s instead of returning the frontend HTML document.
app.get('*', (req, res, next) => {
  if (req.path.startsWith('/api/')) {
    next();
    return;
  }

  res.sendFile(path.join(frontendDist, 'index.html'), err => {
    if (err) next(err);
  });
});

// Start Express server
app.listen(port, '0.0.0.0', () => {
  console.log(`GenUI server running on port ${port}`);
});
