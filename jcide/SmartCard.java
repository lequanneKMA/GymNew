package gymcard;

import javacard.framework.*;

/**
 * Gym Smart Card Applet - Phase 1: PIN Security (Simplified)
 * Chuẩn ISO 7816-4 với PIN retry counter
 * 
 * Cấu trúc: 61 bytes
 * [UserID(2)] [Balance(4)] [ExpiryDays(2)] [PackageType(1)] [PIN(1)] [PINRetry(1)] [FullName(50)]
 * 
 * Lưu ý: Khóa tạm thời được xử lý ở server, thẻ chỉ quản lý retry counter
 */
public class SmartCard extends Applet {
    // APDU Commands (ISO 7816-4 Standard)
    private static final byte INS_READ = (byte) 0xB0;       // READ BINARY
    private static final byte INS_WRITE = (byte) 0xD0;      // UPDATE BINARY
    private static final byte INS_VERIFY_PIN = (byte) 0x20; // VERIFY (ISO standard)
    private static final byte INS_CHANGE_PIN = (byte) 0x24; // CHANGE REFERENCE DATA (ISO)
    private static final byte INS_UNBLOCK_PIN = (byte) 0x2C; // RESET RETRY COUNTER (ISO)
    
    // Data structure (61 bytes - with full name)
    private byte[] cardData;
    private static final short DATA_SIZE = 61;
    
    // Field offsets
    private static final short OFFSET_USER_ID = 0;       // 2 bytes
    private static final short OFFSET_BALANCE = 2;       // 4 bytes
    private static final short OFFSET_EXPIRY = 6;        // 2 bytes
    private static final short OFFSET_PACKAGE = 8;       // 1 byte (0=Basic, 1=Silver, 2=Gold)
    private static final short OFFSET_PIN = 9;           // 1 byte
    private static final short OFFSET_PIN_RETRY = 10;    // 1 byte (5 → 0)
    private static final short OFFSET_FULLNAME = 11;     // 50 bytes (UTF-8)
    
    // Security constants
    private static final byte MAX_PIN_TRIES = 5;
    
    // Admin key for unblocking (8 bytes)
    private static final byte[] ADMIN_KEY = {
        (byte)0x41, (byte)0x44, (byte)0x4D, (byte)0x49, // "ADMI"
        (byte)0x4E, (byte)0x4B, (byte)0x45, (byte)0x59  // "NKEY"
    };
    
    // Security state
    private boolean pinVerified;
    
    /**
     * Constructor - Initialize card with default values
     */
    protected SmartCard() {
        cardData = new byte[DATA_SIZE];
        
        // Initialize defaults
        cardData[OFFSET_PIN] = (byte) 0x00; // Default PIN = 0
        cardData[OFFSET_PIN_RETRY] = MAX_PIN_TRIES; // 5 attempts
        cardData[OFFSET_PACKAGE] = (byte) 0x00; // Basic package
        
        pinVerified = false;
        
        register();
    }
    
    /**
     * Install method called by JCRE
     */
    public static void install(byte[] buffer, short offset, byte length) {
        new SmartCard();
    }
    
    /**
     * Main APDU processor
     */
    public void process(APDU apdu) {
        // Ignore SELECT command
        if (selectingApplet()) {
            return;
        }
        
        byte[] buf = apdu.getBuffer();
        byte ins = buf[ISO7816.OFFSET_INS];
        
        switch (ins) {
            case INS_VERIFY_PIN:
                handleVerifyPIN(apdu);
                break;
            case INS_CHANGE_PIN:
                handleChangePIN(apdu);
                break;
            case INS_UNBLOCK_PIN:
                handleUnblockPIN(apdu);
                break;
            case INS_READ:
                handleRead(apdu);
                break;
            case INS_WRITE:
                handleWrite(apdu);
                break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }
    
    /**
     * VERIFY PIN (0x20) - ISO 7816-4 Standard
     * 
     * Request: 00 20 00 01 01 [PIN] (Simplified)
     * 
     * Request: 00 20 00 01 01 [PIN]
     * Response:
     *   9000 - PIN correct
     *   63Cx - PIN wrong, x attempts left
     *   6983 - Card permanently locked (0 attempts)
     */
    private void handleVerifyPIN(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        byte numBytes = (byte) apdu.setIncomingAndReceive();
        
        if (numBytes != 1) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        
        // Check if permanently locked (0 retries)
        if (cardData[OFFSET_PIN_RETRY] == 0) {
            ISOException.throwIt((short) 0x6983); // Authentication blocked
        }
        
        // Verify PIN
        byte inputPin = buf[ISO7816.OFFSET_CDATA];
        
        if (inputPin == cardData[OFFSET_PIN]) {
            // ✅ Correct PIN
            cardData[OFFSET_PIN_RETRY] = MAX_PIN_TRIES; // Reset counter to 5
            pinVerified = true;
            // Return 9000 (success)
        } else {
            // ❌ Wrong PIN
            cardData[OFFSET_PIN_RETRY]--;
            pinVerified = false;// Return 63Cx where x = remaining attempts
            short sw = (short) (0x63C0 | (cardData[OFFSET_PIN_RETRY] & 0x0F));
            ISOException.throwIt(sw);
        }
    }
    
    /**
     * CHANGE PIN (0x24) - ISO 7816-4 Standard
     * 
     * Request: 00 24 00 01 02 [Old PIN][New PIN]
     * Response:
     *   9000 - Success
     *   6982 - Security status not satisfied (need verify first)
     *   63Cx - Wrong old PIN
     */
    private void handleChangePIN(APDU apdu) {
        // Must verify PIN first
        if (!pinVerified) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED); // 6982
        }
        
