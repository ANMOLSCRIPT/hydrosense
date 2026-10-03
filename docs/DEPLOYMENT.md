# Deployment (Vercel)

One Vercel project serves both the static frontend and the FastAPI backend.

- `vercel.json` builds `frontend/` into `frontend/dist` and rewrites `/api/*` and `/docs` to the Python function `api/index.py`, which imports the FastAPI app from `backend/app`.
- Every other path falls back to `index.html`, so client-side routes such as `/explore/DEMO-04` survive a refresh.
- Python dependencies come from the root `requirements.txt`.
- `regions` in `vercel.json` pins the function to `hnd1` (Tokyo), next to this project's Supabase database in `ap-northeast-1`. If your database is elsewhere, change it to the nearest Vercel region.

```bash
npm i -g vercel
vercel link
vercel env add SUPABASE_URL production
vercel env add SUPABASE_SERVICE_ROLE_KEY production
vercel env add VITE_SUPABASE_URL production
vercel env add VITE_SUPABASE_ANON_KEY production
vercel deploy --prod
```

Optional: `INGEST_API_KEY`, `ANTHROPIC_API_KEY`, `LLM_MODEL`, `CORS_ORIGINS`, `VITE_GITHUB_URL`.

`VITE_API_URL` is left empty because the API is on the same origin. Set it only if you host the backend elsewhere.

`VITE_*` variables are compiled into the frontend bundle, so redeploy after changing them.

## Checks after deploying

```bash
curl https://<your-app>.vercel.app/api/health     # "store": "supabase", "database": "connected"
```

If `store` is `memory`, the Supabase variables are missing and the API is serving its built-in demo dataset without persistence.
