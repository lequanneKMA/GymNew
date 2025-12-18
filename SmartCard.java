package SmartCard;

import javacard.framework.*;

/**
 * GYM SMART CARD APPLET - 64 BYTE STRUCTURE (WITH DOB)
 * 
 * Card Data Structure (64 bytes):
 * [0-1]   UserID (2 bytes) - 0x0000 to 0xFFFF
 * [2-5]   Balance (4 bytes) - Max 2.1 billion VND
 * [6-7]   ExpiryDays (2 bytes) - Days until expiry
 * [8]     PIN (1 byte) - 0-255 (mapped from 6-digit input)
 * [9]     PINRetry (1 byte) - 5 to 0 (0 = locked)
 * [10]    DOB Day (1 byte) - 1-31
 * [11]    DOB Month (1 byte) - 1-12
 * [12-13] DOB Year (2 bytes) - 1900-2099
 * [14-63] FullName (50 bytes) - UTF-8 encoded name
 * 
 * APDU Commands:
 * - 0xB0 READ BINARY: Read all 64 bytes
 * - 0xD0 UPDATE BINARY: Write all 64 bytes (requires PIN for non-blank card)
 * - 0x20 VERIFY: Verify PIN
 */
public class SmartCard extends Applet {
    // Card data offsets (64 bytes total)
    private static final byte OFFSET_USER_ID = 0;
    private static final byte OFFSET_BALANCE = 2;
    private static final byte OFFSET_EXPIRY_DAYS = 6;
    private static final byte OFFSET_PIN = 8;
    private static final byte OFFSET_PIN_RETRY = 9;
    private static final byte OFFSET_DOB_DAY = 10;
    private static final byte OFFSET_DOB_MONTH = 11;
    private static final byte OFFSET_DOB_YEAR = 12;
    private static final byte OFFSET_FULLNAME = 14;
    
    private static final short DATA_SIZE = 64;
    private static final byte MAX_PIN_RETRY = 5;
    
    // Persistent storage (EEPROM)
    private byte[] cardData;
    
    // Security flag (RAM - cleared on power off)
    private boolean pinVerified;

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new SmartCard().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    public SmartCard() {
        // Allocate persistent storage (EEPROM)
        cardData = new byte[DATA_SIZE];
        
        // Initialize with blank card values
        Util.arrayFillNonAtomic(cardData, (short)0, DATA_SIZE, (byte)0x00);
        cardData[OFFSET_PIN_RETRY] = MAX_PIN_RETRY; // 5 attempts
        
        pinVerified = false;
    }

    public void process(APDU apdu) {
        if (selectingApplet()) return;

        byte[] buf = apdu.getBuffer();
        byte ins = buf[ISO7816.OFFSET_INS];

        switch (ins) {
            case (byte) 0xB0:  // READ BINARY
                handleRead(apdu);
                break;

            case (byte) 0xD0:  // UPDATE BINARY
                handleWrite(apdu);
                break;

            case (byte) 0x20:  // VERIFY PIN
                handleVerifyPin(apdu);
                break;

            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private void handleRead(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        
        // Copy card data to APDU buffer
        Util.arrayCopyNonAtomic(cardData, (short)0, buf, (short)0, DATA_SIZE);
        
        // Send response (60 bytes)
        apdu.setOutgoingAndSend((short)0, DATA_SIZE);
    }

    private void handleWrite(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        
        // Receive incoming data
        short bytesRead = apdu.setIncomingAndReceive();
        
        // Check data length
        if (bytesRead != DATA_SIZE) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        
        // Security check: Blank card allows first write without PIN
        boolean isBlankCard = (cardData[OFFSET_USER_ID] == 0) && (cardData[OFFSET_USER_ID + 1] == 0);
        
        if (!isBlankCard && !pinVerified) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED); // 0x6982
        }
        
        // Write data to persistent storage
        Util.arrayCopyNonAtomic(buf, ISO7816.OFFSET_CDATA, cardData, (short)0, DATA_SIZE);
    }

    private void handleVerifyPin(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();

        if (lc != 1) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        byte inputPin = buf[ISO7816.OFFSET_CDATA];
        
        // Check if card is permanently locked
        if (cardData[OFFSET_PIN_RETRY] == 0) {
            ISOException.throwIt(ISO7816.SW_FILE_INVALID); // 0x6983 (locked)
        }
        
        // Verify PIN
        if (inputPin == cardData[OFFSET_PIN]) {
            // ✅ PIN correct
            pinVerified = true;
            cardData[OFFSET_PIN_RETRY] = MAX_PIN_RETRY; // Reset retry counter
            
            // Return 0x9000 (success)
        } else {
            // ❌ PIN wrong
            pinVerified = false;
            
            // Decrement retry counter
            if (cardData[OFFSET_PIN_RETRY] > 0) {
                cardData[OFFSET_PIN_RETRY]--;
            }
            
            // Return 0x63Cx (x = retries left)
            ISOException.throwIt((short)(0x63C0 | cardData[OFFSET_PIN_RETRY]));
        }
    }
}
