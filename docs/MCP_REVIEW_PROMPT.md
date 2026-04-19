# MCP review prompt (code and architecture)

Copy the block below into your AI assistant (e.g. Cursor agent with MCP tools) when you want a **structured senior-level review** of this repository.

---

## Prompt (copy everything inside the fence)

```text
Act as a senior backend architect and perform a comprehensive code and architecture review...

Continue in that role for the repository under review. Be direct, evidence-based, and prioritize risks and trade-offs over generic praise. Use the following checklist and produce a single report with sections matching the headings below.

### Review checklist

1. **Scope and context**
   - [ ] Summarize the intended purpose (wallet API, serverless, event-driven) in 2–3 sentences.
   - [ ] Identify the main modules (e.g. wallet-core, wallet-lambda, serverless-wallet-app) and their responsibilities.

2. **Architecture and layering**
   - [ ] Evaluate separation of concerns: HTTP vs domain vs persistence vs integration (EventBridge).
   - [ ] Flag any god classes, duplicated business logic, or leaky abstractions.
   - [ ] Assess whether `WalletFacade` and port interfaces (`AccountRepository`, `EventPublisher`, `TransferExecutionService`) remain appropriate boundaries.

3. **Serverless and AWS fit**
   - [ ] Review Lambda handler design (routing, cold start, env vars, error mapping).
   - [ ] Review SAM/template resources (IAM least privilege, table names, HTTP API).
   - [ ] Comment on single-Lambda vs per-route Lambdas trade-offs for this codebase.

4. **Data and consistency**
   - [ ] Review DynamoDB access patterns (transfers, idempotency, listing).
   - [ ] Identify hot-key risks, scan-based listing, and consistency guarantees.
   - [ ] Evaluate idempotency semantics for transfers and replay behavior.

5. **Event-driven design**
   - [ ] Verify domain events are emitted after successful writes and serialized sensibly for EventBridge.
   - [ ] Identify gaps: outbox, retries, dead-letter, ordering guarantees.

6. **Security and compliance**
   - [ ] Review logging for PII/secrets leakage; correlation IDs and request tracing.
   - [ ] Note authentication/authorization gaps (this MVP may be intentionally open).

7. **Observability and operations**
   - [ ] Assess logging structure, metrics hooks, and tracing readiness.
   - [ ] List what would be needed for production SLOs (latency, errors, saturation).

8. **Testing**
   - [ ] Evaluate unit vs integration coverage and critical path tests (transfer idempotency, insufficient funds).
   - [ ] Suggest missing tests or flaky areas.

9. **Configuration and operability**
   - [ ] Confirm configuration is externalized (YAML/env) and secrets are not hardcoded.
   - [ ] Note any magic numbers or hardcoded limits that should be configurable.

10. **Prioritized recommendations**
   - [ ] Rank issues: P0 (block production), P1 (near-term), P2 (nice-to-have).
   - [ ] For each P0/P1, suggest a concrete remediation or spike.

### Output format

- Start with an **Executive summary** (bullets).
- Then one subsection per checklist area with **Findings** and **Recommendations**.
- End with a **Risk register** table: Risk | Likelihood | Impact | Mitigation.
```

---

## Optional: persist findings

After the review, paste summarized results into [PRODUCTION-GRADE-ANALYSIS_TEMPLATE.md](PRODUCTION-GRADE-ANALYSIS_TEMPLATE.md) so the team can track remediation.
