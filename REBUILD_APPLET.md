# HƯỚNG DẪN REBUILD APPLET - 64 BYTES (CÓ NGÀY SINH)

## ✅ ĐÃ HOÀN THÀNH - Java Client Side
- CardData.java: Thêm dobDay, dobMonth, dobYear fields
- CardHelper.java: Cập nhật READ/WRITE từ 60→64 bytes
- FunctionPanel.java: Thêm date picker (ngày/tháng/năm)
- CustomerWindow.java: Hiển thị ngày sinh
- SmartCard.java (client): Đã update cấu trúc 64 bytes

## ⚠️ CẦN LÀM - JavaCard Applet Side

### Cấu trúc mới (64 bytes):
```
[0-1]   UserID (2 bytes)
[2-5]   Balance (4 bytes)
[6-7]   ExpiryDays (2 bytes)
[8]     PIN (1 byte)
[9]     PINRetry (1 byte)
[10]    DOB Day (1 byte)
[11]    DOB Month (1 byte)
[12-13] DOB Year (2 bytes)
[14-63] FullName (50 bytes UTF-8)
```

### Các bước rebuild trong JCIDE:

1. **Mở JCIDE**
   - Launch JCIDE (C:\JCIDE\jcide.exe hoặc tương tự)

2. **Mở Project**
   - File → Open Project → C:\workspace\SmartCard\

3. **Copy SmartCard.java mới**
   - Copy file `SmartCard.java` từ:
     ```
     C:\Users\minhq\OneDrive\Documents\NetBeansProjects\GymSmartCardApp\SmartCard.java
     ```
   - Dán vào:
     ```
     C:\workspace\SmartCard\src\SmartCard\SmartCard.java
     ```
   - Hoặc mở file trong JCIDE và thay thế toàn bộ nội dung

4. **Build Applet**
   - Build → Build All (hoặc Ctrl+B)
   - Kiểm tra không có lỗi compile
   - File .cap/.exp sẽ được tạo trong thư mục build/

5. **Deploy lên thẻ**
   - Card → Select Card Reader → Chọn reader của bạn
   - Card → Load → Chọn file .cap vừa build
   - Xác nhận install với AID: 26 12 20 03 20 03 00

6. **Verify**
   - Chạy GymAppLauncher
   - Admin → Tạo Thẻ Mới → Nhập họ tên "Nguyễn Văn A", ngày sinh 15/05/1990
   - Quẹt lại xem tên và ngày sinh có giữ nguyên không

## 🔍 Kiểm tra APDU:

Sau khi rebuild, các lệnh APDU sẽ là:

**READ:**
```
>> 00 B0 00 00 40
<< [64 bytes data] 90 00
```

**WRITE:**
```
>> 00 D0 00 00 40 [64 bytes data]
<< 90 00
```

## 🐛 Nếu vẫn bị reset dữ liệu:

1. **Kiểm tra applet đã rebuild chưa:**
   - Nếu applet vẫn là 11 hoặc 13 bytes → client ghi 60 bytes sẽ bị reject
   - Xem log APDU trong CustomerWindow/FunctionPanel

2. **Kiểm tra lệnh SELECT:**
   ```
   >> 00 A4 04 00 07 26 12 20 03 20 03 00
   << 90 00
   ```
   Nếu 6A 82 → Applet chưa được deploy

3. **Test đơn giản:**
   - Tạo thẻ với userId=1234, name="Nguyễn Văn A", DOB=15/05/1990, balance=1000000
   - Quẹt lại ngay → Nếu userId vẫn = 1234 và name, DOB giữ nguyên → Applet OK
   - Nếu userId = 0 → Applet đang reset

## 📝 Lưu ý:

- **Blank card detection** (userId=0) cho phép ghi lần đầu không cần PIN
- **Subsequent writes** yêu cầu PIN verification
- **UTF-8 encoding**: Name field hỗ trợ tiếng Việt (tối đa 50 bytes)
- **PIN system**: 6-digit input (000000-999999) → mapped to 0-255 via % 256
- **DOB format**: Day (1-31), Month (1-12), Year (1900-2099)

## 🔐 VỀ MÃ HÓA AES + SHA-256:

Bạn đề xuất mã hóa dữ liệu với SHA-256 hash PIN làm AES key.

**Thực tế:**
- JavaCard 2.2.1 **có hỗ trợ** SHA-256 và AES-128
- **Phức tạp**: Cần IV (16 bytes), padding, key derivation
- **Chi phí**: Tăng thời gian xử lý mỗi giao dịch
- **Bộ nhớ**: Cần thêm ~32 bytes cho IV và metadata

**Có nên implement?**
- ✅ Nếu dữ liệu nhạy cảm (y tế, tài chính)
- ❌ Với gym card, PIN verification đã đủ bảo vệ

Nếu muốn thêm, cần thiết kế lại:
```
[Encrypted Block 1] [Encrypted Block 2] [IV] [Metadata]
```

## ✅ Sau khi rebuild xong:

```powershell
# Test lại app
cd C:\Users\minhq\OneDrive\Documents\NetBeansProjects\GymSmartCardApp
java -cp build\classes GymAppLauncher
```

Nếu vẫn gặp vấn đề, kiểm tra:
1. Applet có đúng 64 bytes không (check DATA_SIZE constant)
2. Client có gửi đúng 64 bytes không (check buildWriteCommand)
3. APDU log có hiện error 6700 (wrong length) không
