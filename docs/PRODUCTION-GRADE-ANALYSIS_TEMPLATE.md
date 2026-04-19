# Production-grade analysis — template

Use this template to **capture** architecture reviews, threat assessments, or “readiness” analyses (e.g. after running the prompt in [MCP_REVIEW_PROMPT.md](MCP_REVIEW_PROMPT.md)). Replace placeholder text in **angle brackets** or tables.

**Example filled run (one-off review):** [PRODUCTION-GRADE-ANALYSIS.md](PRODUCTION-GRADE-ANALYSIS.md)

---

## Metadata

| Field | Value |
|-------|--------|
| **Date** | `<YYYY-MM-DD>` |
| **Author / reviewer** | `<name or tool>` |
| **Repository / branch / commit** | `<sha>` |
| **Scope** | `<full repo | module path | PR #>` |

---

## Executive summary

- `<3–7 bullet points: top risks, top strengths, go/no-go if applicable>`

---

## Architecture

| Topic | Assessment | Notes |
|-------|------------|------------|
| Module boundaries | `<OK / concern / gap>` | |
| Serverless deployment model | | |
| Event-driven integration | | |

---

## Data and persistence

| Topic | Assessment | Notes |
|-------|------------|------------|
| DynamoDB schema | | |
| Access patterns | | |
| Idempotency | | |
| Listing / scan trade-offs | | |

---

## Security

| Topic | Assessment | Notes |
|-------|------------|------------|
| Secrets / credentials | | |
| Logging / PII | | |
| AuthN / AuthZ (if any) | | |

---

## Reliability and consistency

| Topic | Assessment | Notes |
|-------|------------|------------|
| Transfer atomicity | | |
| Event publish after commit | | |
| Failure modes (partial writes, EventBridge) | | |

---

## Observability

| Topic | Assessment | Notes |
|-------|------------|------------|
| Logging | | |
| Metrics | | |
| Tracing | | |

---

## Testing

| Topic | Assessment | Notes |
|-------|------------|------------|
| Coverage of critical paths | | |
| Gaps | | |

---

## Prioritized recommendations

| ID | Priority | Recommendation | Owner | Target date |
|----|----------|----------------|-------|-------------|
| R1 | P0 | | | |
| R2 | P1 | | | |
| R3 | P2 | | | |

---

## Risk register

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| | | | |

---

## Follow-up

- [ ] `<action item>`
- [ ] `<action item>`

---

## Appendix — raw notes

`<Free text, links to tickets, PRs, logs>`
