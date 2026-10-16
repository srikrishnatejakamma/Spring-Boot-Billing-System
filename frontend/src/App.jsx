import { useCallback, useEffect, useRef, useState } from 'react';
import { Activity, ArrowDownLeft, ArrowRight, ArrowUpRight, Bell, BookOpen, Check, CheckCheck, CheckCircle2, ChevronDown, ChevronRight, CircleHelp, CircleX, Clock3, Database, ExternalLink, GitBranch, Layers3, LayoutDashboard, ListFilter, LoaderCircle, Menu, Plus, Radio, RefreshCw, Search, Server, ShieldCheck, Sparkles, Users, Wallet, X, Zap } from 'lucide-react';
import { request, money, label, timestamp } from './api';

const navigation = [
  { id: 'overview', name: 'Overview', icon: LayoutDashboard },
  { id: 'accounts', name: 'Billing accounts', icon: Wallet },
  { id: 'events', name: 'Event stream', icon: Activity },
  { id: 'reliability', name: 'Reliability', icon: ShieldCheck },
];
const planRates = { STARTER: '49.00', GROWTH: '149.00', ENTERPRISE: '499.00' };
const initials = (name) => name.split(/\s+/).slice(0, 2).map((word) => word[0]).join('');

function Badge({ status }) {
  return <span className={`badge ${status.toLowerCase()}`}><span className="status-dot" />{label(status)}</span>;
}

