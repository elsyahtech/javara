# Assessment Preparation Checklist

The Auditor's target is to satisfy all 345 OWASP ASVS v5.0.0 Level 3
requirements. This checklist identifies evidence that JAVARA owners and
customer/operations teams will need to provide. It is not a request to share
secrets or production data in this repository.

## Is a server needed now?

No server is needed for the current source assessment and tenant-model design.
Before runtime and operational controls can be verified, provide a
non-production staging environment that represents the intended deployment.
Do not use production customer data for the assessment.

The staging environment can be cloud or on-premises. It should include the
same relevant trust boundaries and services as the target deployment so that
the evidence is meaningful.

## Needed to continue design and repository assessment

- [ ] Confirm the exact JAVARA release/commit to assess. The current code
  baseline is recorded in [scope.md](scope.md).
- [ ] Confirm the product features that must be supported in the initial
  customer-ready release, including user and service-client flows.
- [ ] Confirm which jurisdictions and customer/regulatory overlays are in
  scope for the first release.
- [ ] Review the selected tenancy and role foundation:
  [tenant-model.md](tenant-model.md). `SINGLE` is supported; shared mode,
  tenant context, and tenant-scoped data isolation remain incomplete.

## Needed from architecture and engineering owners

- [ ] System and data-flow diagrams with trust boundaries, external services,
  data stores, and tenant-owned data.
- [ ] Threat model and security design for tenant membership, tenant selection,
  authorization, service clients, and privileged support access.
- [ ] Secure configuration and extension/module model, including which settings
  are global versus tenant-specific and who may change them.
- [ ] CI results for the assessed commit, code-review/release controls, an
  SBOM or dependency inventory, and available SAST, dependency, and secret-scan
  reports.
- [ ] Security test evidence, including cross-tenant authorization tests for
  every tenant-owned read/write/list/export path once implemented.
- [ ] An approved offline password denylist with at least 3,000 common
  passwords, plus the breached-password data source, update cadence, provenance,
  licensing/usage terms, file format, and integrity-check process. Do not put
  leaked personal data or raw credentials in this repository.
- [ ] The accepted hardware security-key models and WebAuthn attestation policy,
  including trusted attestation roots/AAGUIDs, authenticator replacement, and
  the two-key or identity-proofing recovery procedure.
- [ ] The SMTP staging provider and sender domain, with credentials supplied
  only through a secret manager/environment at deployment. Provide delivery
  requirements, bounce handling, and verified sender-domain DNS controls
  (SPF/DKIM/DMARC); do not commit credentials.

## Needed from operations or the customer deployment owner

- [ ] Mount the approved common-password and breached-password datasets at the
  configured paths, read-only where possible. Record source, licensing,
  dataset version, freshness date, integrity checksum, expected coverage, and
  the owner/process for updating and rolling the application.
- [ ] Staging deployment topology, runtime/container platform, DNS name, and
  effective configuration. Redact credentials, key material, live tokens, and
  personal data.
- [ ] TLS configuration and evidence: certificate chain and renewal ownership,
  TLS termination point, HTTPS redirect behavior, and relevant internal
  service-to-service TLS. A representative staging endpoint is needed for
  runtime verification; no production certificate or private key should be
  shared.
- [ ] Network/firewall boundaries, identity-provider/SSO, secret/key
  management, and database/Redis/cloud security settings. Keep databases and
  Redis private to the application network where the deployment supports it.
- [ ] Data classification, retention/deletion rules, access reviews, and
  production logging/monitoring/alerting configuration.
- [ ] Backup and restore drill results, recovery objectives, incident response,
  vulnerability handling, and patching procedures.
- [ ] Independent penetration-test or security-review report, including scope,
  findings, remediation, and retest evidence, when available.

Collect deployment evidence only for the agreed target environment. A
repository-only review cannot prove these operational controls.
