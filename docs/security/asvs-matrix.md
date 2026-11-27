# JAVARA OWASP ASVS Verification Matrix

## Assessment status

| Field | Value |
|---|---|
| Standard | OWASP Application Security Verification Standard (ASVS) v5.0.0 |
| Requested target | Level 3 |
| Assessment status | In progress; 5 requirements `Fail`, 16 `Pending`, 324 `Not assessed`, none `Pass`; see [assessment record](assessment/README.md) |
| Assessor and assessment date | To be completed |
| Intended use | Reusable JAVARA baseline for banking, finance/fintech, energy, and startup solutions |
| Product objective | Modular, configurable, secure foundation for multi-company solutions; not yet assessed |
| Application and deployment scope | Core JAVARA baseline; client-specific deployment scope to be agreed with the Auditor |

This is a working checklist, not an assessment result, compliance claim, or
OWASP certification. The target level records the request for a
"production-ready" assessment; it does not mean JAVARA has achieved Level 3 or
is production-ready. OWASP does not certify applications or issue ASVS
certifications.

This assessment is for the reusable JAVARA baseline, not every application
built by a client or its production environment. Each client deployment still
needs its own scope, risk assessment, configuration review, and operational
evidence. ASVS Level 3 does not by itself establish compliance with sector,
regulatory, contractual, or jurisdiction-specific obligations. Identify any
applicable overlays separately, such as payment-card controls or energy/OT
requirements, based on the client's actual use case.

The multi-company isolation model is an open architecture decision. Do not
claim shared multi-tenancy, dedicated tenant isolation, or support for both
until the model is documented and its isolation guarantees are verified.

The matrix lists all 345 v5.0.0 requirements because a Level 3 assessment
includes the requirements assigned to Levels 1 and 2 as well as Level 3. The
minimum level column identifies the level assigned to each requirement in the
standard; it is not a reason to omit lower-level requirements from a Level 3
assessment. The target is to satisfy every requirement. No requirement is
excluded by default; raise any genuine applicability issue with the Auditor
for an explicit decision rather than silently marking it `Not applicable`.

The initial V6/V7 source review recorded 21 requirements as `Fail`. Following
remediation in commit `b009b8a` and local verification, the current disposition
is 5 `Fail`, 16 `Pending` retest/evidence, and 324 `Not assessed`. No
requirement is currently marked `Pass` or excluded. See the
[authentication and session management
review](assessment/authentication-session-review.md) for scope, evidence, and
remaining gaps.

## How to use this matrix

For each requirement, the assessor should:

1. Confirm applicability to the agreed application and deployment scope.
2. Record a result only after verification, with reproducible evidence.
3. Give a reason and compensating-control details for every non-applicable
   requirement or exception.
4. Record the finding, remediation owner, and retest result for failures.

Use these result values:

- `Not assessed` — no verification has been completed.
- `Pass` — verified with evidence against the agreed scope.
- `Fail` — evidence shows the requirement is not met.
- `Not applicable` — only after the requirement is shown to be genuinely
  outside the agreed scope, the rationale is documented, and the Auditor
  explicitly accepts the exclusion. This does not count as a pass.
- `Pending` — scope, evidence, or retesting is still outstanding.

Do not treat automated test results alone as proof for requirements that need
manual review, architecture or configuration evidence, or operational
verification. Source code review cannot verify production infrastructure,
deployment settings, or operational processes unless those are separately
included in scope.

## Pre-sales readiness evidence

ASVS is the application-security baseline, not a complete product-readiness
or sector-compliance certification. Before making customer-facing security or
multi-company claims, maintain a versioned evidence package for the specific
JAVARA release:

1. **Scope and claims:** supported modules and deployment modes, known
   limitations, target release/commit, and the exact customer-facing claims
   those artifacts support.
2. **Architecture:** system/data-flow diagrams, trust boundaries, threat model,
   extension/configuration model, and a documented tenant-isolation decision.
3. **Verification:** completed ASVS matrix, reproducible test and scan reports,
   tenant-boundary tests for each supported isolation model, and an independent
   assessment with findings and retest evidence.
