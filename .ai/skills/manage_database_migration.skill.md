# Skill: Manage Database Migrations with Liquibase

This skill covers adding and verifying database schema migrations using Liquibase in multiple formats.

## Procedure

### 1. Select the Format
This repository supports XML, YAML, and JSON.
- **XML**: Use for complex migrations or when detailed comments are needed.
- **YAML**: Use for simple, readable changes.
- **JSON**: Use when integrating with JSON-heavy workflows.

### 2. Create the Changelog
Create a new file in `src/main/resources/db/changelog/migrations/`.
Follow naming convention: `YYYYMMDD-HHmm-{description}.{xml|yaml|json}`.

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
Update `src/main/resources/db/changelog/db.changelog-master.xml`:
```xml
<include file="db/changelog/migrations/20240418-01-demo-table.yaml" relativeToChangelogFile="false"/>
```

### 4. Verify with Tests
Run integration tests which use Testcontainers (PostgreSQL) to verify the migration applied correctly:
```powershell
.\mvnw.cmd verify -Dtest=*IntTest
```

## Best Practices
- **Always Rollback**: Include a `<rollback>` section if the change is not automatically reversible.
- **Preconditions**: Use `<preConditions>` for complex logic.
- **No Data in Schema**: Avoid large data inserts in schema migrations. Use a separate seed changelog.
