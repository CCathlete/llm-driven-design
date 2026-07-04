import { readFileSync, writeFileSync } from 'fs';
import { join } from 'path';

export interface Config {
  host: string;
  port: number;
  debug: boolean;
}

export type JsonValue = string | number | boolean | null | JsonValue[] | { [key: string]: JsonValue };

export class ConfigLoader {
  static load(path: string): Config {
    const content = readFileSync(path, 'utf-8');
    return JSON.parse(content) as Config;
  }

  static save(config: Config, path: string): void {
    writeFileSync(path, JSON.stringify(config, null, 2));
  }
}

export enum Environment {
  Development = 'development',
  Production = 'production',
  Test = 'test'
}
