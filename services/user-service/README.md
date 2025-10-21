# User Service - Production-Ready Microservice

A complete, production-ready UserService microservice built with NestJS, Prisma, and PostgreSQL. This service provides user authentication, authorization, and profile management with JWT-based authentication and secure password handling.

## Features

- ✅ User registration and authentication
- ✅ JWT-based access control with database token validation
- ✅ Password hashing with bcrypt
- ✅ Password reset flow with time-limited tokens
- ✅ Profile management (view and update)
- ✅ Token lifecycle management (login/logout)
- ✅ Request logging with Morgan
- ✅ Input validation with class-validator
- ✅ Docker support with PostgreSQL
- ✅ Database migrations with Prisma

## Tech Stack

- **NestJS** (TypeScript) - Backend framework
- **Prisma ORM** - Database ORM
- **PostgreSQL** - Database
- **JWT** - Authentication tokens
- **bcryptjs** - Password hashing
- **class-validator** - DTO validation
- **Morgan** - Request logging

## Prerequisites

- Node.js >= 18.0.0
- Docker and Docker Compose (for containerized setup)
- PostgreSQL (if running without Docker)

## Getting Started

### 1. Clone and Install

```bash
# Clone the repository
git clone <repository-url>
cd user-service

# Install dependencies
npm install
```

### 2. Environment Configuration

```bash
# Copy the environment example file
cp .env.example .env
```

Edit `.env` with your configuration:

```env
# Application
NODE_ENV=development
PORT=3000

# Database
DATABASE_URL="postgresql://postgres:postgres@localhost:5432/userservice?schema=public"

# JWT
JWT_SECRET="your-super-secret-jwt-key-change-this-in-production"
JWT_EXPIRES_IN=1d
```

### 3. Database Setup

#### Option A: Using Docker Compose (Recommended)

```bash
# Start PostgreSQL with Docker Compose
docker-compose up -d postgres

# Wait for PostgreSQL to be ready, then run migrations
npx prisma generate
npx prisma migrate dev --name init

# Start the application
npm run start:dev
```

#### Option B: Using Local PostgreSQL

```bash
# Ensure PostgreSQL is running locally
# Update DATABASE_URL in .env to point to your local instance

# Generate Prisma Client
npx prisma generate

# Run migrations
npx prisma migrate dev --name init

# Start the application
npm run start:dev
```

### 4. Run with Docker (Full Stack)

```bash
# Build and start all services
docker-compose up --build

# The application will run on http://localhost:3000
# Database migrations run automatically on container startup
```

## Project Structure

```
user-service/
├── prisma/
│   └── schema.prisma           # Database schema
├── src/
│   ├── auth/
│   │   ├── auth.module.ts      # Auth module
│   │   ├── auth.service.ts     # Auth business logic
│   │   ├── jwt.strategy.ts     # JWT passport strategy
│   │   └── jwt-auth.guard.ts   # JWT authentication guard
│   ├── common/
│   │   ├── decorators/
│   │   │   └── current-user.decorator.ts  # User decorator
│   │   └── middleware/
│   │       └── logger.middleware.ts       # Request logging
│   ├── prisma/
│   │   ├── prisma.module.ts    # Prisma module
│   │   └── prisma.service.ts   # Prisma service
│   ├── user/
│   │   ├── dto/
│   │   │   ├── create-user.dto.ts
│   │   │   ├── login.dto.ts
│   │   │   ├── update-user.dto.ts
│   │   │   ├── forgot-password.dto.ts
│   │   │   └── reset-password.dto.ts
│   │   ├── user.controller.ts  # User endpoints
│   │   ├── user.service.ts     # User business logic
│   │   └── user.module.ts      # User module
│   ├── app.module.ts           # Root module
│   └── main.ts                 # Application entry point
├── .env.example                # Environment variables template
├── docker-compose.yml          # Docker Compose configuration
├── Dockerfile                  # Docker image configuration
├── package.json                # Dependencies and scripts
└── README.md                   # This file
```

## API Endpoints

### User Registration

**POST** `/users`

Register a new user account.

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "password123",
  "fullName": "John Doe",
  "phoneNumber": "+1234567890",
  "userType": "PASSENGER"
}
```

**Response:** `201 Created`
```json
{
  "id": "uuid",
  "email": "user@example.com",
  "fullName": "John Doe",
  "phoneNumber": "+1234567890",
  "userType": "PASSENGER",
  "createdAt": "2025-10-21T12:00:00.000Z",
  "updatedAt": "2025-10-21T12:00:00.000Z"
}
```

**cURL Example:**
```bash
curl -X POST http://localhost:3000/users \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123",
    "fullName": "John Doe",
    "phoneNumber": "+1234567890",
    "userType": "PASSENGER"
  }'
