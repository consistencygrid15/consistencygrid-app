# Testing Plan - Comprehensive Testing Strategy

## Document Information

- **Version:** 1.0
- **Date:** 2026-02-04
- **Purpose:** Define all testing requirements for native authentication migration

---

## Testing Overview

### Testing Pyramid

```
         ┌─────────────┐
         │   E2E Tests │  (10% - Critical user flows)
         └─────────────┘
       ┌───────────────────┐
       │ Integration Tests │  (30% - Component interaction)
       └───────────────────┘
    ┌────────────────────────┐
    │     Unit Tests         │  (60% - Individual functions)
    └────────────────────────┘
```

---

## Unit Tests

### Backend Unit Tests

#### Test: Google ID Token Verification

**File:** `__tests__/api/auth/native/google.test.js`

**Test Cases:**
- [ ] Valid ID token returns success
- [ ] Invalid ID token returns 401 error
- [ ] Expired ID token returns 401 error
- [ ] Missing ID token returns 400 error
- [ ] New user is created with onboarded = false
- [ ] Existing user returns existing data
- [ ] publicToken is generated for new users
- [ ] publicToken is returned for existing users

---

#### Test: Email Signup

**File:** `__tests__/api/auth/native/email-signup.test.js`

**Test Cases:**
- [ ] Valid signup creates user and returns token
- [ ] Duplicate email returns 400 error
- [ ] Invalid email format returns 422 error
- [ ] Short password returns 422 error
- [ ] Short name returns 422 error
- [ ] Missing fields return 400 error
- [ ] Password is hashed (not stored plain text)
- [ ] Email is normalized (lowercase)

---

#### Test: Email Login

**File:** `__tests__/api/auth/native/email-login.test.js`

**Test Cases:**
- [ ] Valid credentials return success
- [ ] Invalid email returns 401 error
- [ ] Invalid password returns 401 error
- [ ] Missing fields return 400 error
- [ ] Non-existent user returns 401 error
- [ ] User without password returns 401 error

---

### Android Unit Tests

#### Test: AuthManager

**File:** `app/src/test/java/com/consistencygridwallpaper/auth/AuthManagerTest.kt`

**Test Cases:**
- [ ] isLoggedIn() returns true when token exists
- [ ] isLoggedIn() returns false when token is null
- [ ] saveAuthData() saves token and onboarded status
- [ ] getAuthToken() returns saved token
- [ ] isOnboarded() returns saved onboarded status
- [ ] logout() clears all data
- [ ] getInstance() returns singleton instance

---

#### Test: UserPrefs

**File:** `app/src/test/java/com/consistencygridwallpaper/storage/UserPrefsTest.kt`

**Test Cases:**
- [ ] saveToken() persists token
- [ ] getToken() retrieves saved token
- [ ] saveOnboardedStatus() persists onboarded flag
- [ ] isOnboarded() retrieves saved onboarded flag
- [ ] clear() removes all data
- [ ] getToken() returns null after clear()
- [ ] isOnboarded() returns false after clear()

---

## Integration Tests

### Backend Integration Tests

#### Test: Google Sign-In Flow

**Scenario:** Complete Google Sign-In flow from ID token to user creation

**Steps:**
1. Generate valid Google ID token (test account)
2. POST to `/api/auth/native/google`
3. Verify user created in database
4. Verify publicToken generated
5. Verify onboarded = false
6. Verify response contains correct data

**Expected Result:**
- User exists in database
- publicToken is unique
- Response matches schema

---

#### Test: Email Signup → Login Flow

**Scenario:** User signs up, then logs in

**Steps:**
1. POST to `/api/auth/native/email-signup` with test data
2. Verify user created
3. Logout (clear token)
4. POST to `/api/auth/native/email-login` with same credentials
5. Verify token returned
6. Verify onboarded status matches

**Expected Result:**
- Same user returned
- Same publicToken returned
- Password verification works

---

### Android Integration Tests

#### Test: AuthActivity → MainActivity Flow

**Scenario:** User authenticates and navigates to MainActivity

**Steps:**
1. Launch AuthActivity
2. Mock successful Google Sign-In
3. Verify AuthManager.saveAuthData() called
4. Verify MainActivity launched
5. Verify AuthActivity finished

**Expected Result:**
- Token saved to UserPrefs
- MainActivity displays WebView
- AuthActivity not in back stack

---

#### Test: Logout Flow

**Scenario:** User logs out from WebView

**Steps:**
1. Start with logged-in state
2. Load MainActivity with WebView
3. Call JavaScript: `Android.logout()`
4. Verify UserPrefs.clear() called
5. Verify AuthActivity launched
6. Verify MainActivity finished

