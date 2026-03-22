# Backend Changes - API Endpoints & Security

## Document Information

- **Version:** 1.0
- **Date:** 2026-02-04
- **Purpose:** Define all backend changes required for native authentication

---

## Overview

This document specifies the **3 new API endpoints** required to support native Android authentication, along with security considerations, request/response contracts, and implementation details.

---

## New API Endpoints

### Endpoint 1: Google Sign-In Verification

**File:** `src/app/api/auth/native/google/route.js`

**Purpose:** Verify Google ID token from Android SDK and authenticate user

#### Request Specification

```http
POST /api/auth/native/google
Content-Type: application/json
```

**Request Body:**
```json
{
  "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjdlMDNkODEyMTQ3ZmY..."
}
```

**Field Validation:**
- `idToken` (required): String, non-empty, valid JWT format

#### Response Specification

**Success Response (200):**
```json
{
  "success": true,
  "token": "pub_a1b2c3d4e5f6g7h8i9j0",
  "onboarded": false,
  "user": {
    "id": "clx123abc456def789",
    "email": "user@gmail.com",
    "name": "John Doe",
    "image": "https://lh3.googleusercontent.com/a/..."
  }
}
```

**Error Response (400/401):**
```json
{
  "success": false,
  "error": "Invalid ID token"
}
```

**Error Response (500):**
```json
{
  "success": false,
  "error": "Internal server error"
}
```

#### Implementation Logic

```javascript
import { NextResponse } from 'next/server';
import { OAuth2Client } from 'google-auth-library';
import prisma from '@/lib/prisma';
import { generatePublicToken } from '@/lib/token';

const client = new OAuth2Client(process.env.GOOGLE_CLIENT_ID);

export async function POST(request) {
  try {
    // 1. Parse request body
    const { idToken } = await request.json();
    
    if (!idToken) {
      return NextResponse.json(
        { success: false, error: 'ID token is required' },
        { status: 400 }
      );
    }

    // 2. Verify ID token with Google
    const ticket = await client.verifyIdToken({
      idToken,
      audience: process.env.GOOGLE_CLIENT_ID,
    });
    
    const payload = ticket.getPayload();
    const email = payload.email;
    const name = payload.name;
    const image = payload.picture;

    // 3. Check if user exists
    let user = await prisma.user.findUnique({
      where: { email },
      select: {
        id: true,
        email: true,
        name: true,
        image: true,
        publicToken: true,
        onboarded: true,
      },
    });

    // 4. Create user if new
    if (!user) {
      user = await prisma.user.create({
        data: {
          email,
          name,
          image,
          publicToken: generatePublicToken(),
          emailVerified: new Date(),
          onboarded: false,
        },
        select: {
          id: true,
          email: true,
          name: true,
          image: true,
          publicToken: true,
          onboarded: true,
        },
      });
    }

    // 5. Ensure publicToken exists (for old users)
    if (!user.publicToken) {
      const newToken = generatePublicToken();
      await prisma.user.update({
        where: { id: user.id },
        data: { publicToken: newToken },
      });
      user.publicToken = newToken;
    }

    // 6. Return success response
    return NextResponse.json({
      success: true,
      token: user.publicToken,
      onboarded: user.onboarded,
      user: {
        id: user.id,
        email: user.email,
        name: user.name,
        image: user.image,
      },
    });

  } catch (error) {
    console.error('[Google Auth Error]:', error);
    
    if (error.message.includes('Token used too late')) {
      return NextResponse.json(
        { success: false, error: 'ID token expired' },
        { status: 401 }
      );
    }
    
    return NextResponse.json(
      { success: false, error: 'Invalid ID token' },
      { status: 401 }
    );
  }
}
```

#### Security Considerations

✅ **Token Verification:**
- ID token signature verified with Google's public keys
- Token audience checked against your client ID
- Token expiration validated

✅ **Error Handling:**
- Never expose internal error details
- Log errors server-side for debugging
- Return generic error messages to client

✅ **Rate Limiting:**
- Implement rate limiting (e.g., 10 requests/minute per IP)
- Prevent brute force attacks

---

### Endpoint 2: Email Signup

**File:** `src/app/api/auth/native/email-signup/route.js`

**Purpose:** Register new user with email and password

#### Request Specification

```http
POST /api/auth/native/email-signup
Content-Type: application/json
```

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "SecurePass123",
  "name": "John Doe"
}
```

**Field Validation:**
- `email` (required): Valid email format, max 255 chars
- `password` (required): Min 8 chars, max 128 chars
- `name` (required): Min 2 chars, max 100 chars

#### Response Specification

**Success Response (201):**
```json
{
  "success": true,
  "token": "pub_x7y8z9a1b2c3d4e5f6",
  "onboarded": false,
  "user": {
    "id": "clx456def789ghi012",
    "email": "user@example.com",
    "name": "John Doe"
  }
}
```

**Error Response (400):**
```json
{
  "success": false,
  "error": "Email already exists"
}
```

**Error Response (422):**
```json
{
  "success": false,
  "error": "Password must be at least 8 characters"
}
```

#### Implementation Logic

```javascript
import { NextResponse } from 'next/server';
import bcrypt from 'bcryptjs';
import prisma from '@/lib/prisma';
import { generatePublicToken } from '@/lib/token';

