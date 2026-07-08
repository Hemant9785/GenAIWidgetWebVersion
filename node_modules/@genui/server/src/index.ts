// ============================================================
// GenUI Widget Platform - Express Server Entry Point
// ============================================================

import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import { OpenAIProvider } from './llm-provider.js';
import { WidgetOrchestrator } from './orchestrator.js';

// Load environment variables
dotenv.config();

const app = express();
const port = process.env.PORT || 3001;

app.use(cors());
app.use(express.json());

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

// Start Express server
app.listen(port, () => {
  console.log(`GenUI backend server running at http://localhost:${port}`);
});
