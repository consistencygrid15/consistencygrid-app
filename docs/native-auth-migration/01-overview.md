# Native Authentication Migration - Overview

## Document Information

- **Version:** 1.0
- **Date:** 2026-02-04
- **Status:** Planning Phase
- **Author:** Development Team

---

## Problem Statement

### Current Limitation

The ConsistencyGrid Android application currently uses **Chrome Custom Tabs** for user authentication (Google OAuth). While functional, this approach has several limitations:

1. **Non-Native Experience**
   - Users are taken out of the app into a browser tab
   - Breaks the immersive app experience
   - Feels less secure to users (external browser)

2. **Dependency on Browser**
   - Requires Chrome or compatible browser installed
   - Subject to browser-specific issues
   - Cannot leverage native Android account picker

3. **Complex Flow**
   - App → Chrome Custom Tab → OAuth → Callback → Deep Link → App
   - Multiple context switches
   - Potential for users to get lost in the flow

4. **Limited Control**
   - Cannot customize the OAuth UI
   - Cannot add native email/password signup
   - Dependent on browser behavior

### Why Replace WebView OAuth?

The current implementation is **not actually WebView OAuth** - it's Chrome Custom Tabs OAuth. However, the goal is to eliminate **any browser-based authentication** and move to a fully native experience.

**Key Reasons:**

- **User Experience:** Native authentication feels more integrated and trustworthy
- **Control:** Full control over UI/UX and error handling
- **Flexibility:** Can add email/password auth alongside Google Sign-In
- **Performance:** Faster authentication flow without browser overhead
- **Reliability:** No dependency on external browser availability

---

## High-Level Solution Summary

### Proposed Solution

Implement **Native Android Authentication** with the following components:

1. **Native Google Sign-In**
   - Use Google Sign-In SDK for Android
   - Show system account picker (native Android UI)
   - No browser involvement
   - Direct ID token verification with backend

2. **Native Email/Password Authentication**
   - Custom Android UI for signup/login
   - Direct API calls to backend
   - No WebView or browser involvement

3. **WebView for Post-Login Content**
   - WebView loads **only after** successful authentication
   - WebView shows dashboard or onboarding based on user status
   - WebView never handles login/signup

### Architecture Philosophy

```
┌─────────────────────────────────────────┐
│         NATIVE ANDROID                  │
│  ┌───────────────────────────────┐     │
│  │   Authentication Layer        │     │
│  │  - Google Sign-In SDK         │     │
│  │  - Email/Password Forms       │     │
│  │  - Token Management           │     │
│  └───────────────────────────────┘     │
│              ↓                          │
│  ┌───────────────────────────────┐     │
│  │   WebView Layer               │     │
│  │  - Dashboard (authenticated)  │     │
│  │  - Onboarding (new users)     │     │
│  │  - No auth handling           │     │
│  └───────────────────────────────┘     │
└─────────────────────────────────────────┘
```

**Key Principle:** Authentication is **native**, content is **WebView**.

---

## Benefits of Native Authentication

### For Users

✅ **Seamless Experience**
- No context switching to browser
- Familiar native Android UI
- System account picker for Google Sign-In

✅ **Faster Authentication**
- Direct API calls, no browser overhead
- Instant token storage
- Immediate app access

✅ **More Secure Feel**
- Native UI builds trust
- No external browser involvement
- Clear app branding throughout

### For Development

✅ **Full Control**
- Custom UI/UX design
- Better error handling
- Flexible authentication methods

✅ **Better Testing**
- Easier to mock and test
- No browser dependencies
- Clearer debugging

✅ **Future-Proof**
- Can add biometric auth later
- Can add social logins (Facebook, Apple)
- Independent of browser changes

---

## Success Criteria

### Technical Success

- [ ] Zero crashes related to authentication
- [ ] 100% token persistence across app restarts
- [ ] <2 seconds for complete authentication flow
- [ ] Zero existing user session loss

### User Experience Success

- [ ] 95%+ successful login rate
- [ ] <5% authentication flow abandonment
- [ ] Positive user feedback on native UI
- [ ] Zero complaints about logout issues

### Business Success

- [ ] No increase in support tickets
- [ ] Maintained or improved user retention
- [ ] Smooth migration with zero downtime
- [ ] Ability to add new auth methods easily

---

## Scope of This Migration

### In Scope

✅ Native Google Sign-In implementation  
✅ Native email/password signup/login  
✅ Token-based session management  
✅ Onboarding flow integration  
✅ Logout functionality  
✅ Backend API endpoints for native auth  

### Out of Scope

❌ Changes to WebView content (dashboard, onboarding UI)  
❌ Changes to backend business logic (habits, goals, etc.)  
❌ Biometric authentication (future enhancement)  
❌ Social logins beyond Google (future enhancement)  
❌ Password reset flow (uses existing web flow)  

---

## Timeline Overview

| Phase | Duration | Key Deliverables |
|-------|----------|------------------|
| Documentation | 1 day | Complete documentation package |
| Backend APIs | 2 days | 3 new authentication endpoints |
| Android UI | 3 days | Native auth screens and helpers |
| Integration | 2 days | Connect native auth to MainActivity |
| Testing | 3 days | Unit, integration, E2E tests |
| Beta Testing | 3 days | Internal testing and bug fixes |
| Rollout | 2 days | Gradual production deployment |
| **Total** | **16 days** | **Production-ready native auth** |

---

## Risk Mitigation

### Key Risks

1. **Existing User Disruption**
   - **Mitigation:** Preserve all existing tokens, gradual rollout

2. **Google Sign-In SDK Issues**
   - **Mitigation:** Thorough testing, fallback to Chrome Custom Tabs

3. **Backend API Failures**
   - **Mitigation:** Retry logic, comprehensive error handling

4. **Onboarding Flow Breakage**
   - **Mitigation:** No changes to onboarding logic, only routing

### Rollback Strategy

- Feature flag allows instant disable
- No database schema changes required
- Existing tokens remain valid
- Zero data loss on rollback

---

## Next Steps

1. ✅ **Review this overview document**
2. → **Review remaining 8 documentation files**
3. → **Approve architecture and file changes**
4. → **Begin Phase 1: Backend API Development**

---

## Document Status

- [x] Problem statement defined
- [x] Solution approach outlined
- [x] Benefits documented
- [x] Success criteria established
- [x] Scope clarified
- [ ] Stakeholder approval pending