export async function POST(request) {
  try {
    // 1. Parse and validate request
    const { email, password, name } = await request.json();

    // Validation
    if (!email || !password || !name) {
      return NextResponse.json(
        { success: false, error: 'All fields are required' },
        { status: 400 }
      );
    }

    // Email format validation
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      return NextResponse.json(
        { success: false, error: 'Invalid email format' },
        { status: 422 }
      );
    }

    // Password strength validation
    if (password.length < 8) {
      return NextResponse.json(
        { success: false, error: 'Password must be at least 8 characters' },
        { status: 422 }
      );
    }

    // Name validation
    if (name.trim().length < 2) {
      return NextResponse.json(
        { success: false, error: 'Name must be at least 2 characters' },
        { status: 422 }
      );
    }

    // 2. Check if email already exists
    const existing = await prisma.user.findUnique({
      where: { email: email.toLowerCase() },
    });

    if (existing) {
      return NextResponse.json(
        { success: false, error: 'Email already exists' },
        { status: 400 }
      );
    }

    // 3. Hash password
    const hashedPassword = await bcrypt.hash(password, 10);

    // 4. Create user
    const user = await prisma.user.create({
      data: {
        email: email.toLowerCase(),
        name: name.trim(),
        password: hashedPassword,
        publicToken: generatePublicToken(),
        onboarded: false,
      },
      select: {
        id: true,
        email: true,
        name: true,
        publicToken: true,
        onboarded: true,
      },
    });

    // 5. Return success response
    return NextResponse.json(
      {
        success: true,
        token: user.publicToken,
        onboarded: user.onboarded,
        user: {
          id: user.id,
          email: user.email,
          name: user.name,
        },
      },
      { status: 201 }
    );

  } catch (error) {
    console.error('[Email Signup Error]:', error);
    return NextResponse.json(
      { success: false, error: 'Internal server error' },
      { status: 500 }
    );
  }
}
```

#### Security Considerations

✅ **Password Hashing:**
- Use bcrypt with salt rounds = 10
- Never store plain text passwords
- Never log passwords

✅ **Email Normalization:**
- Convert to lowercase before storage
- Trim whitespace
- Validate format

✅ **Input Sanitization:**
- Trim all string inputs
- Validate lengths
- Prevent SQL injection (Prisma handles this)

---

### Endpoint 3: Email Login

**File:** `src/app/api/auth/native/email-login/route.js`

**Purpose:** Authenticate existing user with email and password

#### Request Specification

```http
POST /api/auth/native/email-login
Content-Type: application/json
```

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "SecurePass123"
}
```

**Field Validation:**
- `email` (required): String, non-empty
- `password` (required): String, non-empty

#### Response Specification

**Success Response (200):**
```json
{
  "success": true,
  "token": "pub_x7y8z9a1b2c3d4e5f6",
  "onboarded": true,
  "user": {
    "id": "clx456def789ghi012",
    "email": "user@example.com",
    "name": "John Doe"
  }
}
```

**Error Response (401):**
```json
{
  "success": false,
  "error": "Invalid credentials"
}
```

#### Implementation Logic

```javascript
import { NextResponse } from 'next/server';
import bcrypt from 'bcryptjs';
import prisma from '@/lib/prisma';
import { generatePublicToken } from '@/lib/token';

export async function POST(request) {
  try {
    // 1. Parse request
    const { email, password } = await request.json();

    if (!email || !password) {
      return NextResponse.json(
        { success: false, error: 'Email and password are required' },
        { status: 400 }
      );
    }

    // 2. Find user by email
    const user = await prisma.user.findUnique({
      where: { email: email.toLowerCase() },
      select: {
        id: true,
        email: true,
        name: true,
        password: true,
        publicToken: true,
        onboarded: true,
      },
    });

    // 3. Check if user exists
    if (!user || !user.password) {
      return NextResponse.json(
        { success: false, error: 'Invalid credentials' },
        { status: 401 }
      );
    }

    // 4. Verify password
    const isValid = await bcrypt.compare(password, user.password);

    if (!isValid) {
      return NextResponse.json(
        { success: false, error: 'Invalid credentials' },
        { status: 401 }
      );
    }

    // 5. Ensure publicToken exists
    let token = user.publicToken;
    if (!token) {
      token = generatePublicToken();
      await prisma.user.update({
        where: { id: user.id },
        data: { publicToken: token },
      });
    }

    // 6. Return success response
    return NextResponse.json({
      success: true,
      token,
      onboarded: user.onboarded,
      user: {
        id: user.id,
        email: user.email,
        name: user.name,
      },
    });

  } catch (error) {
    console.error('[Email Login Error]:', error);
    return NextResponse.json(
      { success: false, error: 'Internal server error' },
      { status: 500 }
    );
  }
}
```

#### Security Considerations

✅ **Timing Attack Prevention:**
- Always return same error message for invalid email or password
- Don't reveal whether email exists or password is wrong

