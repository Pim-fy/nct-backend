package nct.global.security.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

// @ai_generated
/**
 * 개인정보 필드의 AES-256-GCM 암복호화와 조회용 HMAC-SHA-256 생성을 한 곳에서 책임진다.
 * 암호문 형식은 v1.{nonce Base64URL}.{ciphertext+tag Base64URL}이다.
 */
@Service
@RequiredArgsConstructor
public class FieldCryptoService {

    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int AES_KEY_BYTES = 32;
    private static final int GCM_NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int HMAC_MINIMUM_KEY_BYTES = 32;

    private final CryptoProperties cryptoProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    private SecretKey aesKey;
    private byte[] hmacKey;

    @PostConstruct
    public void initializeKeys() {
        byte[] aesKeyBytes = decodeRequiredKey(cryptoProperties.getAesKeyBase64(), "AES");
        if (aesKeyBytes.length != AES_KEY_BYTES) {
            throw new IllegalStateException("APP_CRYPTO_AES_KEY_BASE64 must decode to 32 bytes.");
        }
        byte[] decodedHmacKey = decodeRequiredKey(cryptoProperties.getHmacKeyBase64(), "HMAC");
        if (decodedHmacKey.length < HMAC_MINIMUM_KEY_BYTES) {
            throw new IllegalStateException("APP_CRYPTO_HMAC_KEY_BASE64 must decode to at least 32 bytes.");
        }
        aesKey = new SecretKeySpec(aesKeyBytes, "AES");
        hmacKey = decodedHmacKey;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] nonce = new byte[GCM_NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return "v1." + base64Url(nonce) + "." + base64Url(encrypted);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to encrypt personal data.", ex);
        }
    }

    public String decrypt(String envelope) {
        if (envelope == null) {
            return null;
        }
        try {
            String[] parts = envelope.split("\\.", -1);
            if (parts.length != 3 || !"v1".equals(parts[0])) {
                throw new IllegalArgumentException("Unsupported encrypted field format.");
            }
            byte[] nonce = base64UrlDecode(parts[1]);
            byte[] ciphertextAndTag = base64UrlDecode(parts[2]);
            if (nonce.length != GCM_NONCE_BYTES || ciphertextAndTag.length < GCM_TAG_BITS / 8) {
                throw new IllegalArgumentException("Invalid encrypted field format.");
            }
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new String(cipher.doFinal(ciphertextAndTag), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new CustomException(ErrorCode.DATABASE_ERROR, "개인정보 복호화에 실패했습니다.");
        }
    }

    public String hmac(String value) {
        if (value == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return toLowerHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to generate personal data lookup key.", ex);
        }
    }

    public String emailHmac(String email) {
        return hmac(normalizeEmail(email));
    }

    public String providerKeyHmac(String provider, String providerKey) {
        return hmac(provider + ":" + providerKey);
    }

    public String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private byte[] decodeRequiredKey(String encodedKey, String keyName) {
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalStateException("Missing " + keyName + " field-crypto key configuration.");
        }
        try {
            return Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid " + keyName + " field-crypto key configuration.", ex);
        }
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private String toLowerHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }
}
