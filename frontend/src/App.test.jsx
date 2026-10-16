import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';

const account = { id: 'account-01', company: 'Acme Studio', billingEmail: 'billing@acme.example', plan: 'GROWTH', status: 'ACTIVE', monthlyRate: '149.00', version: 3, syncStatus: 'SYNCED' };
const overview = { totalAccounts: 1, activeAccounts: 1, monthlyRevenue: '149.00', syncedAccounts: 1, pendingEvents: 0, failedEvents: 0, deliveredEvents: 1, demo: true, platformAvailable: true };
const calls = [];
beforeEach(() => {
  calls.length = 0;
  vi.stubGlobal('fetch', vi.fn(async (path, options) => {
    calls.push({ path, options });
    const data = path === '/api/overview' ? overview : path === '/api/events' ? [] : path === '/api/accounts' && options?.method !== 'POST' ? [account] : account;
    return { ok: true, json: async () => data };
  }));
});

describe('Billing workspace', () => {
  it('loads live accounts and filters the account table', async () => {
    const user = userEvent.setup({ delay: null }); render(<App/>);
    expect(await screen.findByText('Acme Studio')).toBeInTheDocument();
    await user.type(screen.getByLabelText('Search accounts'), 'missing');
    expect(screen.getByText('No matching accounts')).toBeInTheDocument();
    await user.click(screen.getByLabelText('Clear search'));
    expect(screen.getByText('Acme Studio')).toBeInTheDocument();
  });
  it('creates an account with a stable idempotency key', async () => {
    const user = userEvent.setup({ delay: null }); render(<App/>);
    await screen.findByText('Acme Studio');
    await user.click(screen.getByRole('button', { name: 'New account' }));
    const dialog = screen.getByRole('dialog');
    await user.type(within(dialog).getByLabelText('Company name'), 'New Company');
    await user.type(within(dialog).getByLabelText('Billing email'), 'finance@new.example');
    await user.click(within(dialog).getByRole('button', { name: 'Create account' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    const post = calls.find((call) => call.options?.method === 'POST');
    expect(post.options.headers['Idempotency-Key']).toMatch(/^[\w-]{36}$/);
    expect(JSON.parse(post.options.body)).toEqual({ company: 'New Company', billingEmail: 'finance@new.example', plan: 'GROWTH' });
  });
  it('submits the account version when editing', async () => {
    const user = userEvent.setup({ delay: null }); render(<App/>);
    await user.click(await screen.findByText('Acme Studio'));
    const dialog = screen.getByRole('dialog');
    await user.selectOptions(within(dialog).getByLabelText('Subscription plan'), 'ENTERPRISE');
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    await waitFor(() => expect(calls.some((call) => call.options?.method === 'PUT')).toBe(true));
    const put = calls.find((call) => call.options?.method === 'PUT');
    expect(JSON.parse(put.options.body)).toMatchObject({ version: 3, plan: 'ENTERPRISE' });
  });
  it('keeps the form and key available after a failed create', async () => {
    const user = userEvent.setup({ delay: null });
    const baseFetch = fetch;
    let failedKey;
    vi.stubGlobal('fetch', vi.fn(async (path, options) => {
      if (options?.method === 'POST') { failedKey ||= options.headers['Idempotency-Key']; calls.push({ path, options }); return { ok: false, json: async () => ({ detail: 'Service unavailable' }) }; }
      return baseFetch(path, options);
    }));
    render(<App/>); await screen.findByText('Acme Studio');
    await user.click(screen.getByRole('button', { name: 'New account' }));
    await user.type(screen.getByLabelText('Company name'), 'New Company');
    await user.type(screen.getByLabelText('Billing email'), 'finance@new.example');
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Service unavailable');
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    const posts = calls.filter((call) => call.options?.method === 'POST');
    expect(posts).toHaveLength(2);
    expect(posts.every((call) => call.options.headers['Idempotency-Key'] === failedKey)).toBe(true);
  });
});
