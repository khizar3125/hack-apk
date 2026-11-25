# OpenCode Agent (free coding agent)

Free GitHub Actions pipeline: a prompt in, a working app out.
Runner: `ubuntu-latest` (4 vCPU / 16 GB RAM) — unlimited minutes on this public repo.

Flow:
Ollama + Qwen2.5-Coder-7B (local, no API keys) + [OpenCode](https://opencode.ai)
runs inside the runner, builds the app from your prompt, runs shell commands
itself until builds/tests pass, then commits everything to `runs/<run_id>/`
and uploads an artifact.

## Usage

Via `gh` CLI:

    gh workflow run opencode-agent.yml \
      --repo khizar3125/hack-apk \
      -f prompt="Create a Python CLI todo app with add/list/done commands + pytest tests, run the tests until they pass"

Or the Actions tab → "OpenCode Agent" → Run workflow.

Options:
- `prompt` (required) — what to build. Be specific: language, features, tests.
- `model` (optional) — any Ollama tag, e.g. `qwen2.5-coder:14b-instruct-q4_k_m` (slower but stronger; fits in 16 GB RAM).

## Notes
- Public repo → GitHub Actions minutes are free/unlimited for standard runners.
- One run at a time (concurrency group).
- Results land in `runs/<run_id>/` on the default branch + downloadable artifact.
- Max job length 90 min. Big builds: keep prompts scoped.
