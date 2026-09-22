# Commit

Creates well-formatted commits with Conventional Commit messages using the git-commit-agent.

## Usage
- `/commit` - Create a commit with proper conventional format

## What it does:
1. Uses the git-commit-agent to handle the commit workflow
2. Checks for staged files and auto-stages if none are found
3. Analyzes changes to detect if multiple commits would be better
4. Creates conventional format commit messages
5. Performs the commit with proper formatting and message

## Best Practices:
- Make atomic commits that address a single concern
- Use appropriate commit types (feat, fix, docs, style, refactor, test, chore, etc.)
- Write clear, concise descriptions in the imperative mood