export default function App() {
  const [page, setPage] = useState('overview');
  const [accounts, setAccounts] = useState([]);
  const [events, setEvents] = useState([]);
  const [overview, setOverview] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('ALL');
  const [eventStatus, setEventStatus] = useState('ALL');
  const [editing, setEditing] = useState(null);
  const [notice, setNotice] = useState('');
  const [mobileMenu, setMobileMenu] = useState(false);
  const [busy, setBusy] = useState('');
  const [refreshedAt, setRefreshedAt] = useState(null);
  const requestSequence = useRef(0);

  const refresh = useCallback(async () => {
    const sequence = ++requestSequence.current;
    try {
      const [nextAccounts, nextEvents, nextOverview] = await Promise.all([
        request('/api/accounts'), request('/api/events'), request('/api/overview'),
      ]);
      if (sequence !== requestSequence.current) return;
      setAccounts(nextAccounts); setEvents(nextEvents); setOverview(nextOverview);
      setError(''); setRefreshedAt(new Date());
    } catch (exception) {
      if (sequence === requestSequence.current) setError(exception.message);
    } finally { if (sequence === requestSequence.current) setLoading(false); }
  }, []);

  useEffect(() => { refresh(); const timer = setInterval(refresh, 5000); return () => { clearInterval(timer); requestSequence.current++; }; }, [refresh]);
  useEffect(() => { if (!notice) return; const timer = setTimeout(() => setNotice(''), 5000); return () => clearTimeout(timer); }, [notice]);

  function navigate(nextPage) { setPage(nextPage); setSearch(''); setMobileMenu(false); }
  async function retry(id) {
    setBusy(id);
    try { await request(`/api/events/${id}/retry`, { method: 'POST' }); setNotice('Event queued for another delivery attempt.'); await refresh(); }
    catch (exception) { setNotice(exception.message); }
    finally { setBusy(''); }
  }
  async function togglePlatform() {
    setBusy('platform');
    try {
      const available = !overview.platformAvailable;
      await request('/api/demo/platform', { method: 'PUT', body: JSON.stringify({ available }) });
      setNotice(available ? 'Platform restored. Pending events will retry automatically.' : 'Demo outage started. Account changes will queue safely.');
      await refresh();
    } catch (exception) { setNotice(exception.message); }
    finally { setBusy(''); }
  }

  const filteredAccounts = accounts.filter((account) =>
    (status === 'ALL' || account.status === status) && `${account.company} ${account.billingEmail}`.toLowerCase().includes(search.toLowerCase()));
  const accountNames = Object.fromEntries(accounts.map((account) => [account.id, account.company]));
  const filteredEvents = events.filter((event) => (eventStatus === 'ALL' || event.status === eventStatus)
    && `${event.id} ${event.type} ${accountNames[event.accountId] || ''}`.toLowerCase().includes(search.toLowerCase()));
  const delivered = overview?.deliveredEvents || 0;
  const allEvents = delivered + (overview?.pendingEvents || 0) + (overview?.failedEvents || 0);
  const deliveryRate = allEvents ? `${((delivered / allEvents) * 100).toFixed(1)}%` : '—';

  return <div className="app-shell">
    {mobileMenu && <button className="sidebar-scrim" aria-label="Close navigation" onClick={() => setMobileMenu(false)} />}
    <aside className={`sidebar ${mobileMenu ? 'open' : ''}`}>
      <a className="brand" href="#" onClick={(event) => { event.preventDefault(); navigate('overview'); }}><span className="brand-mark"><Layers3 size={23}/></span>ledger<span className="brand-period">.</span></a>
      <div className="workspace"><div className="workspace-icon">AC</div><div><strong>Acme workspace</strong><span>Billing operations</span></div><ChevronDown size={14}/></div>
      <div className="nav-caption">WORKSPACE</div>
      <nav aria-label="Main navigation">{navigation.map(({ id, name, icon: Icon }) => <button key={id} className={`nav-item ${page === id ? 'selected' : ''}`} onClick={() => navigate(id)}><Icon size={18}/><span>{name}</span>{id === 'events' && (overview?.pendingEvents || 0) > 0 && <span className="nav-count">{overview.pendingEvents}</span>}</button>)}</nav>
      <div className="sidebar-bottom">
        <div className="sidebar-note"><span className="note-icon"><ShieldCheck size={19}/></span><strong>Correctness comes first.</strong><p>Every change is versioned.<br/>Every event is accounted for.</p><button onClick={() => navigate('reliability')}>Explore reliability <ArrowUpRight size={14}/></button></div>
        <a className="nav-item secondary" href="/graphiql" target="_blank" rel="noreferrer"><BookOpen size={17}/>GraphQL explorer<ExternalLink size={13}/></a>
        <div className="profile"><span className="profile-avatar">BO</span><div><strong>Billing operator</strong><span>Local workspace</span></div><span className="online-dot"/></div>
      </div>
    </aside>

    <div className="main-shell">
      <header className="topbar"><div className="breadcrumb"><button className="icon-button mobile-toggle" aria-label="Open navigation" onClick={() => setMobileMenu(true)}><Menu size={20}/></button><span>Workspace</span><ChevronRight size={13}/><strong>{navigation.find((item) => item.id === page).name}</strong></div><div className="topbar-right"><span className="environment"><span/>{overview?.demo === false ? 'Distributed' : 'Local demo'}</span><span className="topbar-divider"/><button className="icon-button" title="View events needing attention" aria-label="View events needing attention" onClick={() => { navigate('events'); setEventStatus('RETRYING'); }}><Bell size={18}/>{(overview?.pendingEvents || 0) > 0 && <i/>}</button><span className="top-avatar">BO</span></div></header>
      <main>
        <div className="page-heading"><div><div className="eyebrow">BILLING CONTROL CENTER</div><h1>{page === 'overview' ? 'A clear view of your billing.' : page === 'accounts' ? 'Every account, in one place.' : page === 'events' ? 'Follow every change.' : 'Built to stay in sync.'}</h1><p>{page === 'overview' ? 'Your accounts, revenue, and sync health. All accounted for.' : page === 'accounts' ? 'Manage account details, subscription plans, and lifecycle changes.' : page === 'events' ? 'Trace account changes from transaction to confirmed delivery.' : 'Inspect delivery guarantees and put recovery to the test.'}</p></div><div className="heading-actions"><button className="button light refresh-button" onClick={refresh} disabled={loading}><RefreshCw size={15} className={loading ? 'spin' : ''}/><span>Refresh</span></button><button className="button primary" onClick={() => setEditing({ isNew: true })}><Plus size={17}/>New account</button></div></div>

        {error && <div className="error-banner" role="alert"><CircleX size={18}/><div><strong>Unable to reach the billing service</strong><p>{error}. Check that the Spring Boot backend is running on port 8080.</p></div><button className="button light" onClick={refresh}>Try again</button></div>}
        {!error && overview?.demo && <div className="demo-banner"><Sparkles size={15}/><span><strong>Your billing sandbox.</strong> Explore with sample accounts, or create your own.</span><button onClick={() => navigate('reliability')}>Test a recovery <ArrowRight size={14}/></button></div>}

        {loading && !overview ? <div className="loading-state"><LoaderCircle className="spin" size={28}/><h3>Connecting your workspace</h3><p>Loading accounts and delivery activity…</p></div> : <>
          {(page === 'overview' || page === 'reliability') && <div className="metrics-grid">
            <Metric title="Monthly recurring revenue" value={money(overview?.monthlyRevenue)} icon={Wallet} description="Active subscriptions · USD" accent/>
            <Metric title="Active accounts" value={overview?.activeAccounts || 0} icon={Users} description={`${overview?.totalAccounts || 0} total billing accounts`}/>
            <Metric title="Delivery completion" value={deliveryRate} icon={CheckCheck} description={`${delivered} confirmed events`}/>
            <Metric title="Awaiting delivery" value={overview?.pendingEvents || 0} icon={Radio} description={`${overview?.failedEvents || 0} events in dead letter`} warning={(overview?.failedEvents || 0) > 0}/>
          </div>}

          {page === 'overview' && <div className="overview-panels">
            <section className="panel revenue-panel"><div className="panel-heading"><div><h2>Your revenue mix</h2><p>Monthly contribution by active plan</p></div><span className="small-chip">USD / month</span></div><RevenueMix accounts={accounts}/></section>
            <section className="panel sync-panel"><div className="panel-heading"><div><h2>Account sync health</h2><p>One source of truth. Connected systems.</p></div><span className="health-icon"><ShieldCheck size={21}/></span></div><div className="sync-summary"><strong>{overview?.syncedAccounts || 0}<span> / {overview?.totalAccounts || 0}</span></strong><span>accounts synchronized</span></div><div className="sync-progress"><span style={{ width: `${overview?.totalAccounts ? overview.syncedAccounts / overview.totalAccounts * 100 : 0}%` }}/></div><div className="sync-endpoints"><span><Database size={15}/>{overview?.demo ? 'H2 ledger' : 'MySQL ledger'}</span><ArrowRight size={14}/><span><GitBranch size={15}/>{overview?.demo ? 'Demo projection' : 'MongoDB + platform'}</span></div><button className="text-button" onClick={() => navigate('reliability')}>View reliability workspace <ArrowUpRight size={14}/></button></section>
          </div>}

          {(page === 'overview' || page === 'accounts') && <section className="panel account-panel"><div className="panel-heading"><div><h2>Billing accounts <span className="count-chip">{accounts.length}</span></h2><p>{page === 'overview' ? 'The latest accounts across your workspace' : 'Changes are versioned and synchronized automatically'}</p></div>{page === 'overview' && <button className="text-button" onClick={() => navigate('accounts')}>View all accounts <ArrowRight size={15}/></button>}</div><div className="table-toolbar"><label className="search-input"><Search size={16}/><input aria-label="Search accounts" placeholder="Search accounts or email…" value={search} onChange={(event) => setSearch(event.target.value)}/>{search && <button aria-label="Clear search" onClick={() => setSearch('')}><X size={14}/></button>}</label><label className="filter-select"><ListFilter size={15}/><select aria-label="Filter account status" value={status} onChange={(event) => setStatus(event.target.value)}><option value="ALL">All statuses</option><option value="ACTIVE">Active</option><option value="SUSPENDED">Suspended</option><option value="CLOSED">Closed</option></select></label></div>
            <div className="table-scroll"><table><thead><tr><th>Account</th><th>Plan</th><th>Monthly rate</th><th>Status</th><th>Sync state</th><th aria-label="Account actions"/></tr></thead><tbody>{(page === 'overview' ? filteredAccounts.slice(0, 6) : filteredAccounts).map((account, index) => <tr key={account.id}><td><button className="account-cell" onClick={() => setEditing(account)}><span className={`company-avatar color-${index % 5}`}>{initials(account.company)}</span><span><strong>{account.company}</strong><small>{account.billingEmail}</small></span></button></td><td><span className={`plan-tag ${account.plan.toLowerCase()}`}>{label(account.plan)}</span></td><td className="money-cell">{money(account.monthlyRate)}<span> / mo</span></td><td><Badge status={account.status}/></td><td><span className={`sync-state ${account.syncStatus.toLowerCase()}`}>{account.syncStatus === 'SYNCED' ? <CheckCheck size={15}/> : <Clock3 size={14}/>} {label(account.syncStatus)}</span></td><td><button className="icon-button row-action" aria-label={`View ${account.company}`} onClick={() => setEditing(account)}><ArrowUpRight size={16}/></button></td></tr>)}</tbody></table></div>
            {filteredAccounts.length === 0 && <Empty icon={Search} title={accounts.length ? 'No matching accounts' : 'Your first account starts here'} description={accounts.length ? 'Try a different search or status filter.' : 'Create an account to start a reliable billing workflow.'}/>}
            <div className="table-footer"><span>Showing {page === 'overview' ? Math.min(6, filteredAccounts.length) : filteredAccounts.length} of {filteredAccounts.length} accounts</span><span><span className="live-dot"/> Live updates every 5s</span></div>
          </section>}

          {page === 'overview' && <section className="activity-preview"><div><span className="activity-symbol"><Activity size={19}/></span><div><h3>Every change leaves a trail.</h3><p>{events.length ? `${label(events[0].type)} · ${accountNames[events[0].accountId] || 'Billing account'} · ${timestamp(events[0].createdAt)}` : 'Create an account to see the transactional event trail.'}</p></div></div><button className="text-button" onClick={() => navigate('events')}>Explore event stream <ArrowRight size={15}/></button></section>}

          {page === 'events' && <section className="panel"><div className="panel-heading"><div><h2>Delivery activity <span className="count-chip">{events.length}</span></h2><p>Most recent 100 events · full-state, versioned messages</p></div><span className="small-chip"><span className="live-dot"/>Live</span></div><div className="table-toolbar"><label className="search-input"><Search size={16}/><input aria-label="Search events" placeholder="Search account, event ID, or type…" value={search} onChange={(event) => setSearch(event.target.value)}/></label><label className="filter-select"><ListFilter size={15}/><select aria-label="Filter event status" value={eventStatus} onChange={(event) => setEventStatus(event.target.value)}>{['ALL', 'PENDING', 'PUBLISHED', 'RETRYING', 'DELIVERED', 'DEAD_LETTER'].map((item) => <option key={item} value={item}>{item === 'ALL' ? 'All events' : label(item)}</option>)}</select></label></div><div className="event-list">{filteredEvents.map((event) => <EventRow key={event.id} event={event} company={accountNames[event.accountId]} retry={retry} busy={busy}/>)}</div>{filteredEvents.length === 0 && <Empty icon={Activity} title="No events to show" description="Account changes will appear here. Try adjusting your filters."/>}</section>}

          {page === 'reliability' && <><section className="panel reliability-flow"><div className="panel-heading"><div><h2>A dependable path for every change</h2><p>Atomic writes, durable delivery, and version-aware synchronization</p></div><span className="small-chip">{overview?.demo ? 'DEMO MODE' : 'DISTRIBUTED MODE'}</span></div><div className="flow-steps">{[{ icon: Database, name: 'Account + outbox', detail: 'One database transaction' }, { icon: Radio, name: overview?.demo ? 'Local worker' : 'RabbitMQ', detail: overview?.demo ? 'Scheduled delivery' : 'Persistent + confirmed' }, { icon: GitBranch, name: 'Sync processor', detail: 'Deduplicated by event ID' }, { icon: CheckCircle2, name: overview?.demo ? 'Demo projection' : 'Platform + MongoDB', detail: 'Only newer versions apply' }].map(({ icon: Icon, name, detail }, index) => <div className="flow-step" key={name}><span><Icon size={24}/></span><strong>{name}</strong><small>{detail}</small>{index < 3 && <ArrowRight className="flow-arrow" size={19}/>}</div>)}</div></section>
            <div className="reliability-grid"><section className="panel recovery-panel"><span className="section-label">RECOVERY LAB</span><h2>Give your workflow a stress test.</h2><p>{overview?.demo ? 'Pause the simulated billing platform, then edit or create an account. Its event stays safe while delivery retries with exponential backoff.' : 'The distributed profile connects to your configured billing platform and MongoDB. Inspect event errors to diagnose integration failures.'}</p>{overview?.demo && <><div className="platform-state"><span className={`platform-light ${overview.platformAvailable ? '' : 'offline'}`}/><div><strong>Demo billing platform</strong><span>{overview.platformAvailable ? 'Available · accepting changes' : 'Unavailable · delivery will retry'}</span></div><Badge status={overview.platformAvailable ? 'ACTIVE' : 'SUSPENDED'}/></div><button className={`button ${overview.platformAvailable ? 'light' : 'primary'}`} disabled={busy === 'platform'} onClick={togglePlatform}>{busy === 'platform' ? <LoaderCircle size={16} className="spin"/> : <Zap size={16}/>} {overview.platformAvailable ? 'Simulate platform outage' : 'Restore platform'}</button></>}<div className="recovery-hint"><CircleHelp size={16}/><span>After 6 failures, events move to dead letter. Restore the platform and retry them from the event stream.</span></div></section>
            <section className="panel guarantees-panel"><div className="panel-heading"><h2>Reliability by design</h2><ShieldCheck size={21}/></div>{[['Optimistic locking', 'Stale account versions are rejected with HTTP 409.'], ['Idempotent requests', 'An onboarding key creates one account, even when retried.'], ['Durable event history', 'Account and event commit together. No dual-write gap.'], ['At-least-once delivery', 'Duplicate and older events cannot roll state backward.'], ['Observable operations', 'Delivery counters and queue gauges via Actuator.']].map(([title, detail]) => <div className="guarantee" key={title}><CheckCircle2 size={17}/><div><strong>{title}</strong><p>{detail}</p></div></div>)}</section></div>
            <div className="developer-links"><a href="/actuator/health" target="_blank" rel="noreferrer"><Server size={17}/>Service health<ExternalLink size={13}/></a><a href="/actuator/metrics" target="_blank" rel="noreferrer"><Activity size={17}/>Service metrics<ExternalLink size={13}/></a><a href="/graphiql" target="_blank" rel="noreferrer"><GitBranch size={17}/>GraphQL explorer<ExternalLink size={13}/></a></div></>}
        </>}
        <footer className="page-footer"><span><Layers3 size={13}/> Ledger · Billing with confidence.</span><span>{refreshedAt ? `Last refreshed ${refreshedAt.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })}` : 'Waiting for service'}</span></footer>
      </main>
    </div>
    {editing && <AccountModal account={editing} events={events.filter((event) => event.accountId === editing.id)} onClose={() => setEditing(null)} onSaved={async (created) => { setEditing(null); setNotice(created ? 'Account created. Its synchronization event is queued.' : 'Account updated. Its new version is queued for synchronization.'); await refresh(); }}/>} 
    {notice && <div className="toast" role="status"><CheckCircle2 size={18}/><span>{notice}</span><button aria-label="Dismiss notification" onClick={() => setNotice('')}><X size={16}/></button></div>}
  </div>;
}

