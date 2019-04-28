package me.stojan.pasbox.argon2;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * Provides a consistent interface to use the Argon2 Password Hashing Function.
 */
public final class Argon2 {
  /**
   * Argon2 type ID version 1.3.
   */
  public static final String ARGON2_ID_13 = "Argon2id13";
  /**
   * Best recommended Argon2 configuration. See {@link #ARGON2_ID_13}.
   */
  public static final String BEST = ARGON2_ID_13;

  static {
    System.loadLibrary("argon2");
  }

  private final Argon2Engine engine;

  Argon2(@NonNull Argon2Engine engine) {
    this.engine = engine;
  }

  /**
   * Obtain an instance of {@link Argon2}.
   *
   * @param type the type of instance, see {@link #BEST}
   * @return the Argon2 instance
   */
  public static Argon2 getInstance(@NonNull String type) throws Argon2Exception {
    if (ARGON2_ID_13.equals(type)) {
      return new Argon2(new NativeArgon2(Argon2Engine.ARGON2_TYPE_ID, Argon2Engine.ARGON2_VERSION_13));
    } else {
      throw new Argon2Exception("Unknown Argon2 instance type " + type);
    }
  }

  /**
   * Obtain an instance of {@link Argon2} for the provided type and version.
   *
   * @param type    the type of instance, one of {@link Argon2Engine#ARGON2_TYPE_D}, {@link Argon2Engine#ARGON2_TYPE_I} or {@link Argon2Engine#ARGON2_TYPE_ID}
   * @param version the version, one of {@link Argon2Engine#ARGON2_VERSION_10} or {@link Argon2Engine#ARGON2_VERSION_13}
   * @return the Argon2 instance
   */
  public static Argon2 getInstance(int type, int version) {
    return new Argon2(new NativeArgon2(type, version));
  }

  /**
   * The type of Argon2 configuration.
   *
   * @return one of {@link Argon2Engine#ARGON2_TYPE_I}, {@link Argon2Engine#ARGON2_TYPE_D}, {@link Argon2Engine#ARGON2_TYPE_ID}
   */
  public int type() {
    return this.engine.type();
  }

  /**
   * The Argon2 version.
   *
   * @return one of {@link Argon2Engine#ARGON2_VERSION_10}, {@link Argon2Engine#ARGON2_VERSION_13}
   */
  public int version() {
    return this.engine.version();
  }

  /**
   * Hashes the provided password and salt into a byte array of a specified size.
   *
   * @param tCost       number of interations
   * @param mCost       memory requirements in kibibytes (1000 bytes, NOT 1024)
   * @param parallelism number of lanes (threads)
   * @param password    the password bytes, must not be null
   * @param salt        the salt bytes, must not be null
   * @param outLength   the size of the output hash
   * @return the hash of size outLength, never null
   * @throws Argon2Exception if Argon2 hashing fails
   */
  @NonNull
  public final byte[] hash(int tCost, int mCost, int parallelism, @NonNull byte[] password, @NonNull byte[] salt, int outLength) throws Argon2Exception {
    if (outLength <= 0) {
      throw new Argon2Exception("outLength must be > 0");
    }

    return this.hash(tCost, mCost, parallelism, password, salt, new byte[outLength]);
  }

  /**
   * Hashes the provided password and salt into the provided byte array.
   *
   * @param tCost       number of interations
   * @param mCost       memory requirements in kibibytes (1000 bytes, NOT 1024)
   * @param parallelism number of lanes (threads)
   * @param password    the password bytes, must not be null
   * @param salt        the salt bytes, must not be null
   * @param out         the output array, must not be null
   * @return the output array with the hash data written inside
   * @throws Argon2Exception if Argon2 hashing fails
   */
  @NonNull
  public final byte[] hash(int tCost, int mCost, int parallelism, @NonNull byte[] password, @NonNull byte[] salt, @NonNull byte[] out) throws Argon2Exception {
    if (tCost <= 0) {
      throw new Argon2Exception("tCost must be > 0");
    }

    if (mCost <= 0) {
      throw new Argon2Exception("mCost must be > 0");
    }

    if (parallelism <= 0) {
      throw new Argon2Exception("parallelism must be > 0");
    }

    if (password.length <= 0) {
      throw new Argon2Exception("password length must be > 0");
    }

    if (salt.length <= 0) {
      throw new Argon2Exception("salt length must be > 0");
    }

    if (out.length <= 0) {
      throw new Argon2Exception("out length must be > 0");
    }

    return this.engine.hash(tCost, mCost, parallelism, password, salt, out);
  }

  /**
   * Verifies in constant time that an existing hash matches a hash derived from the provided parameters.
   *
   * @param inHash      the existing hash
   * @param tCost       number of interations
   * @param mCost       memory requirements in kibibytes (1000 bytes, NOT 1024)
   * @param parallelism number of lanes (threads)
   * @param password    the password bytes, must not be null
   * @param salt        the salt bytes, must not be null
   * @return true if the provided hash matches the derived hash
   * @throws Argon2Exception if hashing failed
   */
  public final boolean verify(@NonNull byte[] inHash, int tCost, int mCost, int parallelism, @NonNull byte[] password, @NonNull byte[] salt) throws Argon2Exception {
    if (inHash.length <= 0) {
      throw new Argon2Exception("inHash length must be > 0");
    }

    final byte[] hash = this.hash(tCost, mCost, parallelism, password, salt, new byte[inHash.length]);

    int inequalityCount = 0;

    // constant time verification
    for (int i = 0; i < hash.length; i += 1) {
      if (inHash[i] != hash[i]) {
        inequalityCount += 1;
      }
    }

    Arrays.fill(hash, (byte) 0);

    return 0 == inequalityCount;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Argon2 argon2 = (Argon2) o;
    return engine.equals(argon2.engine);
  }

  @Override
  public int hashCode() {
    return Objects.hash(engine);
  }

  @NonNull
  @Override
  public String toString() {
    return String.format(Locale.US,
      "Argon2@%x(type=%s, version=%d)",
      System.identityHashCode(this) & 0xFF,
      Argon2Engine.ARGON2_TYPE_ID == type() ?
        "id" : Argon2Engine.ARGON2_TYPE_I == type() ? "i" : "d",
      version());
  }
}
