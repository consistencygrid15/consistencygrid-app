# Exact 12:00 AM Wallpaper Updates - Setup Guide

## 🚨 Important: Enable Exact Alarm Permission (Android 12+)

If your wallpaper is updating several hours after midnight instead of exactly at 12:00 AM, you need to grant the "Alarms & reminders" permission.

### Why This Happens:
- **Android 12 and newer** require explicit permission for apps to schedule exact alarms
- Without this permission, Android delays alarms to save battery
- Updates may occur 1-6 hours late depending on battery optimization

---

## ✅ How to Fix (3 Easy Steps)

### Step 1: Enable Auto-Updates in the App
1. Open the ConsistencyGrid app
2. Go to Settings
3. Toggle "Auto-Update" ON
4. A dialog will appear

### Step 2: Grant Exact Alarm Permission
When you enable auto-updates, you'll see a dialog:
- Tap **"Open Settings"**
- Find **"ConsistencyGrid"** in the list
- Toggle **"Alarms & reminders"** to **ON**

### Step 3: Verify It's Working
The app will show:
- ✅ **"Exact 12:00 AM update scheduled!"** = Permission granted (perfect!)
- ⚠️ **"Update scheduled (may not be exact)"** = Permission needed

---

## 📱 Manual Steps (If Dialog Doesn't Appear)

### For Android 13:
1. Open **Settings** → **Apps**
2. Find **"ConsistencyGrid"**
3. Tap **"Alarms & reminders"**
4. Enable the toggle

### For Android 12:
1. Open **Settings** → **Apps**
2. Find **"ConsistencyGrid"**
3. Tap **"Set alarms and reminders"**
4. Enable the toggle

---

## 🔋 Additional Battery Optimization Tips

For **100% reliability**, especially on Xiaomi/Samsung/Oppo devices:

### Disable Battery Optimization:
1. Settings → Apps → ConsistencyGrid
2. Battery → **Unrestricted**
3. Or: Battery optimization → **Not optimized**

### Xiaomi Devices (MIUI):
1. Settings → Apps → Manage apps → ConsistencyGrid
2. **Autostart**: ON
3. **Battery saver**: No restrictions
4. **Background activity**: Allow

### Samsung Devices (One UI):
1. Settings → Apps → ConsistencyGrid
2. Battery → **Unrestricted**
3. **Put app to sleep**: OFF

### Oppo/Realme Devices (ColorOS):
1. Settings → Apps → App Management → ConsistencyGrid
2. **Auto-launch**: ON
3. **Background freeze**: OFF

---

## 🧪 Testing the Midnight Update

### Quick Test (Change Phone Time):
1. Enable auto-updates in the app
2. Change your phone time to **11:59 PM**
3. Wait for **12:00 AM**
4. Wallpaper should update within 1-2 minutes

### Check Logs (For Developers):
```bash
adb logcat -s WorkScheduler:D MidnightReceiver:D WallpaperWorker:D
```

Look for:
- `⏰ Scheduling next update for: [date]`
- `⏰ Midnight alarm received!`
- `✅ WallpaperWorker enqueued`

---

## ❓ Troubleshooting

### "Updates still delayed even with permission"
- Check battery optimization settings (see above)
- Ensure app is not in "Deep sleep" or "Sleeping apps" list
- Restart your phone after granting permissions

### "Dialog doesn't appear when enabling auto-updates"
- The app may already have permission
- Check Settings → Apps → ConsistencyGrid → Alarms & reminders

### "Can't find 'Alarms & reminders' setting"
- Your Android version may be older than 12
- Exact alarms should work automatically on Android 11 and below

---

## 📊 Expected Behavior

| Permission Status | Update Time | Reliability |
|------------------|-------------|-------------|
| ✅ Exact alarm granted | 12:00 AM ±1 min | 99% |
| ⚠️ No exact alarm | 12:00 AM + 1-6 hours | 60% |
| ❌ Battery optimized | Random / Never | 20% |

---

## 🎯 Summary

For **exact 12:00 AM updates**:
1. ✅ Enable "Alarms & reminders" permission
2. ✅ Set battery to "Unrestricted"
3. ✅ Disable "Put app to sleep" (Samsung)
4. ✅ Enable "Autostart" (Xiaomi/Oppo)

The app will now update your wallpaper precisely at midnight every day!
