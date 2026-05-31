package xxx.com.encrypt;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * A utility class for encrypting and decrypting text using AES-256.
 * This class uses PBKDF2WithHmacSHA256 to derive a key from a password.
 * It uses a random salt for key generation and a random IV for each encryption.
 */
public class Aes256 {

  // Defines the algorithm for the secret key specification.
  private static final String ALGORITHM = "AES";
  // Defines the block size for AES in bytes (128 bits).
  private static final int IV_LENGTH_BYTES = 16;
  // Defines the key length in bits for AES-256.
  private static final int KEY_LENGTH_BITS = 256;
  // Defines the number of iterations for the key derivation function.
  private static final int ITERATION_COUNT = 65536;
  // Defines the salt length in bytes.
  private static final int SALT_LENGTH_BYTES = 16;

  /**
   * Encrypts a plain text string using a password.
   *
   * @param plainText The text to encrypt.
   * @param password  The password to use for encryption.
   * @return A Base64 encoded string containing the salt, IV, and encrypted data.
   * @throws Exception if an error occurs during encryption.
   */
  public static String encrypt(String plainText, String password) throws Exception {
    // 1. Generate a random salt.
    SecureRandom secureRandom = new SecureRandom();
    byte[] salt = new byte[SALT_LENGTH_BYTES];
    secureRandom.nextBytes(salt);

    // 2. Derive the key from the password and salt.
    SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
    KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH_BITS);
    SecretKey tmp = factory.generateSecret(spec);
    SecretKeySpec secretKey = new SecretKeySpec(tmp.getEncoded(), ALGORITHM);

    // 3. Generate a random Initialization Vector (IV).
    byte[] iv = new byte[IV_LENGTH_BYTES];
    secureRandom.nextBytes(iv);
    IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

    // 4. Initialize the Cipher for encryption.
    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);

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
   * Decrypts a Base64 encoded string using a password.
   *
   * @param encryptedText The Base64 encoded text to decrypt.
   * @param password      The password to use for decryption.
   * @return The original plain text string.
   * @throws Exception if an error occurs during decryption.
   */
  public static String decrypt(String encryptedText, String password) throws Exception {
    // 1. Base64 decode the encrypted text.
    byte[] decoded = Base64.getDecoder().decode(encryptedText);

    // 2. Extract salt, IV, and the actual encrypted data.
    byte[] salt = new byte[SALT_LENGTH_BYTES];
    System.arraycopy(decoded, 0, salt, 0, salt.length);

    byte[] iv = new byte[IV_LENGTH_BYTES];
    System.arraycopy(decoded, salt.length, iv, 0, iv.length);

    byte[] encrypted = new byte[decoded.length - salt.length - iv.length];
    System.arraycopy(decoded, salt.length + iv.length, encrypted, 0, encrypted.length);

    // 3. Re-derive the key from the password and extracted salt.
    SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
    KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH_BITS);
    SecretKey tmp = factory.generateSecret(spec);
    SecretKeySpec secretKey = new SecretKeySpec(tmp.getEncoded(), ALGORITHM);

    // 4. Initialize Cipher for decryption.
    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));

    // 5. Decrypt the data.
    byte[] decryptedText = cipher.doFinal(encrypted);

    // 6. Convert the decrypted byte array back to a string.
    return new String(decryptedText, StandardCharsets.UTF_8);
  }

  /**
   * Main method to demonstrate the encryption and decryption process.
   */
  public static void main(String[] args) {
    try {
      String originalString = "This is a secret message!";
      String password = "MySuperSecretPassword123";

      System.out.println("Original String: " + originalString);
      System.out.println("Password: " + password);
      System.out.println("------------------------------------");


      // Encrypt the string
      String encryptedString = Aes256.encrypt(originalString, password);
      System.out.println("Encrypted String: " + encryptedString);

      // Decrypt the string
      String decryptedString = Aes256.decrypt(encryptedString, password);
      System.out.println("Decrypted String: " + decryptedString);

      System.out.println("------------------------------------");
      // Verification
      if (originalString.equals(decryptedString)) {
        System.out.println("Success: The original and decrypted strings match.");
      } else {
        System.out.println("Failure: The strings do not match.");
      }

    } catch (Exception e) {
      System.err.println("An error occurred: " + e.getMessage());
      e.printStackTrace();
    }
  }
}

