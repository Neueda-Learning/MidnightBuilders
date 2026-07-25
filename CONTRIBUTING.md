# Contributing Guide

## Branching

- Base branch: `main`
- Create feature branches: `feature/<short-description>`
- Optional bugfix branches: `fix/<short-description>`

## Commit Message

Use clear, small commits. Suggested style:

`type(scope): short description`

Examples:
- `feat(payment): add payment creation endpoint skeleton`
- `test(validation): add currency validation tests`
- `docs(readme): update local setup steps`

## Pull Request Rules

- Keep PR focused on one task.
- Add a short description of what changed and why.
- Link related task/ticket (if using Trello/Jira).
- Request at least 1 teammate review.
- Merge only when checks pass.

## Local Validation Before Push

From `demo1/`:

```powershell
.\mvnw.cmd test
```

If schema/config changed, ensure local app still starts:

```powershell
.\mvnw.cmd spring-boot:run
```

