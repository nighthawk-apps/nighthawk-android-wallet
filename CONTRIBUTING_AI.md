# AI-Assisted Development Workflow

This document outlines the workflow for utilizing AI coding agents to contribute to this project, inspired by the "Accept Editz" methodology.

## 1. Plan Mode First
Before jumping into writing code, use the AI agent to draft an `implementation_plan.md`. Ask the AI to break down the goal into clear, executable steps. Review and iterate on this plan until you are satisfied.

## 2. Git Worktrees for Throwaway Features
Use `git worktree` to work on separate, experimental features in parallel without cluttering your main repository checkout.
```bash
# From the root of your bare clone or main repo
git worktree add ../feature-branch-name
cd ../feature-branch-name
```

## 3. The "Accept Editz" Loop
To avoid the tedium of manually approving every small change made by an AI agent, use the following loop:
1. **Commit your working state**: `git commit -a -m "checkpoint"`
2. **Run AI Agent**: Let the AI execute its plan and make sweeping code changes with auto-edits enabled.
3. **Review**: Run `git diff` and review the changes.
4. **Mark Issues**: If the AI makes a mistake, add a comment line with `// XXX: <issue description>` above the incorrect code.
5. **Fix**: Tell the AI to "fix the issues marked with XXX".
6. **Commit & Squash**: Once satisfied, commit the changes and use interactive rebase to squash the checkpoint commits before pushing.

## 4. Helper Scripts
You can use `scripts/ai_workflow.sh` to automate parts of this process (e.g., creating checkpoints and reviewing diffs).
