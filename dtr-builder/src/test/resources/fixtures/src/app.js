import { createServer } from 'http';
import express from 'express';

export class App {
  constructor(config) {
    this.config = config;
    this.server = null;
  }

  start() {
    const app = express();
    app.get('/health', (req, res) => res.json({ status: 'ok' }));
    this.server = app.listen(this.config.port);
  }

  stop() {
    if (this.server) {
      this.server.close();
    }
  }
}

export function createApp(config) {
  return new App(config);
}

const DEFAULT_PORT = 3000;
