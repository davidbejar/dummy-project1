# Frontend image, used by the DAST stack (compose.dast.yaml): the production build served
# by nginx, which also proxies /api to the backend so the app stays same-origin.
FROM docker.io/denoland/deno:2.9.8 AS build
WORKDIR /app
COPY deno.json deno.lock ./
RUN deno install --frozen
COPY . .
# Same as `deno task build`, but with -A: on Linux, Vite's WSL check reads
# /proc/sys/fs/binfmt_misc/WSLInterop, which Deno only allows with --allow-all.
RUN deno task check && deno run -A npm:vite build

FROM docker.io/library/nginx:1.30-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
