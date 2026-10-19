FROM node:22.14.0-bookworm-slim
WORKDIR /tests
COPY config/panel/package*.json ./
RUN npm ci --ignore-scripts --no-audit --no-fund
RUN npx playwright install --with-deps --only-shell chromium
COPY config/panel ./
ENTRYPOINT ["npx", "playwright", "test"]