✅ **Brute Force Protection:**
- Implement rate limiting (e.g., 5 failed attempts → 15 min lockout)
- Log failed login attempts
- Consider CAPTCHA after multiple failures

✅ **Password Comparison:**
- Use bcrypt.compare() (constant-time comparison)
- Never compare passwords directly

---

## Existing Endpoints (No Changes Required)

### NextAuth Endpoints

**These remain unchanged:**

1. `/api/auth/[...nextauth]` - NextAuth handler
2. `/api/auth/signin/google` - Google OAuth (for web users)
3. `/api/onboarding/complete` - Onboarding completion

**Why no changes?**
- Native auth uses separate endpoints
- Web auth continues to work as before
- Token-based recovery provider already supports native auth

---

## Database Schema (No Changes Required)

### User Table

**Current schema is sufficient:**

```prisma
model User {
  id          String   @id @default(cuid())
  email       String   @unique
  password    String?  // For email/password auth
  name        String?
  image       String?
  publicToken String   @unique  // For native auth
  onboarded   Boolean  @default(false)
  emailVerified DateTime?
  // ... other fields
}
```

**All required fields already exist:**
- ✅ `publicToken` - For mobile authentication
- ✅ `onboarded` - For routing logic
- ✅ `password` - For email/password auth
- ✅ `emailVerified` - For OAuth users

**No migrations needed!**

---

## Security Best Practices

### 1. Environment Variables

**Required in `.env`:**
```env
# Google OAuth
GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-client-secret

# NextAuth
NEXTAUTH_SECRET=your-nextauth-secret
NEXTAUTH_URL=https://consistencygrid.netlify.app

# Database
DATABASE_URL=postgresql://...
```

**Never commit `.env` to version control!**

### 2. HTTPS Only

- All API endpoints must use HTTPS in production
- Redirect HTTP to HTTPS
- Use HSTS headers

### 3. CORS Configuration

**Not required for native apps:**
- Native Android apps don't have CORS restrictions
- Web app already has proper CORS setup

### 4. Rate Limiting

**Recommended limits:**
- Google Sign-In: 10 requests/minute per IP
- Email Signup: 5 requests/hour per IP
- Email Login: 10 requests/minute per IP

**Implementation:**
```javascript
import rateLimit from 'express-rate-limit';

const limiter = rateLimit({
  windowMs: 60 * 1000, // 1 minute
  max: 10, // 10 requests per minute
  message: 'Too many requests, please try again later',
});
```

### 5. Input Validation

**Always validate:**
- Email format
- Password strength
- Name length
- Token format

**Never trust client input!**

### 6. Error Logging

**Log all errors server-side:**
```javascript
console.error('[Auth Error]:', {
  endpoint: '/api/auth/native/google',
  error: error.message,
  timestamp: new Date().toISOString(),
});
```

**Never expose internal errors to client!**

---

## API Testing

### Test Cases

#### Google Sign-In Endpoint

```bash
# Valid ID token
curl -X POST https://consistencygrid.netlify.app/api/auth/native/google \
  -H "Content-Type: application/json" \
  -d '{"idToken":"eyJhbGci..."}'

# Expected: 200 with token and user data

# Invalid ID token
curl -X POST https://consistencygrid.netlify.app/api/auth/native/google \
  -H "Content-Type: application/json" \
  -d '{"idToken":"invalid"}'

# Expected: 401 with error message
```

#### Email Signup Endpoint

```bash
# Valid signup
curl -X POST https://consistencygrid.netlify.app/api/auth/native/email-signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"SecurePass123","name":"Test User"}'

# Expected: 201 with token and user data

# Duplicate email
curl -X POST https://consistencygrid.netlify.app/api/auth/native/email-signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"SecurePass123","name":"Test User"}'

# Expected: 400 with "Email already exists"
```

#### Email Login Endpoint

```bash
# Valid login
curl -X POST https://consistencygrid.netlify.app/api/auth/native/email-login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"SecurePass123"}'

# Expected: 200 with token and user data

# Invalid password
curl -X POST https://consistencygrid.netlify.app/api/auth/native/email-login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"WrongPass"}'

# Expected: 401 with "Invalid credentials"
```

---

## Dependencies

### New Dependencies Required

```json
{
  "dependencies": {
    "google-auth-library": "^9.0.0",
    "bcryptjs": "^2.4.3"
  }
}
```

**Installation:**
```bash
npm install google-auth-library bcryptjs
```

**Note:** `bcryptjs` may already be installed for existing email/password auth.

---

## Summary

### New Files Created

1. `src/app/api/auth/native/google/route.js`
2. `src/app/api/auth/native/email-signup/route.js`
3. `src/app/api/auth/native/email-login/route.js`

### Existing Files Modified

**NONE** - All changes are additive

### Database Changes

**NONE** - Existing schema is sufficient

### Security Measures

✅ ID token verification with Google  
✅ Password hashing with bcrypt  
✅ Input validation  
✅ Rate limiting  
✅ Error logging  
✅ HTTPS enforcement  

---

## Next Document

→ **06-android-changes.md** - Android component specifications
