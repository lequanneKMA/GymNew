/**
 * Represents data stored on a smart card.
 * Structure (11 bytes): [UserID(2)] [Balance(4)] [ExpiryDays(2)] [PackageType(1)] [PIN(1)] [PINRetry(1)]
 * 
 * Note: Temporary lock (10 min after 3 wrong attempts) is handled by server, not card.
 */
public class CardData {
    public int userId;           // 0-65535
    public int balance;          // balance (4 bytes, max 2.1B)
    public short expiryDays;    // days until expiry
    public byte packageType;     // 0=Basic, 1=Silver, 2=Gold, 3=Platinum
    public byte pin;             // simple PIN (1 byte = 0-255)
    public byte pinRetry;        // Retry counter: 5 → 0 (0 = permanently locked)
    public static final byte MAX_PIN_RETRY = 5;


    public CardData() {
    }

    public CardData(int userId, int balance, short expiryDays, byte packageType, byte pin, byte pinRetry) {
        this.userId = userId;
        this.balance = balance;
        this.expiryDays = expiryDays;
        this.packageType = packageType;
        this.pin = pin;
        this.pinRetry = pinRetry;
    }

    /**
     * Check if card is permanently locked (0 retries left)
     */
    public boolean isLocked() {
        return pinRetry == 0;
    }
    
    /**
     * Get package name
     */
    public String getPackageName() {
        switch (packageType) {
            case 0: return "Basic";
            case 1: return "Silver";
            case 2: return "Gold";
            case 3: return "Platinum";
            default: return "Unknown";
        }
    }

    @Override
    public String toString() {
        return String.format("CardData{userId=%d, balance=%,d VND, expiryDays=%d, package=%s, pinRetry=%d/%d, locked=%s}",
                userId, balance, expiryDays, getPackageName(), pinRetry, 5, isLocked() ? "YES" : "NO");
    }
}
