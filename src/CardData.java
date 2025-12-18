/**
 * Represents data stored on a smart card.
 * Structure (64 bytes): [UserID(2)] [Balance(4)] [ExpiryDays(2)] [PIN(1)] [PINRetry(1)] [DOB_Day(1)] [DOB_Month(1)] [DOB_Year(2)] [FullName(50)]
 * 
 * Note: FullName is now stored directly on card (50 bytes UTF-8)
 */
public class CardData {
    public int userId;           // 0-65535
    public String fullName;      // Họ tên (lưu trên thẻ, 50 bytes UTF-8)
    public int balance;          // balance (4 bytes, max 2.1B)
    public short expiryDays;    // days until expiry
    public byte pin;             // simple PIN (1 byte = 0-255)
    public byte pinRetry;        // Retry counter: 5 → 0 (0 = permanently locked)
    public byte dobDay;          // Date of birth - day (1-31)
    public byte dobMonth;        // Date of birth - month (1-12)
    public short dobYear;        // Date of birth - year (1900-2099)
    public static final byte MAX_PIN_RETRY = 5;


    public CardData() {
    }

    public CardData(int userId, int balance, short expiryDays, byte pin, byte pinRetry, byte dobDay, byte dobMonth, short dobYear) {
        this.userId = userId;
        this.balance = balance;
        this.expiryDays = expiryDays;
        this.pin = pin;
        this.pinRetry = pinRetry;
        this.dobDay = dobDay;
        this.dobMonth = dobMonth;
        this.dobYear = dobYear;
    }

    public String getDobString() {
        if (dobDay == 0 || dobMonth == 0 || dobYear == 0) {
            return "Chưa có";
        }
        return String.format("%02d/%02d/%04d", dobDay, dobMonth, dobYear);
    }

    /**
     * Check if card is permanently locked (0 retries left)
     */
    public boolean isLocked() {
        return pinRetry == 0;
    }

    @Override
    public String toString() {
        return String.format("CardData{userId=%d, fullName=%s, dob=%s, balance=%,d VND, expiryDays=%d, pinRetry=%d/%d, locked=%s}",
                userId, fullName, getDobString(), balance, expiryDays, pinRetry, 5, isLocked() ? "YES" : "NO");
    }
}
