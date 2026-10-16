# AI-assisted engineering policy

Applies to source, tests, configuration, infrastructure, migrations and generated documentation. The author remains responsible for every change.

## Allowed assistance

Use assistants for boilerplate, exploration, test ideas and reviewed refactoring. Do not send customer account data, credentials, production logs, proprietary contracts, or unapproved source to a third-party model. Use synthetic fixtures and organization-approved tools.

## Mandatory review before merge

- Identify AI-assisted files and the tool used in the merge request.
- A human reviewer other than the author must review business behavior, access controls, transaction boundaries, compatibility, logging and error handling. Require approval in GitLab branch protection; a checklist alone does not enforce independent approval.
- Independently derive expected monetary results and state invariants. Do not approve a test merely because generated implementation and generated assertions agree.
- Review duplicate and out-of-order delivery, partial external success, crash recovery, stale versions, retries and dead-letter replay.
- Never accept invented APIs, hidden network calls, new permissions, disabled checks, or suppressions without a documented reason.
- Include an additive migration/rollback plan for persisted-state changes. Rehearse migrations against representative data.
- Run architecture/static checks, business and security tests, coverage, dependency/container scans, and applicable infrastructure tests. Treat scan findings and semantic failures as merge blockers.
- Record actual commands, outcomes, limitations and external validation still required. Never equate a passing local test with a production certification.

## GitLab administration required

Protect the default branch, disallow direct pushes, require passing pipelines and at least one independent approval, prevent author/self approval, and require real code owners for security, ledger, worker and migration changes. Configure the owning team's actual identities; this repository cannot activate server-side branch protection without GitLab administrative access.
