---
name: euclidean-sketchpad
description: Project skills for Euclidean Sketchpad. Supports 4 actions — "deploy app" (version release), "export diagram" (headless PNG/SVG), "merge pr" (merge release PR and push tag), "save changes" (update docs and commit). Use when user says "deploy", "release", "cut a release", "export diagram", "merge release", "merge release pr", "save changes", or invokes /euclidean-sketchpad.
allowed-tools: Bash(git tag *) Bash(git add *) Bash(git commit *) Bash(git push *) Bash(gh pr *) Bash(./gradlew *) Bash(ls *) Bash(git checkout *) Bash(git pull *) Bash(git branch *) Bash(git diff *) Bash(git log *) Bash(git status *)
---

# Euclidean Sketchpad

## Routing

Parse the user's input after the skill name to determine which action to run:

| Keywords | Action |
|----------|--------|
| "deploy", "release" | [Deploy App](#deploy-app) |
| "export" | [Export Diagram](#export-diagram) |
| "merge" | [Merge Release PR](#merge-release-pr) |
| "save" | [Save Changes](#save-changes) |

If the action is ambiguous or not provided, ask the user which action they want.

Pass any remaining text after the action keywords as input to that action (e.g. `/euclidean-sketchpad deploy app as version 1.2.3` passes version `1.2.3` to Deploy App).

## Shared Principles

These apply to **Deploy App** and **Save Changes** (any action that edits tracked files):

- **No absolute paths.** Every path written to tracked files must be relative to the project root. Never write absolute filesystem paths (e.g. `/Users/...` or `/home/...`).
- **Minimal, accurate updates.** Only change what is needed. Don't rewrite sections that are already correct.
- **Run tests before committing.** Run `./gradlew clean test`. If any test fails, **stop** and report the failures. Do not commit or tag.

---

## Deploy App

Prepare a versioned release: bump version, update docs, tag, and commit.

### Inputs

Requires a **version number** (e.g. `1.0.1`, `1.1.0`, `2.0.0`).

- If the user did not provide a version number, **ask for one before proceeding**.
- Format: `<major>.<minor>.<patch>`, no `v` prefix in code (git tag uses `v` prefix).

### Version validation

1. Read the current version from `APP_VERSION` in `src/main/kotlin/euclideansketchpad/Main.kt`.
2. Compare the requested version to the current version using semver ordering:
   - **Requested < current**: warn the user that this is a downgrade and ask for confirmation.
   - **Requested == current**: check if git tag `v<version>` already exists (`git tag -l "v<version>"`). If it does, **stop** — the version is already released. If the tag does not exist, skip version-bump steps and proceed to doc review and tagging.
   - **Requested > current**: proceed with the full flow below.

### Release flow (version > current)

#### 1. Bump APP_VERSION

Edit `src/main/kotlin/euclideansketchpad/Main.kt` — change the `APP_VERSION` constant to the new version string.

#### 2. Update CHANGELOG.md

- Read `CHANGELOG.md`.
- Add a new `## [<version>] - <today's date YYYY-MM-DD>` section **above** the previous version entry.
- Populate from `git log` (last tag to HEAD) and `git diff`/`git status` (uncommitted changes).
- Group under `### Added`, `### Changed`, `### Fixed`, or `### Removed` (omit empty subsections).
- Do NOT alter existing version entries.

#### 3. Review spec and handoff

- Read `spec/SPEC.md` and `handoff/HANDOFF.md`.
- Check whether recent changes have caused either to become stale:
  - New features or behavior not yet documented.
  - Sections that describe behavior differently from the current implementation.
  - Outdated build state, test counts, or architecture notes.
- If updates are needed, make them. If everything is current, skip.

#### 4. Run tests

Run `./gradlew clean test`. If any test fails, **stop the release** and report failures.

#### 5. Git tag and commit

- Stage all changed files by name.
- Commit with message: `Release v<version>`

#### 6. Push and release

Check which branch you are on (`git branch --show-current`).

**If on `main`:**
- Create an annotated git tag: `git tag -a v<version> -m "Release v<version>"`
- Push the commit and tag: `git push --follow-tags`
- Explain that pushing the `v*` tag triggers the GitHub Actions release workflow (`.github/workflows/release.yml`), which builds macOS (.dmg) and Windows (.msi) installers and creates a GitHub Release with them attached.

**If on a feature/side branch:**
- Do **NOT** create the git tag yet.
- Push the branch: `git push -u origin <branch-name>`
- Create a pull request: `gh pr create --title "Release v<version>" --body "<brief description>"`
- Tell the user they can run **`/euclidean-sketchpad merge pr`** to merge the PR, push the tag, and clean up the branch.
- **IMPORTANT — tag must be pushed from `main`.** The release tag must point to a commit on `main` and be pushed from `main`.

---

## Export Diagram

Headless export of a diagram file to 6 images (3 modes x 2 formats).

### Inputs

- **Diagram path** (required): path to a `.json` diagram file.
- **Output directory** (optional, default `exports/`): where to write the exported files.

If the user did not provide a diagram path, **ask for one before proceeding**.

### Steps

1. Verify the diagram file exists.
2. Run the headless export:
   ```
   ./gradlew run --args="--export <diagram-path> <output-dir>"
   ```
3. Verify output with `ls -la <output-dir>/`. Expect 6 non-zero-size files:
   - `<name>_canvas.png`, `<name>_canvas.svg`
   - `<name>_custom.png`, `<name>_custom.svg`
   - `<name>_autofit.png`, `<name>_autofit.svg`
4. Report the results to the user.

---

## Merge Release PR

Merge a release PR, push the release tag from `main`, and clean up the release branch. This is the second half of the release workflow — it picks up after **Deploy App** has committed the version bump and opened a PR.

### Steps

#### 1. Find the release PR

Run `gh pr list --head "release/" --state open` to find open PRs from `release/*` branches.

- If **no release PR is found**, tell the user and stop.
- If **multiple release PRs are found**, list them and ask which one to merge.
- If **exactly one** is found, confirm it with the user (show PR number, title, branch name) before proceeding.

#### 2. Merge the PR

```
gh pr merge <number> --merge --admin
```

If the merge fails, report the error and stop.

#### 3. Switch to main and pull

```
git checkout main
git pull
```

#### 4. Create and push the release tag

Extract the version number from the PR title (expected format: `Release v<version>`). Then:

```
git tag -a v<version> -m "Release v<version>"
git push --follow-tags
```

Explain that pushing the `v*` tag triggers the GitHub Actions release workflow (`.github/workflows/release.yml`), which builds macOS (.dmg) and Windows (.msi) installers and creates a GitHub Release with them attached.

#### 5. Clean up the release branch

**Ask the user for confirmation** before deleting. Show the branch name.

If confirmed:

```
git branch -d release/<version>
git push origin --delete release/<version>
```

If declined, skip this step.

---

## Save Changes

Update documentation to reflect this session's work and commit everything.

### Steps

#### 1. Gather context

- Read `handoff/HANDOFF.md`, `spec/SPEC.md`, and `CHANGELOG.md`.
- Run `git diff` and `git status` to see what changed this session.
- Run `git log` from the last version tag to HEAD for recent committed changes.

#### 2. Update `spec/SPEC.md`

The spec is the **authoritative design reference**. Update if this session changed behavior the spec describes:

- New features or object types not yet documented.
- Sections that describe behavior differently from the current implementation.
- New or changed hit-testing tiers, tool behaviors, menu items, rendering passes, etc.

If the spec is already accurate, skip.

#### 3. Update `CHANGELOG.md`

Add entries under the **current unreleased version section** (the topmost `## [x.y.z]` entry). If no unreleased section exists, create one with today's date above the previous release.

- Group under `### Added`, `### Changed`, `### Fixed`, or `### Removed` (omit empty subsections).
- Do NOT alter existing released version entries.
- One line per change.

#### 4. Update `handoff/HANDOFF.md`

The handoff is a **concise onboarding brief**, not a changelog. It covers only what the spec doesn't: build state, implementation-specific architecture decisions, and known issues.

- `## Current State` — update build status, test count, branch, packaging version.
- `## Architecture Notes` — add notes for new patterns/gotchas. Remove stale notes. Do NOT duplicate the spec.
- `## Known Issues / Gaps` — remove fixed items, add new ones.

Do NOT add session-by-session changelogs. Completed work belongs in git history and `CHANGELOG.md`.

#### 5. Run tests

Run `./gradlew clean test`. If any test fails, **stop** and report failures.

#### 6. Stage and commit

- `git add` all changed files by name. Do not use `git add -A` or `git add .`.
- Commit with a message summarizing the session's work:
  ```
  <short summary of implementation changes>

  Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
  ```
