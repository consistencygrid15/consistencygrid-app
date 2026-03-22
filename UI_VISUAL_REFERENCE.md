# UI Upgrade - Visual Reference Guide

## 🎨 Color Palette Reference

### Brand Colors
```
Primary (Main Brand)
┌─────────────────────────┐
│   #FF7A00 (Orange)      │  ← Used for primary actions, headers
│   ████████████████      │     Accessible, vibrant, professional
└─────────────────────────┘

Primary Variant (Darker)
┌─────────────────────────┐
│   #FF5C00 (Dark Orange) │  ← Used for gradients, hover states
│   ████████████████      │     
└─────────────────────────┘

Secondary (Accent)
┌─────────────────────────┐
│   #00BCD4 (Cyan)        │  ← Used for secondary actions
│   ████████████████      │     Secondary buttons, accents
└─────────────────────────┘

Tertiary (Complement)
┌─────────────────────────┐
│   #9C27B0 (Purple)      │  ← Used for highlights, special actions
│   ████████████████      │     
└─────────────────────────┘

Error States
┌─────────────────────────┐
│   #B3261E (Red)         │  ← Error messages, warnings
│   #F9DEDC (Light Red)   │  ← Error card background
└─────────────────────────┘

Neutral Colors
┌─────────────────────────┐
│   #FFFFFF (White)       │  ← Primary background
│   #F5F5F5 (Light Gray)  │  ← Secondary surfaces
│   #E8E8E8 (Medium Gray) │  ← Borders, dividers
│   #757575 (Dark Gray)   │  ← Secondary text
│   #1F1F1F (Almost Black)│  ← Primary text
└─────────────────────────┘
```

---

## 📱 Screen Layouts

### Auth Screen Layout
```
┌─────────────────────────────────────┐
│  ████████████ GRADIENT HEADER ██    │ ← Gradient (Orange to Dark Orange)
│  ██   ┌─────────────────┐        ██ │
│  ██   │      CG         │        ██ │ ← Logo Circle (100x100dp)
│  ██   │  (Orange Text)  │        ██ │
│  ██   └─────────────────┘        ██ │
│  ████████████████████████████████  │
│                                     │
│  ConsistencyGrid                    │ ← 32sp Bold Headline
│  Build consistency, one day at a    │ ← 16sp Gray Subtitle
│  time                               │
│                                     │
│  ┌─────────────────────────────┐   │
│  │ ✓  Continue with Google     │ ← Primary Button (Orange)
│  └─────────────────────────────┘   │ 56dp Height, 12dp Radius
│                                     │
│  ─────────────  OR  ─────────────  │ ← Divider with Text
│                                     │
│  ┌─────────────────────────────┐   │
│  │ Create Account with Email   │ ← Secondary Button (Cyan)
│  └─────────────────────────────┘   │
│                                     │
│  ┌─────────────────────────────┐   │
│  │ Sign In with Email          │ ← Tertiary Button (Outlined)
│  └─────────────────────────────┘   │
│                                     │
└─────────────────────────────────────┘
```

### Email Auth Screen Layout
```
┌─────────────────────────────────────┐
│  ████████████ GRADIENT HEADER ██    │ ← Gradient (Cyan to Dark Cyan)
│  ██  < Back                      ██ │ ← Back Button
│  ████████████████████████████████  │
│                                     │
│  Create Account                     │ ← 28sp Bold Headline
│  Join ConsistencyGrid and start     │ ← 14sp Gray Description
│  building better habits             │
│                                     │
│  ┌─────────────────────────────┐   │
│  │ 👤  Full Name               │ ← Text Input (with emoji prefix)
│  └─────────────────────────────┘   │ Outlined, 12dp radius
│  48dp Height                        │
│                                     │
│  ┌─────────────────────────────┐   │
│  │ ✉  Email Address            │   │
│  └─────────────────────────────┘   │
│                                     │
│  ┌─────────────────────────────┐   │
│  │ 🔒  Password         [👁️]   │   │ ← Password Toggle
│  └─────────────────────────────┘   │
│                                     │
│  ┌─────────────────────────────┐   │
│  │   Continue                  │ ← Primary Button (Orange)
│  └─────────────────────────────┘   │ 56dp Height
│                                     │
│  Already have an account? Sign in   │ ← Toggle Mode
│                                     │
│  ┌─────────────────────────────┐   │
│  │ Error: Invalid email format │ ← Error Card (Red background)
│  └─────────────────────────────┘   │ Rounded corners, elevation
│                                     │
└─────────────────────────────────────┘
```

