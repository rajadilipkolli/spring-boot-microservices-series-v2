---
name: Manage Database Migrations with Liquibase
description: Add, modify, and verify database schema migrations using Liquibase across multiple formats.
---

# Skill: Manage Database Migrations with Liquibase

This skill covers adding and verifying database schema migrations using Liquibase in multiple formats.

## Rules
- **Non-Destructive**: Never perform `DROP` operations unless explicitly authorized. Use `ALTER` or versioning.
- **Support Multi-Format**: Maintain compatibility with XML, YAML, and JSON changelogs depending on the service.
- **Traceability**: Every changeset must have a unique ID and a descriptive author.

## Procedure

### 1. Select the Format
Locate the service's changelog directory: `src/main/resources/db/changelog/`.
Determine the preferred format (usually defined by existing files). This repository supports XML, YAML, and JSON.
- **XML**: Use for complex migrations or when detailed comments are needed.
- **YAML**: Use for simple, readable changes.
- **JSON**: Use when integrating with JSON-heavy workflows.

### 2. Create the Changelog
Create a new file in `src/main/resources/db/changelog/migrations/`.
Follow naming convention: `YYYYMMDD-HHmm-{description}.{xml|yaml|json}` or `YYYYMMDD-ID-{description}.{ext}`.

#### Example (YAML):
```yaml
databaseChangeLog:
  - changeSet:
      id: 20240418-01
      author: antigravity
      changes:
        - createTable:
            tableName: demo_table
            columns:
              - column:
                  name: id
                  type: BIGINT
                  constraints:
                    primaryKey: true
                    nullable: false
```

### 3. Register in Master
Update `src/main/resources/db/changelog/db.changelog-master.xml` to include the new migration:
```xml
<include file="db/changelog/migrations/20240418-01-demo-table.yaml" relativeToChangelogFile="false"/>
```

### 4. Verify with Tests
Verify the migration by running the service's integration test suite:
```powershell
.\mvnw.cmd verify -Dit.test=*IntTest
```
This ensures the migration applies successfully to a Testcontainers PostgreSQL instance.

## Best Practices
- **Always Rollback**: Include a `<rollback>` section if the change is not automatically reversible to define how to undo the change.
- **Preconditions**: Use `<preConditions>` for complex logic to avoid errors in environments where parts of the schema might already exist.
- **Small Changesets**: Keep changesets small and focused on a single change.
- **No Data in Schema**: Avoid large data inserts in schema migrations. Use a separate seed changelog.
