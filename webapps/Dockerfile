FROM docker.m.daocloud.io/library/node:22-alpine AS build
WORKDIR /app
RUN npm install -g pnpm@11.10.0
ARG APP
COPY pnpm-workspace.yaml pnpm-lock.yaml ./
COPY customer-app/package.json customer-app/package.json
COPY kb-app/package.json kb-app/package.json
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm --filter ./${APP} build

FROM docker.m.daocloud.io/library/nginx:alpine
ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata \
    && ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo Asia/Shanghai > /etc/timezone
ARG APP
COPY --from=build /app/${APP}/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