**Expected Result:**
- All user data cleared
- AuthActivity displayed
- MainActivity destroyed

---

## End-to-End Tests

### E2E Test 1: New User - Google Sign-In

**Scenario:** First-time user signs in with Google and completes onboarding

**Steps:**
1. Install app on test device
2. Launch app
3. Verify AuthActivity displayed
4. Tap "Continue with Google"
5. Select Google account from picker
6. Wait for authentication
7. Verify MainActivity loads
8. Verify WebView loads /onboarding
9. Complete onboarding steps
10. Verify dashboard displayed

**Expected Result:**
- User successfully authenticated
- Onboarding completed
- Dashboard accessible
- Token persisted

**Test Data:**
- Test Google account: test@example.com
- Device: Physical Android device (API 30+)

---

### E2E Test 2: New User - Email Signup

**Scenario:** First-time user signs up with email

**Steps:**
1. Launch app
2. Tap "Sign up with Email"
3. Enter name, email, password
4. Tap "Create Account"
5. Wait for authentication
6. Verify MainActivity loads
7. Verify WebView loads /onboarding
8. Complete onboarding
9. Verify dashboard displayed

**Expected Result:**
- User successfully registered
- Onboarding completed
- Dashboard accessible

**Test Data:**
- Name: "Test User"
- Email: "test+{timestamp}@example.com"
- Password: "TestPass123"

---

### E2E Test 3: Existing User - Auto Login

**Scenario:** User who already signed up opens app again

**Steps:**
1. Complete E2E Test 1 or 2
2. Close app (kill process)
3. Reopen app
4. Verify AuthActivity NOT shown
5. Verify MainActivity loads immediately
6. Verify WebView loads /dashboard directly

**Expected Result:**
- No auth screen shown
- Instant dashboard access
- Session persisted

---

### E2E Test 4: Email Login

**Scenario:** User logs in with email after logout

**Steps:**
1. Complete E2E Test 2
2. Navigate to settings in WebView
3. Tap "Logout"
4. Verify AuthActivity displayed
5. Tap "Log in with Email"
6. Enter email and password
7. Tap "Log In"
8. Verify dashboard displayed

**Expected Result:**
- Successful login
- Direct dashboard access (skip onboarding)

---

### E2E Test 5: App Restart Persistence

**Scenario:** Verify session persists across app restarts

**Steps:**
1. Complete E2E Test 1 or 2
2. Force stop app
3. Restart device
4. Launch app
5. Verify dashboard displayed immediately

**Expected Result:**
- No re-authentication needed
- Token persisted across restart
- Dashboard loads directly

---

## Edge Case Tests

### Edge Case 1: Network Failure During Auth

**Scenario:** User tries to authenticate with no internet

**Steps:**
1. Disable WiFi and mobile data
2. Launch app
3. Tap "Continue with Google"
4. Verify error message displayed
5. Enable internet
6. Retry authentication
7. Verify success

**Expected Result:**
- Clear error message: "Network error. Please check your connection."
- Retry works after internet restored

---

### Edge Case 2: Invalid Credentials

**Scenario:** User enters wrong password

**Steps:**
1. Launch app
2. Tap "Log in with Email"
3. Enter valid email, wrong password
4. Tap "Log In"
5. Verify error message displayed
6. Enter correct password
7. Verify success

**Expected Result:**
- Error message: "Invalid email or password. Please try again."
- No account lockout on first attempt
- Success after correct password

---

### Edge Case 3: Duplicate Email Signup

**Scenario:** User tries to sign up with existing email

**Steps:**
1. Sign up with test@example.com
2. Logout
3. Try to sign up again with test@example.com
4. Verify error message displayed
5. Verify "Log in" button shown

**Expected Result:**
- Error message: "This email is already registered. Please log in instead."
- User can tap "Log in" to switch to login mode

---

### Edge Case 4: Google Sign-In Cancellation

**Scenario:** User cancels Google account picker

**Steps:**
1. Launch app
2. Tap "Continue with Google"
3. Tap back button on account picker
4. Verify AuthActivity still displayed
5. Verify no error message
6. Retry authentication
7. Verify success

**Expected Result:**
- No crash
- User can retry
- Graceful handling of cancellation

---

### Edge Case 5: Onboarding Interruption

**Scenario:** User kills app during onboarding

**Steps:**
1. Start onboarding flow
2. Complete step 1 (personalization)
3. Kill app (force stop)
4. Reopen app
5. Verify onboarding resumes or restarts

**Expected Result:**
- No crash
- User can complete onboarding
- Data not corrupted

---

## Performance Tests

### Test 1: App Startup Time

**Scenario:** Measure time from app launch to dashboard display