```

### Login

**POST** `/sessions`

Authenticate and receive an access token.

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "password123",
  "deviceInfo": "iPhone 13"
}
```

**Response:** `200 OK`
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**cURL Example:**
```bash
curl -X POST http://localhost:3000/sessions \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123",
    "deviceInfo": "iPhone 13"
  }'
```

### Get Current User Profile

**GET** `/users/me`

Get the authenticated user's profile.

**Headers:**
- `Authorization: Bearer <access_token>`

**Response:** `200 OK`
```json
{
  "id": "uuid",
  "email": "user@example.com",
  "fullName": "John Doe",
  "phoneNumber": "+1234567890",
  "userType": "PASSENGER",
  "createdAt": "2025-10-21T12:00:00.000Z",
  "updatedAt": "2025-10-21T12:00:00.000Z"
}
```

**cURL Example:**
```bash
curl -X GET http://localhost:3000/users/me \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

### Update User Profile

**PUT** `/users/me`

Update the authenticated user's profile.

**Headers:**
- `Authorization: Bearer <access_token>`

**Request Body:**
```json
{
  "fullName": "Jane Doe",
  "phoneNumber": "+9876543210"
}
```

**Response:** `200 OK`
```json
{
  "id": "uuid",
  "email": "user@example.com",
  "fullName": "Jane Doe",
  "phoneNumber": "+9876543210",
  "userType": "PASSENGER",
  "createdAt": "2025-10-21T12:00:00.000Z",
  "updatedAt": "2025-10-21T12:30:00.000Z"
}
```

**cURL Example:**
```bash
curl -X PUT http://localhost:3000/users/me \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Jane Doe",
    "phoneNumber": "+9876543210"
  }'
```

### Get User by ID

**GET** `/users/:id`

Get any user's profile by ID (requires authentication).

**Headers:**
- `Authorization: Bearer <access_token>`

**Response:** `200 OK`
```json
{
  "id": "uuid",
  "email": "user@example.com",
  "fullName": "John Doe",
  "phoneNumber": "+1234567890",
  "userType": "PASSENGER",
  "createdAt": "2025-10-21T12:00:00.000Z",
  "updatedAt": "2025-10-21T12:00:00.000Z"
}
```

**cURL Example:**
```bash
curl -X GET http://localhost:3000/users/123e4567-e89b-12d3-a456-426614174000 \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

### Logout

**POST** `/sessions/logout`

Logout and invalidate the current access token.

**Headers:**
- `Authorization: Bearer <access_token>`

**Response:** `200 OK`
```json
{
  "message": "Successfully logged out"
}
```

**cURL Example:**
```bash
curl -X POST http://localhost:3000/sessions/logout \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

### Forgot Password

**POST** `/sessions/forgot-password`

Request a password reset token.

**Request Body:**
```json
{
  "email": "user@example.com"
}
```

**Response:** `200 OK`
```json
{
  "message": "If the email exists, a password reset link has been sent",
  "resetToken": "abc123-def456-ghi789"
}
```

**Note:** In production, the `resetToken` should be sent via email and not returned in the response.

**cURL Example:**
```bash
curl -X POST http://localhost:3000/sessions/forgot-password \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com"
  }'
```

### Reset Password

**POST** `/sessions/reset-password`

Reset password using the reset token.

**Request Body:**
```json
{
  "token": "abc123-def456-ghi789",
  "newPassword": "newpassword123"
}
```

**Response:** `200 OK`
```json
{
  "message": "Password successfully reset"
}
```

**Note:** This invalidates all existing access tokens for the user.

**cURL Example:**
```bash
curl -X POST http://localhost:3000/sessions/reset-password \
  -H "Content-Type: application/json" \
  -d '{
    "token": "abc123-def456-ghi789",
    "newPassword": "newpassword123"
  }'
