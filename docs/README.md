# JAVARA Documentation

This documentation is for the PostgreSQL implementation on the `postgres`
branch. It is written for developers who want to run JAVARA, connect an
application to its API, or evaluate it as a starting point for a business
service.

## Choose a path

### Guide

Use the guide when you need an explanation of how JAVARA is installed,
configured, and operated.

- [Guide index](guide/README.md)
- [Getting started](guide/getting-started.md)
- [Configuration](guide/configuration.md)
- [Database migrations and seed data](guide/database-migrations-and-seeding.md)
- [Service-to-service authentication](guide/service-authentication.md)
- [API and access rules](guide/api-usage.md)
- [Production and business adoption](guide/production-and-adoption.md)

### Tutorials

Follow the tutorials to complete common tasks step by step.

- [Tutorial index](tutorial/README.md)
- [First API workflow](tutorial/first-api-workflow.md)
- [Browser application with cookie authentication](tutorial/browser-cookie-authentication.md)

## Reference

The root [README](../README.md) contains the project overview, full endpoint
examples, validation rules, password hashing details, test instructions, and
troubleshooting notes. If a guide and the code disagree, the current code and
tests are the source of truth; please report the mismatch.

JAVARA is a starter service, not a complete identity or business platform.
Read [Production and business adoption](guide/production-and-adoption.md)
before using it with real customers or sensitive data.
