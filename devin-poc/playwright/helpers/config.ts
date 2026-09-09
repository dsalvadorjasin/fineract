import fs from 'node:fs';
import path from 'node:path';

const requiredEnv = (name: string): string => {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
};

export const FINERACT_ADMIN_PASSWORD = requiredEnv('FINERACT_ADMIN_PASSWORD');
export const POC_MAKER_PASSWORD = requiredEnv('POC_MAKER_PASSWORD');
export const POC_CHECKER_PASSWORD = requiredEnv('POC_CHECKER_PASSWORD');

const idsPath = path.resolve(__dirname, '../../seed/out/ids.env');
if (!fs.existsSync(idsPath)) {
  throw new Error(`Missing seeded ids file: ${idsPath}; run devin-poc/seed/seed.sh first`);
}

const ids = Object.fromEntries(
  fs
    .readFileSync(idsPath, 'utf8')
    .split(/\r?\n/)
    .filter((line) => line && !line.startsWith('#'))
    .map((line) => {
      const separator = line.indexOf('=');
      if (separator < 1) {
        throw new Error(`Invalid ids.env line: ${line}`);
      }
      return [line.slice(0, separator), line.slice(separator + 1)];
    }),
);

const requiredId = (name: string): string => {
  const value = ids[name];
  if (!value) {
    throw new Error(`Missing ${name} in ${idsPath}`);
  }
  return value;
};

export const CLIENT_ID = requiredId('CLIENT_ID');
export const SAVINGS_ID = requiredId('SAVINGS_ID');
export const SAVINGS_ACCOUNT_NO = requiredId('SAVINGS_ACCOUNT_NO');
export const MAKER_USER = requiredId('MAKER_USER');
export const CHECKER_USER = requiredId('CHECKER_USER');
export const API_BASE = 'http://localhost:8080/fineract-provider/api/v1';
export const TENANT_HEADER = 'Fineract-Platform-TenantId';
export const TENANT_ID = 'default';
