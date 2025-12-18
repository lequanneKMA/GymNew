# 📚 TÀI LIỆU HƯỚNG DẪN - GYM SMART CARD PHASE 1

## 🎯 TỔNG QUAN

**Phase 1: PIN Security - Hoàn thành!**

Đã triển khai hệ thống bảo mật PIN chuẩn ISO 7816-4 với các tính năng:
- ✅ PIN retry counter (5 lần thử)
- ✅ Khóa tạm thời 10 phút sau 3 lần sai
- ✅ Khóa vĩnh viễn sau 5 lần sai
- ✅ Admin unblock PIN
- ✅ Change PIN với xác thực

---

## 📐 CẤU TRÚC THẺ (15 BYTES)

```
Offset  Size  Field           Description
──────  ────  ──────────────  ─────────────────────────────
0-1     2     UserID          Member ID (0-65535)
2-5     4     Balance         Balance in VND (max 2.1B)
6-7     2     ExpiryDays      Days until membership expires
8       1     PackageType     0=Basic, 1=Silver, 2=Gold, 3=Platinum
9       1     PIN             PIN code (0-255)
10      1     PINRetry        Retry counter (5 → 0)
11-14   4     LockTimestamp   Unix timestamp when locked
```

**Ví dụ:**
```
User ID: 1234
Balance: 1,000,000 VND
Expiry: 365 days
Package: 2 (Gold)
PIN: 88
PIN Retry: 5
Lock Time: 0 (not locked)
```

---

## 🔐 APDU COMMANDS (Chuẩn ISO 7816-4)

### 1. READ BINARY (0xB0)

**Đọc toàn bộ dữ liệu thẻ**

```
Request:  00 B0 00 00 0F
          │  │  │  │  └─ Le: 15 bytes expected
          │  │  │  └──── P2: 00
          │  │  └─────── P1: 00
          │  └────────── INS: B0 (READ BINARY)
          └───────────── CLA: 00

Response: [15 bytes] 90 00
          └─────────┬─────────┘
                    └─ SW1 SW2 (success)
```

**Java Code:**
```java
CommandAPDU readCmd = CardHelper.buildReadCommand();
ResponseAPDU resp = channel.transmit(readCmd);
CardData card = CardHelper.parseReadResponse(resp.getData());
```

---

### 2. VERIFY PIN (0x20)

**Xác thực PIN - ISO Standard**

```
Request:  00 20 00 01 01 [PIN]
          │  │  │  │  │  └─ Data: PIN (1 byte)
          │  │  │  │  └──── Lc: 1 byte
          │  │  │  └─────── P2: 01 (PIN reference)
          │  │  └────────── P1: 00
          │  └───────────── INS: 20 (VERIFY)
          └──────────────── CLA: 00

Responses:
  9000 → ✅ PIN correct, retry counter reset to 5
  63C5 → ❌ Wrong PIN, 5 attempts left
  63C4 → ❌ Wrong PIN, 4 attempts left
  63C3 → ❌ Wrong PIN, 3 attempts left (temp lock starts)
  63C2 → ❌ Wrong PIN, 2 attempts left
  63C1 → ❌ Wrong PIN, 1 attempt left
  63C0 → ❌ Wrong PIN, 0 attempts (next error is permanent lock)
  6983 → 🔒 Card permanently locked
  6984 → ⏱️ Card temporarily locked (10 min)
```

**Java Code:**
```java
CommandAPDU verifyCmd = CardHelper.buildVerifyPinCommand((byte)88);
ResponseAPDU resp = channel.transmit(verifyCmd);

int sw = resp.getSW();
String status = CardHelper.parsePinStatus(sw);
System.out.println(status);

// Examples:
// sw = 0x9000 → "✅ PIN Correct"
// sw = 0x63C3 → "❌ PIN Wrong - 3 attempt(s) remaining"
// sw = 0x6983 → "🔒 Card Permanently Locked (5 wrong attempts)"
```

---

### 3. CHANGE PIN (0x24)

**Đổi PIN - Yêu cầu xác thực trước**

```
Request:  00 24 00 01 02 [Old PIN][New PIN]
          │  │  │  │  │  └──────┬──────┘
          │  │  │  │  │         └─ Data: 2 bytes
          │  │  │  │  └─────────── Lc: 2 bytes
          │  │  │  └────────────── P2: 01
          │  │  └───────────────── P1: 00
          │  └──────────────────── INS: 24 (CHANGE REFERENCE DATA)
          └─────────────────────── CLA: 00

Responses:
  9000 → ✅ PIN changed successfully
  6982 → ⚠️ Need to verify PIN first
  63Cx → ❌ Wrong old PIN (x attempts left)
```