**Logged-In User:**
- Target: < 3 seconds
- Measure: Time from onCreate() to WebView onPageFinished()

**Logged-Out User:**
- Target: < 1 second
- Measure: Time from onCreate() to AuthActivity display

---

### Test 2: Authentication Flow Time

**Scenario:** Measure time from auth button tap to dashboard display

**Google Sign-In:**
- Target: < 5 seconds
- Measure: From button tap to dashboard load

**Email Login:**
- Target: < 3 seconds
- Measure: From button tap to dashboard load

---

### Test 3: API Response Time

**Scenario:** Measure backend API response times

**Targets:**
- `/api/auth/native/google`: < 1 second
- `/api/auth/native/email-signup`: < 500ms
- `/api/auth/native/email-login`: < 500ms

---

## Security Tests

### Test 1: Token Storage Security

**Scenario:** Verify tokens are stored securely

**Steps:**
1. Authenticate user
2. Use ADB to inspect SharedPreferences
3. Verify token is stored
4. Verify token is not easily readable (if encrypted)

**Expected Result:**
- Token exists in SharedPreferences
- (Future) Token is encrypted with EncryptedSharedPreferences

---

### Test 2: Password Hashing

**Scenario:** Verify passwords are never stored plain text

**Steps:**
1. Sign up with email/password
2. Query database directly
3. Verify password field is hashed
4. Verify hash starts with "$2a$" (bcrypt)

**Expected Result:**
- Password is bcrypt hashed
- Plain text password not in database

---

### Test 3: ID Token Verification

**Scenario:** Verify backend validates Google ID tokens

**Steps:**
1. Generate fake/invalid ID token
2. POST to `/api/auth/native/google`
3. Verify 401 error returned
4. Verify user NOT created

**Expected Result:**
- Invalid token rejected
- No user created
- Error logged server-side

---

## Regression Tests

### Test 1: Existing Chrome Custom Tabs Flow

**Scenario:** Verify old auth flow still works (during parallel run)

**Steps:**
1. Disable feature flag
2. Launch app
3. Verify Chrome Custom Tabs opens for OAuth
4. Complete OAuth
5. Verify deep link works
6. Verify dashboard loads

**Expected Result:**
- Old flow unaffected
- No breaking changes

---

### Test 2: Existing User Sessions

**Scenario:** Verify existing logged-in users not affected

**Steps:**
1. Have user logged in with old flow
2. Deploy new version with native auth
3. Verify user stays logged in
4. Verify dashboard loads normally

**Expected Result:**
- No logout
- No re-authentication needed
- Seamless upgrade

---

## Test Automation

### Automated Unit Tests

**Tool:** JUnit (Android), Jest (Backend)

**CI/CD Integration:**
- Run on every commit
- Block merge if tests fail
- Coverage target: > 80%

---

### Automated Integration Tests

**Tool:** Espresso (Android), Supertest (Backend)

**CI/CD Integration:**
- Run on pull requests
- Run before deployment

---

### Manual E2E Tests

**Tool:** Manual testing on physical devices

**Frequency:**
- Before each production deployment
- After each major change

**Devices:**
- Android 10, 11, 12, 13, 14
- Various manufacturers (Samsung, Google Pixel, OnePlus)

---

## Test Coverage Goals

### Backend

- Unit Tests: > 90% coverage
- Integration Tests: > 70% coverage
- E2E Tests: Critical paths covered

### Android

- Unit Tests: > 80% coverage
- Integration Tests: > 60% coverage
- E2E Tests: All user flows covered

---

## Testing Checklist

### Before Deployment

- [ ] All unit tests passing
- [ ] All integration tests passing
- [ ] All E2E tests passing
- [ ] Performance tests meet targets
- [ ] Security tests passing
- [ ] Edge cases handled
- [ ] Regression tests passing

### After Deployment

- [ ] Smoke tests in production
- [ ] Monitor error rates
- [ ] Monitor crash rates
- [ ] Monitor auth success rates

---

## Summary

### Test Coverage

✅ **Unit Tests** - Individual function testing  
✅ **Integration Tests** - Component interaction testing  
✅ **E2E Tests** - Complete user flow testing  
✅ **Edge Cases** - Error and failure scenarios  
✅ **Performance Tests** - Speed and responsiveness  
✅ **Security Tests** - Token storage and password hashing  
✅ **Regression Tests** - Existing functionality preserved  

### Quality Gates

✅ **Code Coverage** - > 80% for all components  
✅ **Test Passing** - 100% tests must pass  
✅ **Performance** - All targets must be met  
✅ **Security** - All security tests must pass  

---

## Next Document

→ **09-implementation-plan.md** - Step-by-step implementation guide