4. **Operations:** secure deployment profile, secrets/key management, TLS and
   network configuration, backup/restore results, logging/monitoring, incident
   response, and recovery procedures.
5. **Supply chain and maintenance:** SBOM or dependency inventory, dependency
   and secret-scan results, release/change controls, vulnerability handling,
   and patch-support policy.
6. **Industry overlays:** separately identify applicable contractual,
   regulatory, jurisdictional, payment-card, or energy/OT requirements for
   each customer use case. Do not imply that ASVS alone satisfies them.

Do not market an unverified capability as supported. A security assessment by
this project can assemble repository evidence and identify gaps, but production
claims require evidence from the target deployment and review by the Auditor
or another qualified independent assessor.

## Official source

Requirement IDs and minimum levels are derived from the official
[OWASP ASVS v5.0.0 English CSV](https://github.com/OWASP/ASVS/blob/v5.0.0/5.0/docs_en/OWASP_Application_Security_Verification_Standard_5.0.0_en.csv).
Use the [official ASVS v5.0.0 release](https://github.com/OWASP/ASVS/tree/v5.0.0)
for the normative requirement text and assessment guidance. Requirement
descriptions are intentionally not duplicated here.

Source: OWASP ASVS v5.0.0. This checklist adapts its versioned requirement IDs
and minimum levels into a blank evidence matrix. The ASVS-derived matrix is
shared under the same
[Creative Commons Attribution-ShareAlike 4.0 International license](https://creativecommons.org/licenses/by-sa/4.0/).
It is not endorsed by OWASP.

## Requirement checklist

### V1 - Encoding and Sanitization
| Requirement ID | Minimum level | Applicability | Result | Evidence | Finding / owner / retest |
|---|---:|---|---|---|---|
| `v5.0.0-V1.1.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V1.1.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V1.2.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V1.2.2` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V1.2.3` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V1.2.4` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V1.2.5` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V1.2.6` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V1.2.7` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V1.2.8` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V1.2.9` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V1.2.10` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V1.3.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V1.3.2` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V1.3.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V1.3.4` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V1.3.5` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V1.3.6` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V1.3.7` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V1.3.8` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V1.3.9` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V1.3.10` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V1.3.11` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V1.3.12` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V1.4.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V1.4.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V1.4.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V1.5.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V1.5.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V1.5.3` | L3 | Pending | Not assessed |  |  |

### V2 - Validation and Business Logic
| Requirement ID | Minimum level | Applicability | Result | Evidence | Finding / owner / retest |
|---|---:|---|---|---|---|
| `v5.0.0-V2.1.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V2.1.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V2.1.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V2.2.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V2.2.2` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V2.2.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V2.3.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V2.3.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V2.3.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V2.3.4` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V2.3.5` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V2.4.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V2.4.2` | L3 | Pending | Not assessed |  |  |

### V3 - Web Frontend Security
| Requirement ID | Minimum level | Applicability | Result | Evidence | Finding / owner / retest |
|---|---:|---|---|---|---|
| `v5.0.0-V3.1.1` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V3.2.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V3.2.2` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V3.2.3` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V3.3.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V3.3.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V3.3.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V3.3.4` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V3.3.5` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V3.4.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V3.4.2` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V3.4.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V3.4.4` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V3.4.5` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V3.4.6` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V3.4.7` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V3.4.8` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V3.5.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V3.5.2` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V3.5.3` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V3.5.4` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V3.5.5` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V3.5.6` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V3.5.7` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V3.5.8` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V3.6.1` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V3.7.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V3.7.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V3.7.3` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V3.7.4` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V3.7.5` | L3 | Pending | Not assessed |  |  |

### V4 - API and Web Service
| Requirement ID | Minimum level | Applicability | Result | Evidence | Finding / owner / retest |
|---|---:|---|---|---|---|
| `v5.0.0-V4.1.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V4.1.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V4.1.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V4.1.4` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V4.1.5` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V4.2.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V4.2.2` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V4.2.3` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V4.2.4` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V4.2.5` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V4.3.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V4.3.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V4.4.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V4.4.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V4.4.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V4.4.4` | L2 | Pending | Not assessed |  |  |

### V5 - File Handling
| Requirement ID | Minimum level | Applicability | Result | Evidence | Finding / owner / retest |
|---|---:|---|---|---|---|
| `v5.0.0-V5.1.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V5.2.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V5.2.2` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V5.2.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V5.2.4` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V5.2.5` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V5.2.6` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V5.3.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V5.3.2` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V5.3.3` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V5.4.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V5.4.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V5.4.3` | L2 | Pending | Not assessed |  |  |

### V6 - Authentication
| Requirement ID | Minimum level | Applicability | Result | Evidence | Finding / owner / retest |
|---|---:|---|---|---|---|
| `v5.0.0-V6.1.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V6.1.2` | L2 | In scope | Fail | [AUTH-01](assessment/authentication-session-review.md#auth-01) | Owner TBD; verify context-word policy at registration and change. |
| `v5.0.0-V6.1.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V6.2.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V6.2.2` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V6.2.3` | L1 | In scope | Pending | [AUTH-02](assessment/authentication-session-review.md#auth-02) | Dedicated current-password change flow implemented; complete ASVS retest remains. |
| `v5.0.0-V6.2.4` | L1 | In scope | Pending | [AUTH-03](assessment/authentication-session-review.md#auth-03) | Policy implemented; approved production dataset and coverage evidence remain. |
| `v5.0.0-V6.2.5` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V6.2.6` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V6.2.7` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V6.2.8` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V6.2.9` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V6.2.10` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V6.2.11` | L2 | In scope | Fail | [AUTH-01](assessment/authentication-session-review.md#auth-01) | Owner TBD; verify documented context-word policy is enforced. |
| `v5.0.0-V6.2.12` | L2 | In scope | Pending | [AUTH-04](assessment/authentication-session-review.md#auth-04) | Offline hash lookup implemented; approved corpus and deployment evidence remain. |
| `v5.0.0-V6.3.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V6.3.2` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V6.3.3` | L2 | In scope | Fail | [AUTH-05](assessment/authentication-session-review.md#auth-05) | Local Email OTP enrollment/login and session revocation manually verified; verify production WebAuthn policy, authenticators, and phishing resistance. |
| `v5.0.0-V6.3.4` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V6.3.5` | L3 | In scope | Fail | [AUTH-06](assessment/authentication-session-review.md#auth-06) | Owner TBD; test suspicious-auth detection and notifications. |
| `v5.0.0-V6.3.6` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V6.3.7` | L3 | In scope | Pending | [AUTH-07](assessment/authentication-session-review.md#auth-07) | Email notifications implemented; verify all event delivery and failure/retry handling. |
| `v5.0.0-V6.3.8` | L3 | In scope | Pending | [AUTH-08](assessment/authentication-session-review.md#auth-08) | Registration returns the same generic response for new and existing addresses; automated checks and local retest pass. Complete requirement review, including timing considerations, remains. |
| `v5.0.0-V6.4.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V6.4.2` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V6.4.3` | L2 | In scope | Pending | [AUTH-09](assessment/authentication-session-review.md#auth-09) | Local recovery succeeded with a new password; previously invalidated password was rejected without consuming the link. Requirement-specific coverage remains. |
| `v5.0.0-V6.4.4` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V6.4.5` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V6.4.6` | L3 | In scope | Pending | [AUTH-10](assessment/authentication-session-review.md#auth-10) | Emergency randomization, session revocation, notice delivery, and support-authorized user recovery verified locally; complete requirement-specific review. |
| `v5.0.0-V6.5.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V6.5.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V6.5.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V6.5.4` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V6.5.5` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V6.5.6` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V6.5.7` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V6.5.8` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V6.6.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V6.6.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V6.6.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V6.6.4` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V6.7.1` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V6.7.2` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V6.8.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V6.8.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V6.8.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V6.8.4` | L2 | Pending | Not assessed |  |  |

### V7 - Session Management
| Requirement ID | Minimum level | Applicability | Result | Evidence | Finding / owner / retest |
|---|---:|---|---|---|---|
| `v5.0.0-V7.1.1` | L2 | In scope | Pending | [SES-01](assessment/authentication-session-review.md#ses-01) | Timeout values documented and implemented; risk rationale and retest remain. |
| `v5.0.0-V7.1.2` | L2 | In scope | Pending | [SES-02](assessment/authentication-session-review.md#ses-02) | Five-session limit implemented; edge-case retest and risk rationale remain. |
| `v5.0.0-V7.1.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V7.2.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V7.2.2` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V7.2.3` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V7.2.4` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V7.3.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V7.3.2` | L2 | In scope | Pending | [SES-03](assessment/authentication-session-review.md#ses-03) | Absolute session expiry preserved across refresh; boundary/repeated-refresh retest remains. |
| `v5.0.0-V7.4.1` | L1 | In scope | Pending | [SES-04](assessment/authentication-session-review.md#ses-04) | Session-bound access-token validation implemented; complete revocation retest remains. |
| `v5.0.0-V7.4.2` | L1 | In scope | Pending | [SES-05](assessment/authentication-session-review.md#ses-05) | Local disable/delete token-revocation checks exercised; full requirement review remains. |
| `v5.0.0-V7.4.3` | L2 | In scope | Pending | [SES-06](assessment/authentication-session-review.md#ses-06) | Local same-user multi-session password-change revocation exercised; complete requirement review remains. |
| `v5.0.0-V7.4.4` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V7.4.5` | L2 | In scope | Pending | [SES-07](assessment/authentication-session-review.md#ses-07) | Admin termination manually exercised; audit-trail and complete requirement review remain. |
| `v5.0.0-V7.5.1` | L2 | In scope | Pending | [SES-08](assessment/authentication-session-review.md#ses-08) | Re-authenticated email change and confirmation manually exercised; complete requirement review remains. |
| `v5.0.0-V7.5.2` | L2 | In scope | Pending | [SES-09](assessment/authentication-session-review.md#ses-09) | Session listing and self/admin termination flows manually exercised; complete requirement review remains. |
| `v5.0.0-V7.5.3` | L3 | In scope | Fail | [SES-10](assessment/authentication-session-review.md#ses-10) | Login MFA does not enforce step-up verification for sensitive operations. |
| `v5.0.0-V7.6.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V7.6.2` | L2 | Pending | Not assessed |  |  |

### V8 - Authorization
| Requirement ID | Minimum level | Applicability | Result | Evidence | Finding / owner / retest |
|---|---:|---|---|---|---|
| `v5.0.0-V8.1.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V8.1.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V8.1.3` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V8.1.4` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V8.2.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V8.2.2` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V8.2.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V8.2.4` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V8.3.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V8.3.2` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V8.3.3` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V8.4.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V8.4.2` | L3 | Pending | Not assessed |  |  |

### V9 - Self-contained Tokens
| Requirement ID | Minimum level | Applicability | Result | Evidence | Finding / owner / retest |
|---|---:|---|---|---|---|
| `v5.0.0-V9.1.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V9.1.2` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V9.1.3` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V9.2.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V9.2.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V9.2.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V9.2.4` | L2 | Pending | Not assessed |  |  |

### V10 - OAuth and OIDC
| Requirement ID | Minimum level | Applicability | Result | Evidence | Finding / owner / retest |
|---|---:|---|---|---|---|
| `v5.0.0-V10.1.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V10.1.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V10.2.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V10.2.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V10.2.3` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V10.3.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V10.3.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V10.3.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V10.3.4` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V10.3.5` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V10.4.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V10.4.2` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V10.4.3` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V10.4.4` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V10.4.5` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V10.4.6` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V10.4.7` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V10.4.8` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V10.4.9` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V10.4.10` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V10.4.11` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V10.4.12` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V10.4.13` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V10.4.14` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V10.4.15` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V10.4.16` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V10.5.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V10.5.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V10.5.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V10.5.4` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V10.5.5` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V10.6.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V10.6.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V10.7.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V10.7.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V10.7.3` | L2 | Pending | Not assessed |  |  |

### V11 - Cryptography
| Requirement ID | Minimum level | Applicability | Result | Evidence | Finding / owner / retest |
|---|---:|---|---|---|---|
| `v5.0.0-V11.1.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V11.1.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V11.1.3` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V11.1.4` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V11.2.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V11.2.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V11.2.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V11.2.4` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V11.2.5` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V11.3.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V11.3.2` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V11.3.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V11.3.4` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V11.3.5` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V11.4.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V11.4.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V11.4.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V11.4.4` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V11.5.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V11.5.2` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V11.6.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V11.6.2` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V11.7.1` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V11.7.2` | L3 | Pending | Not assessed |  |  |

### V12 - Secure Communication
| Requirement ID | Minimum level | Applicability | Result | Evidence | Finding / owner / retest |
|---|---:|---|---|---|---|
| `v5.0.0-V12.1.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V12.1.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V12.1.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V12.1.4` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V12.1.5` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V12.2.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V12.2.2` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V12.3.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V12.3.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V12.3.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V12.3.4` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V12.3.5` | L3 | Pending | Not assessed |  |  |

### V13 - Configuration
| Requirement ID | Minimum level | Applicability | Result | Evidence | Finding / owner / retest |
|---|---:|---|---|---|---|
| `v5.0.0-V13.1.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V13.1.2` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V13.1.3` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V13.1.4` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V13.2.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V13.2.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V13.2.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V13.2.4` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V13.2.5` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V13.2.6` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V13.3.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V13.3.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V13.3.3` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V13.3.4` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V13.4.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V13.4.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V13.4.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V13.4.4` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V13.4.5` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V13.4.6` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V13.4.7` | L3 | Pending | Not assessed |  |  |

### V14 - Data Protection
| Requirement ID | Minimum level | Applicability | Result | Evidence | Finding / owner / retest |
|---|---:|---|---|---|---|
| `v5.0.0-V14.1.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V14.1.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V14.2.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V14.2.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V14.2.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V14.2.4` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V14.2.5` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V14.2.6` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V14.2.7` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V14.2.8` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V14.3.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V14.3.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V14.3.3` | L2 | Pending | Not assessed |  |  |

### V15 - Secure Coding and Architecture
| Requirement ID | Minimum level | Applicability | Result | Evidence | Finding / owner / retest |
|---|---:|---|---|---|---|
| `v5.0.0-V15.1.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V15.1.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V15.1.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V15.1.4` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V15.1.5` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V15.2.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V15.2.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V15.2.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V15.2.4` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V15.2.5` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V15.3.1` | L1 | Pending | Not assessed |  |  |
| `v5.0.0-V15.3.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V15.3.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V15.3.4` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V15.3.5` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V15.3.6` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V15.3.7` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V15.4.1` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V15.4.2` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V15.4.3` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V15.4.4` | L3 | Pending | Not assessed |  |  |

### V16 - Security Logging and Error Handling
| Requirement ID | Minimum level | Applicability | Result | Evidence | Finding / owner / retest |
|---|---:|---|---|---|---|
| `v5.0.0-V16.1.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V16.2.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V16.2.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V16.2.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V16.2.4` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V16.2.5` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V16.3.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V16.3.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V16.3.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V16.3.4` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V16.4.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V16.4.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V16.4.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V16.5.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V16.5.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V16.5.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V16.5.4` | L3 | Pending | Not assessed |  |  |

### V17 - WebRTC
| Requirement ID | Minimum level | Applicability | Result | Evidence | Finding / owner / retest |
|---|---:|---|---|---|---|
| `v5.0.0-V17.1.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V17.1.2` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V17.2.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V17.2.2` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V17.2.3` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V17.2.4` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V17.2.5` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V17.2.6` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V17.2.7` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V17.2.8` | L3 | Pending | Not assessed |  |  |
| `v5.0.0-V17.3.1` | L2 | Pending | Not assessed |  |  |
| `v5.0.0-V17.3.2` | L2 | Pending | Not assessed |  |  |