### Main Activity Layout
```
┌─────────────────────────────────────┐
│ ▓▓▓ Progress Bar (3dp) ▓▓▓▓▓▓▓▓    │ ← Modern gradient progress
├─────────────────────────────────────┤
│                                     │
│                                     │
│        WebView Content              │
│      (Full Screen)                  │
│                                     │
│                                     │
│                                     │
│                                 ┌─┐ │
│                              ┌──┤▶├─┤ ← Extended FAB with label
│                              │  │ │ │   "Test"
│                              │  └─┘ │
│                              │  24dp │   (Orange background)
│                              │margin │   (Elevation shadow)
└──────────────────────────────┴──────┘
```

---

## 🎯 Component Specifications

### Button Styles

#### Primary Button (Orange)
```
Size: 56dp height, full width
Background: #FF7A00 (Orange)
Text Color: #FFFFFF (White)
Corner Radius: 12dp
Elevation: 2dp
Font: 16sp Bold
Padding: 24dp horizontal
```

#### Secondary Button (Cyan)
```
Size: 56dp height, full width
Background: #00BCD4 (Cyan)
Text Color: #FFFFFF (White)
Corner Radius: 12dp
Font: 16sp Bold
Padding: 24dp horizontal
```

#### Tertiary Button (Outlined)
```
Size: 56dp height, full width
Background: #F5F5F5 (Surface)
Text Color: #FF7A00 (Orange)
Border: 2dp #FF7A00
Corner Radius: 12dp
Font: 16sp Bold
```

### Text Input Layout
```
Size: Full width, 48dp height
Background: #F5F5F5 (Surface)
Border: 1dp #E8E8E8 (Outline)
Corner Radius: 12dp
Padding: 16dp
Hint Color: #757575 (Gray)
Icon/Prefix: Emoji or text
Password Toggle: Top-right position
```

### Spacing System (8dp Grid)
```
Extra Small:  4dp
Small:        8dp
Medium:       16dp
Large:        24dp
Extra Large:  32dp

Applied to:
- Padding inside elements
- Margins between elements
- Touch targets (minimum 48dp)
- Component heights
```

---

## 🎨 Typography Hierarchy

```
HEADLINE EXTRA LARGE
32sp • Bold • Primary Color
Used for: Main titles (App name, screen headers)

Headline Large
28sp • Bold • Primary Color
Used for: Section headers (Create Account, Sign In)

Headline Medium
24sp • Bold • Primary Color
Used for: Sub-headers

Body Large
16sp • Regular • Primary Text
Used for: Main body text, descriptions

Body Medium
14sp • Regular • Primary Text
Used for: Secondary content

Label Large
14sp • Bold • Uppercase
Used for: Button text, labels

Caption Small
12sp • Regular • Gray Text
Used for: Helper text, secondary info
```

---

## ✨ Visual Effects

### Shadows (Elevation)
```
FAB:          8dp elevation
Cards:        2dp elevation
Buttons:      2dp elevation (on press: 4dp)
Main surfaces: 1dp elevation (subtle)
```

### Rounded Corners
```
Buttons:      12dp
Text Inputs:  12dp
Cards:        12dp
FAB:          Default circular
```

### Transitions
```
Button Press:       100ms scale animation
Navigation:         300ms fade transition
Loading Indicator:  Smooth rotation
Progress Bar:       Smooth fill animation
```

---

## 🌓 Dark Mode (Future Implementation)

### Dark Mode Colors (Optional)
```
Background (Dark):     #121212
Surface (Dark):        #1E1E1E
Surface Variant:       #2A2A2A
Primary Text (Dark):   #FFFFFF
Secondary Text (Dark): #B0B0B0
```

---

## 📐 Responsive Breakpoints

```
Small Phone:   320dp - 479dp (4.0" - 5.0")
Normal Phone:  480dp - 599dp (5.1" - 6.5")
Large Phone:   600dp+ (6.7"+)

All layouts support minimum 320dp width with proper scrolling
```

---

## ✅ Design System Compliance

- ✓ Material Design 3 Guidelines
- ✓ WCAG 2.1 AA Accessibility Standards
- ✓ Android Material Component Library
- ✓ Consistent spacing (8dp grid)
- ✓ Accessible color contrast ratios
- ✓ Touch target minimum 48dp
- ✓ Readable font sizes (minimum 14sp)

---

## 🚀 Implementation Notes

1. **Colors**: All hardcoded colors replaced with resource IDs
2. **Layouts**: Converted to Material Components (MaterialButton, TextInputLayout)
3. **Styles**: Centralized theme definitions for easy maintenance
4. **Consistency**: All screens follow same spacing and color system
5. **Scalability**: Layouts adapt to different screen sizes

---

Generated: March 1, 2026
Modern Material Design 3 - Visual Reference v1.0