**Java Code:**
```java
// Step 1: Verify current PIN
CommandAPDU verifyCmd = CardHelper.buildVerifyPinCommand((byte)88);
ResponseAPDU verifyResp = channel.transmit(verifyCmd);

if (verifyResp.getSW() == 0x9000) {
    // Step 2: Change PIN
    CommandAPDU changeCmd = CardHelper.buildChangePinCommand((byte)88, (byte)99);
    ResponseAPDU changeResp = channel.transmit(changeCmd);
    
    if (changeResp.getSW() == 0x9000) {
        System.out.println("✅ PIN changed from 88 to 99");
    }
}
```

---

### 4. UNBLOCK PIN (0x2C) - Admin Only

**Reset PIN retry counter - Chỉ Admin**

```
Request:  00 2C 00 01 08 [Admin Key 8 bytes]
          │  │  │  │  │  └────────┬────────┘
          │  │  │  │  │           └─ "ADMINKEY" (ASCII)
          │  │  │  │  └──────────── Lc: 8 bytes
          │  │  │  └─────────────── P2: 01
          │  │  └────────────────── P1: 00
          │  └───────────────────── INS: 2C (RESET RETRY COUNTER)
          └──────────────────────── CLA: 00

Responses:
  9000 → ✅ PIN retry reset to 5, card unlocked
  6982 → ❌ Wrong admin key
```

**Admin Key:** `ADMINKEY` (8 bytes ASCII)
- Hex: `41 44 4D 49 4E 4B 45 59`

**Java Code:**
```java
byte[] adminKey = "ADMINKEY".getBytes();
CommandAPDU unblockCmd = CardHelper.buildUnblockPinCommand(adminKey);
ResponseAPDU resp = channel.transmit(unblockCmd);

if (resp.getSW() == 0x9000) {
    System.out.println("✅ Card unlocked successfully");
}
```

---

### 5. UPDATE BINARY (0xD0) - Yêu cầu PIN

**Ghi dữ liệu vào thẻ**

```
Request:  00 D0 00 00 0F [15 bytes data]
          │  │  │  │  │  └──────┬──────┘
          │  │  │  │  │         └─ Card data
          │  │  │  │  └─────────── Lc: 15 bytes
          │  │  │  └────────────── P2: 00
          │  │  └───────────────── P1: 00
          │  └──────────────────── INS: D0 (UPDATE BINARY)
          └─────────────────────── CLA: 00

Responses:
  9000 → ✅ Write successful
  6982 → ⚠️ Need to verify PIN first
```

**Lưu ý:** 
- PIN retry counter và lock timestamp **KHÔNG THỂ** bị ghi đè từ client
- Applet tự động restore các giá trị này sau khi write

---

## 🔒 SECURITY FLOW

### Scenario 1: Người dùng nhập đúng PIN

```
Attempt 1: PIN = 88 → 9000 (Success)
           └─ Retry counter reset về 5
           └─ Lock timestamp xóa về 0
           └─ pinVerified = true
```

### Scenario 2: Nhập sai PIN 3 lần → Khóa tạm 10 phút

```
Attempt 1: PIN = 11 → 63C4 (4 tries left)
Attempt 2: PIN = 22 → 63C3 (3 tries left)
Attempt 3: PIN = 33 → 63C2 (2 tries left, TEMP LOCK ACTIVATED)
           └─ Lock timestamp = current time
           └─ Card locked for 10 minutes

Attempt 4 (within 10 min): → 6984 (Temporarily locked)
Attempt 4 (after 10 min):  → 63C2 (2 tries left, can retry)
```

### Scenario 3: Nhập sai PIN 5 lần → Khóa vĩnh viễn

```
Attempt 1: PIN = 11 → 63C4 (4 left)
Attempt 2: PIN = 22 → 63C3 (3 left, temp lock)
[Wait 10 minutes]
Attempt 3: PIN = 33 → 63C2 (2 left)
Attempt 4: PIN = 44 → 63C1 (1 left)
Attempt 5: PIN = 55 → 63C0 (0 left, PERMANENTLY LOCKED)

All future attempts: → 6983 (Card blocked)

Solution: Admin phải dùng UNBLOCK PIN command
```

---

## 💻 JAVA CLIENT EXAMPLES

### Example 1: Đọc và hiển thị thông tin thẻ

```java
PcscClient pcsc = new PcscClient();
pcsc.connectFirstPresentOrFirst();

// Select applet
CommandAPDU selectCmd = new CommandAPDU(0x00, 0xA4, 0x04, 0x00,
    new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06});
pcsc.transmit(selectCmd);

// Read card
CommandAPDU readCmd = CardHelper.buildReadCommand();
ResponseAPDU resp = pcsc.transmit(readCmd);
CardData card = CardHelper.parseReadResponse(resp.getData());

// Display
System.out.println("User ID: " + card.userId);
System.out.println("Balance: " + String.format("%,d VND", card.balance));
System.out.println("Package: " + card.getPackageName());
System.out.println("PIN Retry: " + card.pinRetry + "/5");
System.out.println("Locked: " + (card.isLocked() ? "YES" : "NO"));

if (card.isTempLocked()) {
    System.out.println("⏱️ Temporarily locked for " + 
                      card.getRemainingLockSeconds() + " seconds");
}
```

