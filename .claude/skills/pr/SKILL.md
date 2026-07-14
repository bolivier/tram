---
name: pr
description: 'Create a pull request for the current branch. Example: "/pr" or "Create a PR" or "Open a pull request"'
---

Create a pull request for the current branch's changes.

A PR here serves two purposes: an audit trail of the decisions behind a change,
and a checkpoint to run quality skills (review, refactor) against a self-contained
chunk of work. Keep the ceremony minimal — draft from context, confirm once, open.

This repo uses `jj`. All VCS steps below are jj-native; there is no git fallback.

## Steps

### 1. Gather context

- `jj st` to see the working-copy changes.
- `jj log` to see the local revisions on this line of work.
- Diff the full branch against `main` to understand the complete change:
  ```sh
  jj diff --from 'fork_point(main)' --to @
  ```
- Check for an existing PR with `gh pr view`; if one exists, use `gh pr diff <number>`
  as the authoritative diff (GitHub computes it against the real merge-base).

### 2. Run quality skills

- If a review or refactor skill exists, run it against the diff before drafting, and
  fold anything it surfaces into the change.
- If no such skill exists yet, skip this step and note in one line that no quality
  skill ran.

### 3. Bookmark and commits

- Ensure the work sits on a bookmark. Create one if needed.
- Commits should be conventional (`feat:`, `fix:`, `refactor:`, `chore:`, `test:`,
  `docs:`), atomic, and single-line. Do not split or rewrite existing commits unless
  the user asks.
- Do not add any attribution trailer (`Co-Authored-By`, `Claude-Session`, etc.).

### 4. Draft the body

Auto-populate from the conversation. Write for a senior engineer already familiar
with the codebase conventions. Sections:

```markdown
# Rationale

Why this change exists — the motivation and the decisions worth reviewing later.

# How

Roughly three sentences on the approach.

# Verification

How to check the change, and what automated verification (tests, checks) covers it.
```

Writing style: no recap intros ("This PR adds…"), no padding or softeners, cite
tickets/PRs by reference rather than paraphrasing, state the fact and stop. The
Rationale section is the audit trail — give it the room it needs; the others stay tight.

### 5. Confirm

- Show the full drafted title and body as plain text in your message.
- Use `AskUserQuestion` to get approval. This is the only interactive gate.
- If the draft changes for any reason, show it again and re-confirm.
- Nothing is pushed before the user approves.

### 6. Create the PR

- `jj git push` the bookmark as an independent command (not chained with `&&`).
- Create the PR with `gh pr create`. Write the body to a temp file and pass
  `--body-file` — never `--body` — so code fences, backticks, and shell
  metacharacters reach GitHub exactly as authored.
- Base branch: if the parent bookmark has an open PR, pass `--base <that-branch>` to
  stack on it; otherwise base on `main`. Only ask if detection is ambiguous.
