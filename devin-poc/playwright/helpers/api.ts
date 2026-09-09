import {
  API_BASE,
  FINERACT_ADMIN_PASSWORD,
  SAVINGS_ID,
  TENANT_HEADER,
  TENANT_ID,
} from './config';

type Json = Record<string, unknown> | unknown[];

export type PendingCommand = {
  id: number;
  actionName: string;
  entityName: string;
  resourceId: number | null;
  savingsAccountNo: string | null;
  processingResult: string | null;
};

export async function apiGet(path: string, user: string, pass: string): Promise<Json> {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      Authorization: `Basic ${Buffer.from(`${user}:${pass}`).toString('base64')}`,
      Accept: 'application/json',
      [TENANT_HEADER]: TENANT_ID,
    },
  });
  const body = await response.text();
  if (!response.ok) {
    throw new Error(`GET ${path} failed with HTTP ${response.status}: ${body}`);
  }
  return body ? (JSON.parse(body) as Json) : {};
}

export async function getSavingsAccount(user = 'mifos', pass = FINERACT_ADMIN_PASSWORD): Promise<Record<string, any>> {
  return (await apiGet(`/savingsaccounts/${SAVINGS_ID}?associations=transactions`, user, pass)) as Record<string, any>;
}

export async function getSavingsBalance(
  savingsId = SAVINGS_ID,
  user = 'mifos',
  pass = FINERACT_ADMIN_PASSWORD,
): Promise<number> {
  const account = (await apiGet(`/savingsaccounts/${savingsId}?associations=transactions`, user, pass)) as Record<
    string,
    any
  >;
  return Number(account.summary?.accountBalance);
}

export async function getSavingsTransactions(
  savingsId = SAVINGS_ID,
  user = 'mifos',
  pass = FINERACT_ADMIN_PASSWORD,
): Promise<Record<string, any>[]> {
  const account = (await apiGet(`/savingsaccounts/${savingsId}?associations=transactions`, user, pass)) as Record<
    string,
    any
  >;
  return account.transactions ?? [];
}

export async function getPendingCommands(user = 'mifos', pass = FINERACT_ADMIN_PASSWORD): Promise<PendingCommand[]> {
  const commands = (await apiGet('/makercheckers?includeJson=false', user, pass)) as PendingCommand[];
  return commands.filter((command) => command.processingResult?.toLowerCase().replace(/\s+/g, '.') === 'awaiting.approval');
}