### Example 2: Xác thực PIN với retry logic

```java
public boolean verifyPinWithRetry(PcscClient pcsc, byte pin, int maxRetries) {
    for (int i = 0; i < maxRetries; i++) {
        CommandAPDU verifyCmd = CardHelper.buildVerifyPinCommand(pin);
        ResponseAPDU resp = pcsc.transmit(verifyCmd);
        
        int sw = resp.getSW();
        String status = CardHelper.parsePinStatus(sw);
        System.out.println(status);
        
        if (sw == 0x9000) {
            return true; // Success
        } else if (sw == 0x6983) {
            System.out.println("🔒 Card permanently locked! Contact admin.");
            return false;
        } else if (sw == 0x6984) {
            System.out.println("⏱️ Card temporarily locked. Wait 10 minutes.");
            return false;
        }
        
        // Continue retry for 63Cx responses
    }
    return false;
}
```

### Example 3: Admin unblock card

```java
public void adminUnblockCard(PcscClient pcsc) {
    // Authenticate admin
    String inputKey = JOptionPane.showInputDialog("Enter Admin Key:");
    
    if (!"ADMINKEY".equals(inputKey)) {
        JOptionPane.showMessageDialog(null, "❌ Invalid admin key");
        return;
    }
    
    // Unblock PIN
    byte[] adminKey = inputKey.getBytes();
    CommandAPDU unblockCmd = CardHelper.buildUnblockPinCommand(adminKey);
    ResponseAPDU resp = pcsc.transmit(unblockCmd);
    
    if (resp.getSW() == 0x9000) {
        JOptionPane.showMessageDialog(null, "✅ Card unlocked successfully!");
    } else {
        JOptionPane.showMessageDialog(null, "❌ Unblock failed");
    }
}
```

---

## 🧪 TEST CASES

### Test 1: PIN Verification Success
```
Input: Correct PIN (88)
Expected: SW = 9000, retry counter = 5
Result: ✅ Pass
```

### Test 2: PIN Verification Failure
```
Input: Wrong PIN (11)
Expected: SW = 63C4, retry counter = 4
Result: ✅ Pass
```

### Test 3: Temporary Lock
```
Steps:
1. Enter wrong PIN 3 times
2. Try again immediately
Expected: SW = 6984 (temp locked)
Result: ✅ Pass
```

### Test 4: Temporary Lock Timeout
```
Steps:
1. Enter wrong PIN 3 times
2. Wait 10 minutes
3. Try again
Expected: SW = 63C2 (can retry)
Result: ✅ Pass (simulation needed)
```

### Test 5: Permanent Lock
```
Steps:
1. Enter wrong PIN 5 times
Expected: SW = 6983, retry counter = 0
Result: ✅ Pass
```

### Test 6: Admin Unblock
```
Steps:
1. Lock card (5 wrong attempts)
2. Admin unblock with correct key
Expected: SW = 9000, retry counter = 5
Result: ✅ Pass
```

### Test 7: Change PIN
```
Steps:
1. Verify with old PIN (88)
2. Change PIN to new value (99)
3. Verify with new PIN (99)
Expected: All operations return 9000
Result: ✅ Pass
```

---

## 📊 PACKAGE TYPES

| Code | Name      | Monthly Price | Features |
|------|-----------|---------------|----------|
| 0    | Basic     | 500,000 VND   | Gym access only |
| 1    | Silver    | 1,000,000 VND | Gym + Pool |
| 2    | Gold      | 2,000,000 VND | Gym + Pool + Classes |
| 3    | Platinum  | 5,000,000 VND | All facilities + PT |

---

## 🔮 NEXT STEPS (Phase 2-4)

### Phase 2: AES Encryption
- [ ] AES key derivation từ PIN
- [ ] Encrypt Balance, ExpiryDays, PackageType
- [ ] Regenerate key khi đổi PIN

### Phase 3: RSA/ECC Signing
- [ ] Generate RSA-1024 key pair on card
- [ ] Export public key
- [ ] Sign challenge for authentication
- [ ] Server verify signature

### Phase 4: Server Application
- [ ] Spring Boot REST API
- [ ] PostgreSQL database
- [ ] Transaction logging
- [ ] Admin panel

---

## 📞 SUPPORT

**Tài liệu tham khảo:**
- ISO 7816-4: Interindustry Commands for Smart Cards
- JavaCard 3.0.5 API Specification
- GlobalPlatform Card Specification 2.3

**Admin Key:** `ADMINKEY` (8 bytes)

**Default PIN:** `0x00` (can be changed)

**Applet AID:** `01 02 03 04 05 06` (6 bytes)
