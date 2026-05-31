package xxx.com.encrypt;

import org.apache.commons.lang3.RandomStringUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * A utility class for encrypting and decrypting text using AES-256 with GCM.
 * This class now uses char arrays for passkeys to enhance security.
 * It uses PBKDF2WithHmacSHA256 to derive a key from the passkey.
 * It uses a random salt for key generation and a random IV for each encryption.
 * GCM mode provides both confidentiality and authenticity.
 */
public class EncryptAESGCM {

  // Defines the algorithm for the secret key specification.
  private static final String ALGORITHM = "AES";
  // Defines the salt length in bytes.
  private static final int SALT_LENGTH_BYTES = 16;
  // Defines the IV length in bytes for GCM mode. 12 is recommended.
  private static final int IV_LENGTH_BYTES = 12;
  // Defines the GCM authentication tag length in bits. 128 is recommended.
  private static final int TAG_LENGTH_BITS = 128;
  // Defines the key length in bits for AES-256.
  private static final int KEY_LENGTH_BITS = 256;
  // Defines the number of iterations for the key derivation function.
  private static final int ITERATION_COUNT = 65536;

  /**
   * Encrypts a plain text string using a character array for the passkey.
   *
   * @param passKey The passkey as a char array.
   * @param plainText The text to encrypt.
   * @return A Base64 encoded string containing the salt, IV, and encrypted data.
   * @throws Exception if an error occurs during encryption.
   */
  public static String encrypt(char[] passKey, String plainText) throws Exception {
    // 1. Generate a random salt.
    SecureRandom secureRandom = new SecureRandom();
    byte[] salt = new byte[SALT_LENGTH_BYTES];
    secureRandom.nextBytes(salt);

    // 2. Derive the key from the passKey and the salt.
    SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
    KeySpec spec = new PBEKeySpec(passKey, salt, ITERATION_COUNT, KEY_LENGTH_BITS);

    SecretKey tmp = null;
    try {
      tmp = factory.generateSecret(spec);
    } finally {
      // CORRECTED: Cast spec to PBEKeySpec to access the clearPassword method.
      ((PBEKeySpec) spec).clearPassword();
    }

    SecretKeySpec secretKey = new SecretKeySpec(tmp.getEncoded(), ALGORITHM);

    // 3. Generate a random Initialization Vector (IV).
    byte[] iv = new byte[IV_LENGTH_BYTES];
    secureRandom.nextBytes(iv);
    GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);

    // 4. Initialize the Cipher for encryption using AES/GCM/NoPadding.
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec);

    // 5. Encrypt the plain text.
    byte[] encryptedText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

    // 6. Combine salt, IV, and encrypted text.
    byte[] combined = new byte[salt.length + iv.length + encryptedText.length];
    System.arraycopy(salt, 0, combined, 0, salt.length);
    System.arraycopy(iv, 0, combined, salt.length, iv.length);
    System.arraycopy(encryptedText, 0, combined, salt.length + iv.length, encryptedText.length);

    // 7. Base64 encode the combined byte array.
    return Base64.getEncoder().encodeToString(combined);
  }

  /**
   * Decrypts a Base64 encoded string using a character array for the passkey.
   *
   * @param passKey The passkey as a char array.
   * @param encryptedText The Base64 encoded text to decrypt.
   * @return The original plain text string.
   * @throws Exception if an error occurs during decryption (e.g., wrong passKey).
   */
  public static String decrypt(char[] passKey, String encryptedText) throws Exception {
    // 1. Base64 decode the encrypted text.
    byte[] decoded = Base64.getDecoder().decode(encryptedText);

    // 2. Extract salt, IV, and the actual encrypted data.
    byte[] salt = new byte[SALT_LENGTH_BYTES];
    System.arraycopy(decoded, 0, salt, 0, salt.length);

    byte[] iv = new byte[IV_LENGTH_BYTES];
    System.arraycopy(decoded, salt.length, iv, 0, iv.length);

    byte[] encrypted = new byte[decoded.length - salt.length - iv.length];
    System.arraycopy(decoded, salt.length + iv.length, encrypted, 0, encrypted.length);

    // 3. Re-derive the key from the passKey and extracted salt.
    SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
    KeySpec spec = new PBEKeySpec(passKey, salt, ITERATION_COUNT, KEY_LENGTH_BITS);

    SecretKey tmp = null;
    try {
      tmp = factory.generateSecret(spec);
    } finally {
      // CORRECTED: Cast spec to PBEKeySpec to access the clearPassword method.
      ((PBEKeySpec) spec).clearPassword();
    }

    SecretKeySpec secretKey = new SecretKeySpec(tmp.getEncoded(), ALGORITHM);

    // 4. Initialize Cipher for decryption.
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

    // 5. Decrypt the data.
    byte[] decryptedText = cipher.doFinal(encrypted);

    // 6. Convert the decrypted byte array back to a string.
    return new String(decryptedText, StandardCharsets.UTF_8);
  }

  /**
   * Validates the passkey by performing a self-test encryption and decryption cycle.
   * This can be used to check if the provided passkey is correct for decrypting
   * existing data, as an incorrect passkey will cause a decryption failure.
   *
   * @param passKey The passkey to validate, as a char array.
   * @return true if the passkey is valid for encryption/decryption, false otherwise.
   */
  public static boolean validatepassKey(char[] passKey) {
    if (passKey == null || passKey.length == 0) {
      return false;
    }
    try {
      // A simple, constant string is sufficient for a self-test.
      String testData = "AES-GCM-VALIDATION-STRING";

      // Perform an encrypt-decrypt cycle.
      String encryptedData = EncryptAESGCM.encrypt(passKey, testData);
      String decryptedData = EncryptAESGCM.decrypt(passKey, encryptedData);

      // If the decrypted data matches the original, the passkey is correct.
      return testData.equals(decryptedData);
    }
    catch (Exception e) {
      // Any exception during the cycle (most likely during decryption
      // due to a bad passkey) indicates a failure.
      return false;
    }
  }

  /**
   * Main method to demonstrate the new instance-based usage.
   */
  public static void main(String[] args) {
    try {
      String originalString = "This is a secret message!";
      char[] passKey = "MySuperSecretPassKey123".toCharArray();

      System.out.println("Original String: " + originalString);
      System.out.println("passKey: [hidden]");
      System.out.println("------------------------------------");

      // 2. Encrypt the string using the char array.
      String encryptedString = EncryptAESGCM.encrypt(passKey, originalString);
      System.out.println("Encrypted String: " + encryptedString);

      // 3. Decrypt the string using the same char array.
      String decryptedString = EncryptAESGCM.decrypt(passKey, encryptedString);
      System.out.println("Decrypted String: " + decryptedString);

      System.out.println("------------------------------------");
      // Verification
      if (originalString.equals(decryptedString)) {
        System.out.println("Success: The original and decrypted strings match.");
      } else {
        System.out.println("Failure: The strings do not match.");
      }

      // Clean up the passkey from memory
      Arrays.fill(passKey, '\0');
      System.out.println("Passkey has been cleared from memory.");

    } catch (Exception e) {
      System.err.println("An error occurred: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
