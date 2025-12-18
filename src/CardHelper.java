import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

/**
 * Helpers to build/parse APDU commands for gym smart card.
 * Applet INS codes (ISO 7816-4 Standard):
 *  0xB0 = READ BINARY (read card data)
 *  0xD0 = UPDATE BINARY (write card data)
 *  0x20 = VERIFY (verify PIN)
 *  0x24 = CHANGE REFERENCE DATA (change PIN)
 *  0x2C = RESET RETRY COUNTER (unblock PIN - admin only)
 */
public class CardHelper {
    public static final byte INS_READ = (byte) 0xB0;
    public static final byte INS_WRITE = (byte) 0xD0;
    public static final byte INS_VERIFY_PIN = (byte) 0x20;
    public static final byte INS_CHANGE_PIN = (byte) 0x24;
    public static final byte INS_UNBLOCK_PIN = (byte) 0x2C;

    /**
     * Build: 00 B0 00 00 40 (read 64 bytes - with DOB and full name)
     * ISO 7816-4 READ BINARY command
     */
    public static CommandAPDU buildReadCommand() {
        return new CommandAPDU(0x00, INS_READ, 0x00, 0x00, 0x40); // Le = 64 bytes (0x40)
    }

    /**
     * Build: 00 D0 00 00 40 [data...] (write 64 bytes)
     * ISO 7816-4 UPDATE BINARY command
     * Format: [userID(2)] [balance(4)] [expiryDays(2)] [pin(1)] [pinRetry(1)] [dobDay(1)] [dobMonth(1)] [dobYear(2)] [fullName(50)]
     */
    public static CommandAPDU buildWriteCommand(CardData card) {
        byte[] data = new byte[64];
        
        // UserID (2 bytes)
        data[0] = (byte) ((card.userId >> 8) & 0xFF);
        data[1] = (byte) (card.userId & 0xFF);
        
        // Balance (4 bytes)
        data[2] = (byte) ((card.balance >> 24) & 0xFF);
        data[3] = (byte) ((card.balance >> 16) & 0xFF);
        data[4] = (byte) ((card.balance >> 8) & 0xFF);
        data[5] = (byte) (card.balance & 0xFF);
        
        // ExpiryDays (2 bytes)
        data[6] = (byte) ((card.expiryDays >> 8) & 0xFF);
        data[7] = (byte) (card.expiryDays & 0xFF);
        
        // PIN (1 byte)
        data[8] = card.pin;
        
        // PINRetry (1 byte)
        data[9] = card.pinRetry;
        
        // DOB Day (1 byte)
        data[10] = card.dobDay;
        
        // DOB Month (1 byte)
        data[11] = card.dobMonth;
        
        // DOB Year (2 bytes)
        data[12] = (byte) ((card.dobYear >> 8) & 0xFF);
        data[13] = (byte) (card.dobYear & 0xFF);
        
        // FullName (50 bytes) - UTF-8 encoded
        if (card.fullName != null && !card.fullName.isEmpty()) {
            try {
                byte[] nameBytes = card.fullName.getBytes("UTF-8");
                int len = Math.min(nameBytes.length, 50);
                System.arraycopy(nameBytes, 0, data, 14, len);
            } catch (Exception e) {
                // Ignore encoding errors
            }
        }
        
        return new CommandAPDU(0x00, INS_WRITE, 0x00, 0x00, data);
    }

    /**
     * Build: 00 20 00 01 01 [pin] - ISO 7816-4 VERIFY command
     * Verify the PIN on card.
     */
    public static CommandAPDU buildVerifyPinCommand(byte pin) {
        return new CommandAPDU(0x00, INS_VERIFY_PIN, 0x00, 0x01, new byte[]{pin});
    }
    
    /**
     * Build: 00 24 00 01 02 [old PIN][new PIN] - ISO 7816-4 CHANGE REFERENCE DATA
     * Change PIN (requires prior PIN verification)
     */
    public static CommandAPDU buildChangePinCommand(byte oldPin, byte newPin) {
        return new CommandAPDU(0x00, INS_CHANGE_PIN, 0x00, 0x01, new byte[]{oldPin, newPin});
    }
    
    /**
     * Build: 00 2C 00 01 08 [admin key 8 bytes] - ISO 7816-4 RESET RETRY COUNTER
     * Unblock PIN (admin only) - resets retry counter to 5
     */
    public static CommandAPDU buildUnblockPinCommand(byte[] adminKey) {
        if (adminKey == null || adminKey.length != 8) {
            throw new IllegalArgumentException("Admin key must be exactly 8 bytes");
        }
        return new CommandAPDU(0x00, INS_UNBLOCK_PIN, 0x00, 0x01, adminKey);
    }

    /**
     * Parse response from READ command. Expects 64-byte format.
     * Format: [UserID(2) | Balance(4) | ExpiryDays(2) | PIN(1) | PINRetry(1) | DOB_Day(1) | DOB_Month(1) | DOB_Year(2) | FullName(50)]
     */
    public static CardData parseReadResponse(byte[] data) {
        if (data.length < 64) {
            throw new IllegalArgumentException("Response too short: " + data.length + " (need 64)");
        }
        
        CardData card = new CardData();
        
        // UserID (2 bytes)
        card.userId = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
        
        // Balance (4 bytes)
        card.balance = ((data[2] & 0xFF) << 24) | ((data[3] & 0xFF) << 16) | 
                       ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
        
        // ExpiryDays (2 bytes)
        card.expiryDays = (short) (((data[6] & 0xFF) << 8) | (data[7] & 0xFF));
        
        // PIN (1 byte)
        card.pin = data[8];
        
        // PINRetry (1 byte)
        card.pinRetry = data[9];
        
        // DOB Day (1 byte)
        card.dobDay = data[10];
        
        // DOB Month (1 byte)
        card.dobMonth = data[11];
        
        // DOB Year (2 bytes)
        card.dobYear = (short) (((data[12] & 0xFF) << 8) | (data[13] & 0xFF));
        
        // FullName (50 bytes) - UTF-8 decode
        try {
            // Find actual length (until null byte or end)
            int nameLen = 0;
            for (int i = 14; i < 64; i++) {
                if (data[i] == 0) break;
                nameLen++;
            }
            if (nameLen > 0) {
                card.fullName = new String(data, 14, nameLen, "UTF-8").trim();
            } else {
                card.fullName = "";
            }
        } catch (Exception e) {
            card.fullName = "";
        }
        
        return card;
    }
    
    /**
     * Parse PIN verification status from SW code
     * 
     * @param sw Status Word from VERIFY PIN response
     * @return Human-readable status message
     */
    public static String parsePinStatus(int sw) {
        if (sw == 0x9000) {
            return "✅ PIN Correct";
        } else if (sw == 0x6983) {
            return "🔒 Card Permanently Locked (0 attempts left)";
        } else if ((sw & 0xFFF0) == 0x63C0) {
            int tries = sw & 0x0F;
            return "❌ PIN Wrong - " + tries + "/5 attempts remaining";
        } else if (sw == 0x6982) {
            return "⚠️ Security status not satisfied (verify PIN first)";
        } else {
            return "❓ Unknown error: 0x" + Integer.toHexString(sw).toUpperCase();
        }
    }

    /**
     * Get APDU as hex string for debugging.
     */
    public static String toHexCommand(CommandAPDU apdu) {
        byte[] bytes = apdu.getBytes();
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}
