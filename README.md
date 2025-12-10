# SmartSched API

Short description: Spring Boot backend that exposes REST endpoints for managing teachers, classrooms, sections, and users, and generates optimized class schedules with Timefold AI, secured with JWT-based roles.

## At a Glance

- Spring Boot 3 / Java 21 with MongoDB persistence
- AI schedule generation via Timefold Solver
- JWT auth with ADMIN and SCHEDULER roles
- Excel export and status polling for schedule jobs

## Required Environment Variables

### For Render Deployment:

1. **`JWT_SECRET`** (REQUIRED)
   - Must be a valid Base64-encoded string
   - Must be at least 32 bytes (256 bits) after decoding
   - Generate using: `openssl rand -base64 32`
   - See `GENERATE_JWT_SECRET.md` for detailed instructions
   - ⚠️ **Common Error**: If you see "Illegal base64 character", your secret is not properly Base64 encoded

2. **`SPRING_DATA_MONGODB_URI`** (REQUIRED)
   - MongoDB connection string
   - Default: MongoDB Atlas connection (configured in application.properties)

3. **`CORS_ALLOWED_ORIGINS`** (OPTIONAL)
   - Comma-separated list of allowed origins
   - Default includes: `https://smartsched-client.vercel.app`

