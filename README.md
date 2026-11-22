# JAVARA — Java REST Applications

`JAVARA` is a collection of Java REST API Application templates, organized into dedicated Git branches based on the target database.

The `main` branch serves as the repository overview and index of available database branches. Each database branch provides a self-contained starter template tailored to its respective datastore.

JAVARA provides a consistent and practical foundation for building backend services, helping developers reduce repetitive infrastructure work while keeping application logic, standard Java practices, and database-specific behavior explicit.

## Why JAVARA?

Building backend applications involves more than implementing business requirements. Developers repeatedly deal with application structure, configuration, request handling, validation, database integration, security, and operational concerns.

JAVARA aims to provide a consistent foundation for these recurring requirements, so developers can spend more time building their applications rather than repeatedly setting up the underlying infrastructure.

JAVARA is built on the Spring ecosystem, leveraging its established capabilities while introducing its own project conventions, structure, and modular approach to application development.

The goal is not to hide Java or Spring, but to make common application development workflows more consistent, practical, and maintainable.

## What JAVARA Provides

- **Consistent Application Structure** — Establishes predictable project conventions that make applications easier to navigate, maintain, and hand over between developers.
- **Integrated Application Foundation** — Provides a structured starting point for common backend application components and infrastructure.
- **Database-Specific Starters** — Maintains dedicated starter implementations for different database systems.
- **Explicit and Predictable Behavior** — Encourages clear configuration and understandable application behavior without unnecessary hidden abstractions.
- **Modular Approach** — Organizes application components and database-specific implementations to keep concerns separated and maintainable.
- **Developer Control** — Builds upon Java and Spring while allowing developers to use standard APIs and underlying technologies whenever needed.

JAVARA provides a foundation, not a replacement for Java or the Spring ecosystem. Its purpose is to reduce repetitive setup while leaving application design, business requirements, and implementation decisions under the developer's control.

## Focus on Business Logic

JAVARA handles common application setup and infrastructure so developers can spend more time implementing application-specific requirements.

The starter templates are intended to provide a practical foundation for:

- REST API development
- Application structure and conventions
- Request validation and handling
- Database integration
- Error handling
- Security configuration
- Logging and operational capabilities
- Testing and maintainability

Applications remain Java applications built on Spring. Developers can use Java standard libraries, Spring APIs, and other libraries whenever a requirement falls outside JAVARA's conventions or starter implementation.

## Design Philosophy

### Opinionated, Not Restrictive

JAVARA establishes common conventions and sensible defaults to reduce repetitive decisions without unnecessarily restricting application architecture or implementation choices.

### Explicit Over Implicit

Application configuration and behavior should remain understandable. Avoid unnecessary magic, hidden side effects, and silent fallback behavior that makes applications difficult to diagnose.

### Practical Abstractions

Abstractions should simplify recurring development tasks without obscuring the underlying Java, Spring, or database behavior.

### Modular by Design

Database implementations and application components should remain organized around clear responsibilities, making the project easier to maintain and extend.

### Maintainability Over Complexity

Prefer straightforward, readable, and maintainable implementations over unnecessary abstraction or complexity.

### Developer Control

JAVARA provides conventions and infrastructure, while developers retain control over business logic, application behavior, and the technologies used underneath.

---

## Branches & Supported Databases

Choose the branch that matches your target database:

| Branch | Database | Type |
| :--- | :--- | :--- |
| `oracle` | Oracle | Relational (SQL) |
| `sqlserver` | SQL Server | Relational (SQL) |
| `postgres` | PostgreSQL | Relational (SQL) |
| `mysql` | MySQL | Relational (SQL) |
| `sqlite` | SQLite | Embedded Relational (SQL) |
| `mongo` | MongoDB | Document (NoSQL) |
| `scylla` | ScyllaDB | Wide-Column (NoSQL) |
| `redis` | Redis | Memcache |

Each database implementation is maintained in its own branch rather than mixing all database configurations into a single branch.

## How to Use This Repository

### 1. Clone the Repository

```bash
git clone https://github.com/elsyahtech/javara.git
cd javara
```

### 2. Switch to Your Database Branch

For example, to use the ORACLE starter:

```bash
git switch oracle
```

Replace `oracle` with the branch corresponding to your target database.

### 3. Explore the Starter

After switching branches, refer to the branch-specific README and documentation for requirements, setup instructions, configuration, and usage.

## Repository Structure

- `main` — Repository overview and database branch index.
- Database branches — Self-contained Java REST API starter templates for their respective database implementations.

## Technology Foundation

JAVARA starter templates use the Spring ecosystem as their underlying application foundation.

Spring provides established infrastructure and application capabilities, while JAVARA maintains its own project conventions and starter organization. This approach avoids unnecessarily reimplementing mature infrastructure and allows JAVARA to focus on providing a consistent developer experience.

Java, Spring, and database-specific APIs remain accessible to developers when direct usage is needed.

## Production Use

Starter templates provide a foundation for application development, not a guarantee of production readiness.

Before deploying an application, review and validate its configuration, security requirements, database behavior, error handling, dependencies, testing, and deployment environment.

Production readiness depends on the application implementation and deployment configuration.

## Documentation

Each database branch maintains its own README and supporting documentation for its specific implementation.

Refer to the documentation in the branch you intend to use for database configuration, application setup, implementation details, and testing.

## Contributing

Contributions, suggestions, and bug reports are welcome.

Please review the repository guidelines before submitting changes. Contributions should align with JAVARA's design philosophy, maintainability goals, and project conventions.

## License

See the [LICENSE](LICENSE) file for license information.