function Metric({ title, value, icon: Icon, description, accent, warning }) {
  return <section className={`metric-card ${accent ? 'accent' : ''} ${warning ? 'warning' : ''}`}><div className="metric-top"><span>{title}</span><Icon size={17}/></div><strong className="metric-value">{value}</strong><div className="metric-description">{accent ? <span className="metric-tick"><ArrowDownLeft size={12}/></span> : <span className="tiny-dot"/>}{description}</div></section>;
}

function RevenueMix({ accounts }) {
  const plans = Object.keys(planRates).map((plan) => ({ plan, count: accounts.filter((account) => account.plan === plan && account.status === 'ACTIVE').length }));
  const total = plans.reduce((sum, row) => sum + row.count * Number(planRates[row.plan]), 0);
  return <div className="revenue-content"><div className="revenue-total"><span>Recurring revenue</span><strong>{money(total)}<small> / mo</small></strong><p>Based on current active subscriptions</p></div><div className="revenue-bars">{plans.map(({ plan, count }) => <div className="revenue-row" key={plan}><div><span className={`plan-dot ${plan.toLowerCase()}`}/><strong>{label(plan)}</strong><small>{count} {count === 1 ? 'account' : 'accounts'}</small><b>{money(count * Number(planRates[plan]))}</b></div><div className="bar-track"><span className={plan.toLowerCase()} style={{ width: `${total ? count * Number(planRates[plan]) / total * 100 : 0}%` }}/></div></div>)}</div></div>;
}