```

## Error Responses

All errors follow a consistent JSON format:

```json
{
  "statusCode": 400,
  "message": "Error message here",
  "error": "Bad Request"
}
```

Common status codes:
- `400` - Bad Request (validation errors)
- `401` - Unauthorized (invalid/expired token)
- `404` - Not Found (resource doesn't exist)
- `409` - Conflict (duplicate email/phone)
- `500` - Internal Server Error

## Database Schema

### User Model

```prisma
model User {
  id        String   @id @default(uuid())
  email     String   @unique
  password  String
  fullName  String
  phoneNumber String? @unique
  userType  UserType @default(PASSENGER)
  
  resetToken          String?   @unique
  resetTokenExpiresAt DateTime?
  
  createdAt DateTime @default(now())
  updatedAt DateTime @updatedAt
  
  accessTokens AccessToken[]
}
```

### AccessToken Model

```prisma
model AccessToken {
  id        String   @id @default(uuid())
  token     String   @unique
  userId    String
  expiresAt DateTime
  deviceInfo String?
  ipAddress  String?
  createdAt DateTime @default(now())
  
  user User @relation(fields: [userId], references: [id], onDelete: Cascade)
}
```

## Scripts

- `npm run dev` - Start development server with watch mode
- `npm run start:dev` - Same as dev
- `npm run build` - Build for production
- `npm run start:prod` - Start production server
- `npm run prisma:generate` - Generate Prisma Client
- `npm run prisma:migrate` - Run database migrations (dev)
- `npm run prisma:studio` - Open Prisma Studio (database GUI)

## Security Features

1. **Password Hashing**: All passwords are hashed with bcrypt (salt rounds: 10)
2. **JWT Authentication**: Tokens are signed and verified
3. **Database Token Validation**: Each request validates token exists in DB and is not expired
4. **Token Expiration**: Access tokens expire after 24 hours
5. **Reset Token Expiration**: Password reset tokens expire after 1 hour
6. **Token Revocation**: Logout deletes token from database
7. **Unique Constraints**: Email and phone number uniqueness enforced at DB level
8. **Input Validation**: All inputs validated with class-validator
9. **CORS Enabled**: Cross-origin requests allowed (configure in production)

## Production Considerations

### Security Enhancements

1. **Email Service**: Replace in-development reset token response with actual email sending (SendGrid, AWS SES, etc.)
2. **Rate Limiting**: Add `@nestjs/throttler` to prevent brute force attacks
3. **HTTPS**: Use HTTPS in production
4. **JWT Secret**: Use a strong, random JWT_SECRET
5. **CORS**: Configure CORS to only allow specific origins
6. **Helmet**: Add `helmet` middleware for security headers

### Monitoring & Logging

1. Add structured logging (Winston, Pino)
2. Add application monitoring (DataDog, New Relic, etc.)
3. Add error tracking (Sentry)
4. Add health check endpoints

### Performance

1. Add Redis for token caching
2. Add database connection pooling
3. Add request caching where appropriate
4. Add database indexes on frequently queried fields

### Deployment

1. Use environment-specific configuration
2. Set up CI/CD pipeline
3. Use container orchestration (Kubernetes, ECS, etc.)
4. Set up database backups and disaster recovery

## Development

### Running Locally

```bash
# Start database
docker-compose up -d postgres

# Install dependencies
npm install

# Generate Prisma Client
npx prisma generate

# Run migrations
npx prisma migrate dev --name init

# Start development server
npm run start:dev
```

### Database Management

```bash
# Create a new migration
npx prisma migrate dev --name migration_name

# Reset database (WARNING: deletes all data)
npx prisma migrate reset

# Open Prisma Studio (database GUI)
npx prisma studio
```

## Testing the Service

### Complete Test Flow

```bash
# 1. Register a user
curl -X POST http://localhost:3000/users \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "password123",
    "fullName": "Test User",
    "phoneNumber": "+1234567890",
    "userType": "PASSENGER"
  }'

# 2. Login
TOKEN=$(curl -X POST http://localhost:3000/sessions \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "password123"
  }' | jq -r '.access_token')

# 3. Get profile
curl -X GET http://localhost:3000/users/me \
  -H "Authorization: Bearer $TOKEN"

# 4. Update profile
curl -X PUT http://localhost:3000/users/me \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Updated Name"
  }'

# 5. Request password reset
RESET=$(curl -X POST http://localhost:3000/sessions/forgot-password \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com"
  }' | jq -r '.resetToken')

# 6. Reset password
curl -X POST http://localhost:3000/sessions/reset-password \
  -H "Content-Type: application/json" \
  -d "{
    \"token\": \"$RESET\",
    \"newPassword\": \"newpassword123\"
  }"

# 7. Login with new password
curl -X POST http://localhost:3000/sessions \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "newpassword123"
  }'
```

## Troubleshooting

### Database Connection Issues

If you can't connect to the database:

1. Ensure PostgreSQL is running: `docker-compose ps`
2. Check DATABASE_URL in `.env` is correct
3. Try restarting the database: `docker-compose restart postgres`

### Migration Issues

If migrations fail:

1. Ensure database is accessible
2. Check Prisma schema syntax
3. Reset database if needed: `npx prisma migrate reset`

### Port Already in Use

If port 3000 is already in use:

1. Change `PORT` in `.env`
2. Or kill the process using port 3000: `lsof -ti:3000 | xargs kill`

## License

MIT

## Support

For issues, questions, or contributions, please open an issue in the repository.
