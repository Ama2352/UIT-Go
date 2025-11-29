# User Service

NestJS service for registration, login, profile management, and password recovery.

## Tech stack
- NestJS + Passport JWT
- Prisma ORM with PostgreSQL
- Bcrypt password hashing
- RSA JWT (RS256) shared with downstream consumers

## How to run
```
npm install
npx prisma migrate deploy
npm run start:dev
```

Docker:
```
docker-compose up --build user-service
```

## Auth endpoints
- `POST /users` register
- `POST /sessions` login (returns RSA JWT)
- `POST /sessions/logout` revoke current token
- `GET /users/me` fetch profile
- `PUT /users/me` update profile
- `POST /sessions/forgot-password`
- `POST /sessions/reset-password`
- `GET /users/ping` health string

Tokens issued here must be passed to other services (notification, driver, etc.) as Bearer tokens or `token` query parameters so they can validate with the shared public key.
