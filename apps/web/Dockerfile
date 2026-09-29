FROM node:22-alpine AS build
WORKDIR /app
COPY apps/web/package.json apps/web/package-lock.json ./
RUN npm ci
COPY apps/web/ ./
ARG VITE_IDENTITY_API_URL=/identity
ARG VITE_LEARNING_API_URL=/learning
ENV VITE_IDENTITY_API_URL=$VITE_IDENTITY_API_URL
ENV VITE_LEARNING_API_URL=$VITE_LEARNING_API_URL
RUN npm run build

FROM caddy:2-alpine
COPY --from=build /app/dist /usr/share/caddy
COPY deploy/Caddyfile /etc/caddy/Caddyfile
EXPOSE 80 443