        byte[] buf = apdu.getBuffer();
        byte numBytes = (byte) apdu.setIncomingAndReceive();
        
        if (numBytes < 2) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        
        byte oldPIN = buf[ISO7816.OFFSET_CDATA];
        byte newPIN = buf[ISO7816.OFFSET_CDATA + 1];
        
        // Verify old PIN again for security
        if (oldPIN != cardData[OFFSET_PIN]) {
            cardData[OFFSET_PIN_RETRY]--;
            short sw = (short) (0x63C0 | (cardData[OFFSET_PIN_RETRY] & 0x0F));
            pinVerified = false;
            ISOException.throwIt(sw);
        }
        
        // Change PIN
        cardData[OFFSET_PIN] = newPIN;
        pinVerified = false; // Require re-verification with new PIN
        
        // TODO Phase 2: Regenerate AES key from new PIN
    }
    
    /**
     * UNBLOCK PIN (0x2C) - Admin Only
     * 
     * Request: 00 2C 00 01 08 [Admin Key 8 bytes]
     * Response:
     *   9000 - PIN retry counter reset to 5
     *   6982 - Admin key invalid
     */
    private void handleUnblockPIN(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        byte numBytes = (byte) apdu.setIncomingAndReceive();
        
        if (numBytes < 8) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        
        // Verify admin key
        boolean keyMatch = true;
        for (short i = 0; i < 8; i++) {
            if (buf[(short)(ISO7816.OFFSET_CDATA + i)] != ADMIN_KEY[i]) {
                keyMatch = false;
                break;
            }
        }
        
        if (!keyMatch) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED); // 6982
        }
        
        // Reset PIN retry counter and unlock
        cardData[OFFSET_PIN_RETRY] = MAX_PIN_TRIES;
        pinVerified = false;
        
        // Return 9000 (success)
    }
    
    /**
     * READ BINARY (0xB0) - Read all card data
     * 
     * Request: 00 B0 00 00 3D (61 bytes)
     * Response: [61 bytes of data]
     */
    private void handleRead(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        Util.arrayCopyNonAtomic(cardData, (short) 0, buf, (short) 0, DATA_SIZE);
        apdu.setOutgoingAndSend((short) 0, DATA_SIZE);
    }
    
    /**
     * UPDATE BINARY (0xD0) - Write card data (requires PIN)
     * 
     * Request: 00 D0 00 00 3D [61 bytes]
     * Response:
     *   9000 - Success
     *   6982 - PIN not verified
     */
    private void handleWrite(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        byte numBytes = (byte) apdu.setIncomingAndReceive();
        
        if (numBytes < DATA_SIZE) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        
        // Check if card is blank (userId = 0) - allow first-time initialization
        boolean isBlankCard = (cardData[OFFSET_USER_ID] == 0) && (cardData[OFFSET_USER_ID + 1] == 0);
        
        // Must verify PIN before writing (except for blank card initialization)
        if (!isBlankCard && !pinVerified) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED); // 6982
        }
        
        // Copy data to card (preserve PIN retry counter)
        byte oldRetry = cardData[OFFSET_PIN_RETRY];
        
        Util.arrayCopyNonAtomic(buf, ISO7816.OFFSET_CDATA, cardData, (short) 0, DATA_SIZE);
        
        // Restore retry counter (cannot be overwritten by client)
        cardData[OFFSET_PIN_RETRY] = oldRetry;
        pinVerified = false; // Require re-verification after write
    }
}