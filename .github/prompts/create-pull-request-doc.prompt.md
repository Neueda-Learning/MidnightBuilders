# Create Pull Request Doc

Generate a pull request description for this repository based on the current code changes.

## Goal

Review the current changes in the workspace (staged, unstaged, and newly added files when available), then write a concise pull request document that matches the repository's PR style and the structure in `.github/pull_request_template.md`.

## Instructions

- Base the summary on the actual changed code and files only.
- Do not invent changes, reasons, or test results.
- Prefer grouping related changes together instead of listing files one by one.
- Mention important API, validation, persistence, configuration, or documentation changes when they are part of the diff.
- If a section has no meaningful content, write a short, honest item instead of leaving it blank.
- Keep the writing concise, clear, and ready to paste into a pull request.
- Output Markdown only.

## Repository-specific context

- Main backend module is in `demo1/`.
- This project uses Spring Boot + Maven.
- Prefer test steps that are realistic for this repo, for example:
  - `cd demo1 && ./mvnw test`
  - `cd demo1 && ./mvnw spring-boot:run` when startup verification is relevant
- Align with the existing PR expectations in `CONTRIBUTING.md`:
  - keep the PR focused
  - explain what changed and why
  - mention testing clearly
  - note docs updates when behavior or configuration changed

## Output format

Use exactly this structure:

```md
## Title
<one concise pull request title based on the generated PR document>

## What changed
1.
2.

## Why
1.
2.

## How to test
1.
2.

## Checklist
1.
2.
```

## Content guidance

### `## What changed`
- Summarize the actual implementation changes.
- Include notable refactors, new endpoints, validation changes, database/entity updates, state-machine logic changes, tests, and docs updates when applicable.

### `## Why`
- Explain the motivation behind the change.
- Focus on business value, bug prevention, maintainability, correctness, or consistency with project requirements.

### `## How to test`
- Provide concrete verification steps.
- Include commands when they are relevant.
- Mention specific API flows, state transitions, validation cases, or regression checks if the change touches them.

### `## Checklist`
- Convert the repository's PR expectations into short checklist items that match the current change.
- Prefer items such as:
  1. PR stays focused on one task.
  2. Relevant tests were run or identified.
  3. No secrets or sensitive data were added.
  4. Documentation/config updates were included when needed.

## Quality bar

Before finalizing, make sure the PR document:
- includes a concise and specific PR title derived from the generated PR content
- matches the actual code diff
- is easy for reviewers to scan quickly
- avoids generic filler text
- uses numbered items under every heading
- is ready to paste directly into GitHub
