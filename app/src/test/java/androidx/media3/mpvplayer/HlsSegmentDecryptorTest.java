package androidx.media3.mpvplayer;

import org.junit.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class HlsSegmentDecryptorTest {

    private static final byte[] KEY = {
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
            0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f};

    @Test
    public void decryptsAes128CbcWithPkcs7Padding() throws Exception {
        byte[] iv = HlsSegmentDecryptor.sequenceIv(7);
        byte[] plain = "0123456789abcdef0123456789abcdef-payload".getBytes("UTF-8");
        byte[] ciphertext = encrypt(KEY, iv, plain);

        byte[] decrypted = HlsSegmentDecryptor.decrypt(KEY, iv, ciphertext);

        assertNotNull(decrypted);
        assertArrayEquals(plain, decrypted);
    }

    @Test
    public void sequenceIvIsSixteenByteBigEndian() {
        byte[] iv = HlsSegmentDecryptor.sequenceIv(1);

        assertEquals(16, iv.length);
        for (int i = 0; i < 15; i++) assertEquals(0, iv[i]);
        assertEquals(1, iv[15]);
    }

    @Test
    public void sequenceIvUsesLowBytesBeyondOneByte() {
        byte[] iv = HlsSegmentDecryptor.sequenceIv(0x0102L);

        assertEquals(0, iv[13]);
        assertEquals(0x01, iv[14]);
        assertEquals(0x02, iv[15]);
    }

    @Test
    public void rejectsCiphertextThatIsNotWholeBlocks() {
        byte[] iv = HlsSegmentDecryptor.sequenceIv(0);

        assertNull(HlsSegmentDecryptor.decrypt(KEY, iv, new byte[]{1, 2, 3}));
        assertNull(HlsSegmentDecryptor.decrypt(KEY, iv, new byte[0]));
    }

    @Test
    public void rejectsWrongKeyLength() {
        byte[] iv = HlsSegmentDecryptor.sequenceIv(0);

        assertNull(HlsSegmentDecryptor.decrypt(new byte[8], iv, new byte[16]));
    }

    @Test
    public void stripsFullBlockOfPadding() {
        byte[] plain = new byte[16];
        java.util.Arrays.fill(plain, (byte) 16);

        byte[] stripped = HlsSegmentDecryptor.stripPkcs7(plain);

        assertNotNull(stripped);
        assertEquals(0, stripped.length);
    }

    @Test
    public void keepsUnpaddedDataWhenTrailingByteIsNotValidPadding() {
        byte[] data = {0x01, 0x02, 0x03, 0x00};

        byte[] stripped = HlsSegmentDecryptor.stripPkcs7(data);

        assertArrayEquals(data, stripped);
    }

    @Test
    public void keepsDataWhenPaddingIsInvalid() {
        byte[] data = {0x01, 0x02, 0x03, 0x04};

        byte[] stripped = HlsSegmentDecryptor.stripPkcs7(data);

        assertArrayEquals(data, stripped);
    }

    private static byte[] encrypt(byte[] key, byte[] iv, byte[] plain) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return cipher.doFinal(plain);
    }
}
