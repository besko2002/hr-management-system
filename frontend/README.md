# HR Management System: frontend

React 18 + TypeScript + Vite. Plain CSS, no UI library.

```bash
npm install
npm run dev            # http://localhost:5175, proxies /api to http://localhost:8091
npm run test -- --run  # Vitest + Testing Library
npm run lint && npm run build
```

The API client (`src/api/`) adds the bearer token, parses the backend `ApiError` JSON and ends the session on a 401.
Menus and routes follow the user's role; salary is rendered only when the API payload contains it.
