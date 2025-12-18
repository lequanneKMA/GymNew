package SmartCard;

import javacard.framework.*;

/**
 * GYM Smart Card Applet - Hỗ trợ đọc/ghi dữ liệu thẻ đầy đủ
 * 
 * Structure (13 bytes) - UPDATED to support larger balance:
 *  [0-1]   UserID (2 bytes)
 *  [2-5]   Balance (4 bytes) ← CHANGED from 2 bytes to 4 bytes
 *  [6-7]   ExpiryDays (2 bytes)
 *  [8]     PIN (1 byte)
 *  [9]     DOB Day (1 byte)
 *  [10]    DOB Month (1 byte)
 *  [11-12] DOB Year (2 bytes)
 * 
 * APDU Commands:
 *  0x11 = READ (returns 13 bytes)
 *  0x12 = WRITE (sets 13 bytes)
 *  0x13 = VERIFY PIN (verify then return 0x9000)
 */
public class SmartCard extends Applet {
    private byte[] cardData;

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new SmartCard().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    public SmartCard() {
        cardData = new byte[13];  // CHANGED: 11 → 13 bytes
        // Initialize with default data
        cardData[0] = (byte) 0x00;  // UserID = 0
        cardData[1] = (byte) 0x00;
        cardData[2] = (byte) 0x00;  // Balance = 0 (4 bytes)
        cardData[3] = (byte) 0x00;
        cardData[4] = (byte) 0x00;  // NEW: Balance high bytes
        cardData[5] = (byte) 0x00;
        cardData[6] = (byte) 0x00;  // ExpiryDays = 0
        cardData[7] = (byte) 0x00;
        cardData[8] = (byte) 0x00;  // PIN = 0 (MOVED from [6])
        cardData[9] = (byte) 0x01;  // DOB Day = 1 (MOVED from [7])
        cardData[10] = (byte) 0x01; // DOB Month = 1 (MOVED from [8])
        cardData[11] = (byte) 0x07; // DOB Year = 2000 (MOVED from [9-10])
        cardData[12] = (byte) 0xD0;
    }

    public void process(APDU apdu) {
        if (selectingApplet()) return;

        byte[] buf = apdu.getBuffer();
        byte ins = buf[ISO7816.OFFSET_INS];

        switch (ins) {
            case (byte) 0x11:  // READ
                handleRead(apdu);
                break;

            case (byte) 0x12:  // WRITE
                handleWrite(apdu);
                break;

            case (byte) 0x13:  // VERIFY PIN
                handleVerifyPin(apdu);
                break;

            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private void handleRead(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short le = apdu.setOutgoing();
        
        // Return all 13 bytes of card data (CHANGED from 11)
        if (le < 13) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        
        apdu.setOutgoingLength((short) 13);
        Util.arrayCopyNonAtomic(cardData, (short) 0, buf, (short) 0, (short) 13);
        apdu.sendBytes((short) 0, (short) 13);
    }

    private void handleWrite(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();

        if (lc != 13) {  // CHANGED from 11
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        // Copy received data to card storage
        Util.arrayCopyNonAtomic(buf, ISO7816.OFFSET_CDATA, cardData, (short) 0, (short) 13);
    }

    private void handleVerifyPin(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();

        if (lc != 1) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        byte providedPin = buf[ISO7816.OFFSET_CDATA];
        byte storedPin = cardData[8];  // CHANGED: PIN moved from [6] to [8]

        if (providedPin != storedPin) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        // PIN correct - return 0x9000 (implicit)
    }
}
