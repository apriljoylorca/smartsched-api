# How to Generate a JWT Secret for SmartSched API

## Problem
If you see the error: `Illegal base64 character: '-'` or similar Base64 decoding errors, it means your `JWT_SECRET` environment variable is not properly Base64 encoded.

## Solution: Generate a Proper Base64 Secret

### Option 1: Using OpenSSL (Recommended)

**On Linux/Mac:**
```bash
openssl rand -base64 32
```

**On Windows (PowerShell):**
```powershell
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Minimum 0 -Maximum 256 }))
```

**On Windows (Git Bash or WSL):**
```bash
openssl rand -base64 32
```

### Option 2: Using Online Tools

1. Go to: https://www.random.org/bytes/
2. Generate 32 bytes (256 bits)
3. Copy the hexadecimal output
4. Convert to Base64 using: https://www.base64encode.org/
   - Paste the hex string
   - Encode it
   - Use the result as your JWT_SECRET

### Option 3: Using Java (if you have JDK)

```bash
java -cp . GenerateSecret
```

Or create a simple Java program:
```java
import java.util.Base64;
import java.security.SecureRandom;

public class GenerateSecret {
    public static void main(String[] args) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String secret = Base64.getEncoder().encodeToString(bytes);
        System.out.println(secret);
    }
}
```

## Setting in Render

1. **Go to Render Dashboard** → Your API Service → Environment
2. **Add/Edit Environment Variable**:
   - **Key**: `JWT_SECRET`
   - **Value**: Paste the generated Base64 string (e.g., `xK8mP2qL9vR4wT7yU5iO3pA6sD1fG0hJ=`)
3. **Save**
4. **Redeploy** your service

## Requirements

- ✅ Must be **Base64 encoded**
- ✅ Must be **at least 32 bytes** (256 bits) after decoding
- ✅ Must contain **only valid Base64 characters**: `A-Z`, `a-z`, `0-9`, `+`, `/`, and `=` (for padding)
- ❌ **Cannot contain**: `-`, `_`, spaces, or other special characters

## Example Valid Secret

```
xK8mP2qL9vR4wT7yU5iO3pA6sD1fG0hJkLmNoPqRsTuVwXyZ1234567890+/
```

## Verification

After setting the secret, check your Render logs. You should see:
```
JWT Secret Key decoded successfully. Key length: 32 bytes
```

If you see errors, the secret is likely invalid.

## Quick Test Command

To test if a string is valid Base64:
```bash
echo "YOUR_SECRET_HERE" | base64 -d > /dev/null && echo "Valid Base64" || echo "Invalid Base64"
```

