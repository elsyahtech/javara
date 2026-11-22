# JAVARA — Java REST Applications

`JAVARA` is a collection of Java REST Applications templates, organized into dedicated Git branches based on the target database.

The `main` branch serves as the repository overview and index of available database branches. Each database branch provides a self-contained starter template tailored to its respective datastore.

JAVARA aims to provide developers with consistent project conventions and a practical foundation for building REST API applications, allowing them to focus on business logic while keeping underlying technologies and database-specific behavior explicit.

---

## Branches & Supported Databases

Choose the branch that matches your target database:

| Branch | Database | Type |
| :--- | :--- | :--- |
| `postgres` | PostgreSQL | Relational (SQL) |
| `mysql` | MySQL | Relational (SQL) |
| `sqlserver` | SQL Server | Relational (SQL) |
| `oracle` | Oracle | Relational (SQL) |
| `sqlite` | SQLite | Embedded Relational (SQL) |
| `mongo` | MongoDB | Document (NoSQL) |
| `scylla` | ScyllaDB | Wide-Column (NoSQL) |

---

## How to Use This Repository

Database implementations are maintained in separate branches rather than combined in a single branch. Each branch contains an independent starter template for its respective database.

### 1. Clone the Repository

```bash
git clone https://github.com/elsyahtech/javara.git
cd java-rest-api-starter
```

### 2. Switch to Your Database Branch

For example, to use the MySQL starter:

```bash
git switch mysql
```

Replace `mysql` with the branch corresponding to your target database.

### 3. Explore the Starter

After switching branches, refer to the branch-specific README and documentation for setup instructions, configuration, and usage.

---

## Repository Structure

- `main` — Repository overview and database branch index.
- Database branches — Self-contained Java REST API starter templates for each supported database.

---

## Design Philosophy

JAVARA is built around the following principles:

- **Consistency** — Establish common project conventions across database implementations.
- **Transparency** — Keep standard Java and database-specific behavior explicit.
- **Modularity** — Maintain database implementations independently.
- **Practicality** — Provide a useful foundation without unnecessary abstractions.
- **Developer Focus** — Let developers focus on business logic rather than repeatedly setting up the underlying application foundation.

---

## Technology Foundation

The starter templates are built using the Spring ecosystem, leveraging its established infrastructure while maintaining JAVARA's own project conventions and structure.

JAVARA builds upon Spring rather than attempting to reimplement the underlying framework infrastructure.

---

## License

See the [LICENSE](LICENSE) file for license information.
