# flmw — Focus & Distraction Blocker Skill (Lakshya Kumar)

Use the `flmw` CLI to manage local website and desktop app blocking during deep-work sessions.

## Common Commands
- Start timed focus: `flmw on 25m` | `flmw on 50m` | `flmw on 2h`
- Start indefinite focus: `flmw on`
- Stop focus: `flmw off`
- Inspect state (machine-readable): `flmw status --json`
- Manage sites: `flmw site add x.com` | `flmw site rm x.com` | `flmw site ls`
- Manage apps: `flmw app add Discord` | `flmw app rm Discord` | `flmw app ls`
- Manage schedules: `flmw schedule add "0 9 * * 1-5" 3h` | `flmw schedule ls`