function Empty({ icon: Icon, title, description }) { return <div className="empty-state"><span><Icon size={23}/></span><h3>{title}</h3><p>{description}</p></div>; }

function EventRow({ event, company, retry, busy }) {
  return <article className="event-row"><span className={`event-icon ${event.status.toLowerCase()}`}>{event.status === 'DELIVERED' ? <Check size={19}/> : event.status === 'DEAD_LETTER' ? <CircleX size={19}/> : <RefreshCw size={17}/>}</span><div className="event-content"><div><strong>{label(event.type)}</strong><Badge status={event.status}/></div><p>{company || event.accountId}<span>·</span>Version {event.accountVersion}<span>·</span>{timestamp(event.createdAt)}</p><code>{event.id}</code>{event.lastError && <div className="event-error">{event.lastError}</div>}</div><div className="event-attempts"><span>{event.attempts} failed {event.attempts === 1 ? 'attempt' : 'attempts'}</span>{['RETRYING', 'DEAD_LETTER'].includes(event.status) && <button className="button light small" disabled={busy === event.id} onClick={() => retry(event.id)}><RefreshCw size={13} className={busy === event.id ? 'spin' : ''}/>Retry now</button>}</div></article>;
}

function AccountModal({ account, events, onClose, onSaved }) {
  const [form, setForm] = useState({ company: account.company || '', billingEmail: account.billingEmail || '', plan: account.plan || 'GROWTH', status: account.status || 'ACTIVE' });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const requestIdentity = useRef({ key: crypto.randomUUID(), payload: null });
  const modalRef = useRef(null);
  const closeRef = useRef(onClose);
  const savingRef = useRef(saving);
  closeRef.current = onClose;
  savingRef.current = saving;
  const closed = account.status === 'CLOSED';
  useEffect(() => {
    const previousFocus = document.activeElement;
    modalRef.current?.querySelector('input, button')?.focus();
    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    function keydown(event) {
      if (event.key === 'Escape' && !savingRef.current) closeRef.current();
      if (event.key === 'Tab') {
        const focusable = modalRef.current?.querySelectorAll('button:not(:disabled), input:not(:disabled), select:not(:disabled), a[href]');
        const first = focusable?.[0], last = focusable?.[focusable.length - 1];
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
      }
    }
    document.addEventListener('keydown', keydown);
    return () => { document.body.style.overflow = originalOverflow; document.removeEventListener('keydown', keydown); previousFocus?.focus(); };
  }, []);
  function change(event) { setForm((previous) => ({ ...previous, [event.target.name]: event.target.value })); }
  async function submit(event) {
    event.preventDefault(); setSaving(true); setError('');
    try {
      const payload = account.isNew ? { company: form.company, billingEmail: form.billingEmail, plan: form.plan } : { ...form, version: account.version };
      const serialized = JSON.stringify(payload);
      if (requestIdentity.current.payload && requestIdentity.current.payload !== serialized) requestIdentity.current.key = crypto.randomUUID();
      requestIdentity.current.payload = serialized;
      await request(account.isNew ? '/api/accounts' : `/api/accounts/${account.id}`, { method: account.isNew ? 'POST' : 'PUT', headers: { 'Idempotency-Key': requestIdentity.current.key }, body: serialized });
      await onSaved(account.isNew);
    } catch (exception) { setError(exception.message); setSaving(false); }
  }
  return <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget && !saving) onClose(); }}><section className="account-modal" role="dialog" aria-modal="true" aria-labelledby="modal-title" ref={modalRef}><div className="modal-header"><span className="modal-symbol"><Wallet size={23}/></span><button className="icon-button" disabled={saving} aria-label="Close account details" onClick={onClose}><X size={20}/></button></div><h2 id="modal-title">{account.isNew ? 'A new account, a fresh start.' : account.company}</h2><p className="modal-intro">{account.isNew ? 'Add the details. We’ll take care of keeping them in sync.' : `Account details · version ${account.version} · ${label(account.syncStatus)}`}</p><form onSubmit={submit}><fieldset disabled={saving || closed}><label>Company name<input name="company" autoComplete="organization" value={form.company} onChange={change} required maxLength={120} placeholder="e.g. Acme Studio"/></label><label>Billing email<input name="billingEmail" type="email" autoComplete="email" value={form.billingEmail} onChange={change} required maxLength={254} placeholder="billing@company.com"/></label><label>Subscription plan<select name="plan" value={form.plan} onChange={change}>{Object.entries(planRates).map(([plan, rate]) => <option key={plan} value={plan}>{label(plan)} · {money(rate)} / month</option>)}</select></label>{!account.isNew && <label>Account status<select name="status" value={form.status} onChange={change}><option value="ACTIVE">Active</option><option value="SUSPENDED">Suspended</option><option value="CLOSED">Closed (permanent)</option></select></label>}</fieldset><div className="plan-summary"><span>Monthly subscription</span><strong>{money(planRates[form.plan])}<small> USD / mo</small></strong></div>{form.status === 'CLOSED' && <p className="form-warning">Closed accounts are permanently read-only and excluded from recurring revenue.</p>}{error && <div className="form-error" role="alert">{error}</div>}{!account.isNew && events.length > 0 && <div className="modal-history"><h3>Recent account events</h3>{events.slice(0, 3).map((event) => <div key={event.id}><span>{label(event.type)} <small>v{event.accountVersion}</small></span><Badge status={event.status}/></div>)}</div>}<div className="modal-actions"><button className="button light" type="button" disabled={saving} onClick={onClose}>{closed ? 'Close' : 'Cancel'}</button>{!closed && <button className="button primary" type="submit" disabled={saving}>{saving ? <LoaderCircle size={16} className="spin"/> : <Check size={16}/>} {saving ? 'Saving…' : account.isNew ? 'Create account' : 'Save changes'}</button>}</div><p className="form-footnote"><ShieldCheck size={13}/>Saved atomically. Synchronized reliably.</p></form></section></div>;
}
