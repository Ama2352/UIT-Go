# Stage 1: Build
FROM node:18-alpine AS builder

WORKDIR /app

# Copy package files
COPY package*.json ./
COPY prisma ./prisma/

# Install dependencies (use mirror registry for better connectivity)
RUN npm config set registry https://registry.npmmirror.com && npm ci

# Copy source code
COPY . .

# Generate Prisma Client
RUN npx prisma generate

# Build the application
RUN npm run build

# Stage 2: Production
FROM node:18-alpine

WORKDIR /app

# Install OpenSSL (important for Prisma)
RUN apk add --no-cache openssl

# Copy package files
COPY package*.json ./
COPY prisma ./prisma/

# Install production dependencies only (use mirror registry for better connectivity)
RUN npm config set registry https://registry.npmmirror.com && npm ci --only=production

# Copy Prisma generated client from builder
COPY --from=builder /app/node_modules/.prisma ./node_modules/.prisma
COPY --from=builder /app/node_modules/@prisma ./node_modules/@prisma

# Copy built application from builder
COPY --from=builder /app/dist ./dist

EXPOSE 3000

# Run migration and start app
CMD ["sh", "-c", "npx prisma migrate deploy && node dist/main"]
