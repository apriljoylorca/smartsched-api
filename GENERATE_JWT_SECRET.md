# How to Set JWT Secret for SmartSched API

## ✅ Simplified! No Base64 Encoding Needed

**You can now use ANY string as your JWT secret!** The system automatically converts it to a secure key.

## Quick Setup

### Option 1: Use Any Secure String (Simplest!)

Just use any string you want! Examples:
- `my-super-secret-key-2024`
- `SmartschedSecureKey123!`
- `production-jwt-secret-key`

**Recommendation**: Use at least 16 random characters for better security.

### Option 2: Generate a Random String

**On Linux/Mac:**
```bash
openssl rand -hex 16
```

**On Windows (PowerShell):**
```powershell
-join ((48..57) + (65..90) + (97..122) | Get-Random -Count 32 | ForEach-Object {[char]$_})
```

**Or use an online generator:**
- https://www.random.org/strings/ (generate a random string)

## Setting in Render

1. **Go to Render Dashboard** → Your API Service → Environment
2. **Add/Edit Environment Variable**:
   - **Key**: `JWT_SECRET`
   - **Value**: Paste the generated Base64 string (e.g., `xK8mP2qL9vR4wT7yU5iO3pA6sD1fG0hJ=`)
3. **Save**
4. **Redeploy** your service

## Requirements

- ✅ Can be **any string** - no encoding needed!
- ✅ **Minimum 8 characters** (required)
- ✅ **16+ characters recommended** for better security
- ✅ Can contain **any characters**: letters, numbers, symbols, spaces, etc.

## Example Secrets

```
my-super-secret-key-2024
SmartschedSecureKey123!
production-jwt-secret-key-xyz789
```

## Verification

After setting the secret, check your Render logs. You should see:
```
✓ JWT Secret is configured and will be automatically converted to a secure key
=== JWT SECRET VALIDATION: OK ===
```

That's it! No Base64 validation needed.

