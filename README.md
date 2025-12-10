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
   - Can be **any string** - no Base64 encoding needed!
   - Minimum 8 characters recommended
   - 16+ characters recommended for better security
   - Examples: `my-super-secret-key-12345` or `Smartsched2024!SecureKey`
   - The system automatically converts it to a secure 32-byte key

2. **`SPRING_DATA_MONGODB_URI`** (REQUIRED)
   - MongoDB connection string
   - Default: MongoDB Atlas connection (configured in application.properties)

3. **`CORS_ALLOWED_ORIGINS`** (OPTIONAL)
   - Comma-separated list of allowed origins
   - Default includes: `https://smartsched-client.vercel.app`

