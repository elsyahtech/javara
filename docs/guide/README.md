# JAVARA Guide

This guide explains the main decisions needed to run and adopt JAVARA. You do
not need to run a database or Redis before starting: the local installer can
create temporary development services with Docker.

## Recommended reading order

1. [Getting started](getting-started.md) — install prerequisites, start the
   API, and create the first administrator.
2. [Configuration](configuration.md) — adjust credentials, token transport,
   CORS, rate limits, and proxy settings for your environment.
3. [Database migrations and seed data](database-migrations-and-seeding.md) —
   add schema changes and optional development examples safely.
4. [Service-to-service authentication](service-authentication.md) — provision
   OAuth client credentials, request service tokens, and authorize scopes.
5. [API and access rules](api-usage.md) — understand endpoints, permissions,
   responses, and errors before connecting a client.
6. [Production and business adoption](production-and-adoption.md) — check
   whether the current feature set fits your use case and what must be added
   before production.

## Who this guide is for

- **API users** integrating a web, mobile, or server-side client.
- **Developers** setting up JAVARA locally or changing its configuration.
- **Teams evaluating JAVARA** as a foundation for a business application.

The [tutorials](../tutorial/README.md) provide copyable examples after the
initial setup.
