# Frontend — UBIT Mentorship Hub

React + TypeScript + Vite single-page app. See the [root README](../README.md) for
project-wide setup and status.

## Commands

| Command | Purpose |
| --- | --- |
| `npm install` | Install dependencies |
| `npm run dev` | Dev server on <http://localhost:5173> |
| `npm run build` | Type-check (`tsc -b`) then build to `dist/` |
| `npm run typecheck` | Type-check only |
| `npm run lint` | Lint with oxlint |
| `npm run preview` | Serve the production build locally |

## Conventions

- Feature-oriented folders live in `src/features/<feature>/`; a feature owns its
  components, hooks, and types.
- `src/components`, `src/layouts`, `src/services`, `src/hooks`, `src/context`,
  `src/types`, and `src/utils` hold only genuinely shared code.
- Import shared modules through the `@/` alias (`@/services/...` → `src/services/...`).
- In development, `/api` requests are proxied to the Spring Boot backend on
  `http://localhost:8080` (see `vite.config.ts`).
