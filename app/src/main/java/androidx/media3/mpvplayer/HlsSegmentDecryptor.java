package androidx.media3.mpvplayer;

import androidx.annotation.Nullable;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-128-CBC decryption for HLS segments the packaged IJK runtime cannot decrypt itself. */
final class HlsSegmentDecryptor {

    private static final String TRANSFORMATION = "AES/CBC/NoPadding";

    private HlsSegmentDecryptor() {
    }

    /**
     * @param key 16-byte AES key
     * @param iv 16-byte IV, explicit from the playlist or derived from the media sequence number
     * @param data ciphertext in whole AES blocks
     * @return plaintext with PKCS#7 padding removed, or null when the input is not decryptable
     */
    @Nullable
    static byte[] decrypt(byte[] key, byte[] iv, byte[] data) {
        if (key == null || key.length != 16 || iv == null || iv.length != 16) return null;
        if (data == null || data.length == 0 || data.length % 16 != 0) return null;
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new IvParameterSpec(iv));
            return stripPkcs7(cipher.doFinal(data));
        } catch (Exception e) {
            return null;
        }
    }

    /** HLS AES-128 segments carry PKCS#7 padding; a full block may be padding alone. */
    @Nullable
    static byte[] stripPkcs7(byte[] plain) {
        if (plain == null || plain.length == 0) return plain;
        int padding = plain[plain.length - 1] & 0xFF;
        if (padding < 1 || padding > 16 || padding > plain.length) return plain;
        for (int i = plain.length - padding; i < plain.length; i++) {
            if ((plain[i] & 0xFF) != padding) return plain;
        }
        byte[] trimmed = new byte[plain.length - padding];
        System.arraycopy(plain, 0, trimmed, 0, trimmed.length);
        return trimmed;
    }

    /**
     * IV derived from the segment's media sequence number (RFC 8216 section 5.2): a 16-byte
     * big-endian integer.
     */
    static byte[] sequenceIv(long sequence) {
        byte[] iv = new byte[16];
        for (int i = 0; i < 8; i++) {
            iv[15 - i] = (byte) (sequence >>> (i * 8));
        }
        return iv;
    }
}
