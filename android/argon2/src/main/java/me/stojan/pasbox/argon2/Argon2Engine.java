package me.stojan.pasbox.argon2;

import androidx.annotation.NonNull;

/**
 * An Argon2 engine.
 */
interface Argon2Engine {

  /**
   * Argon2 type D.
   */
  int ARGON2_TYPE_D = 0;

  /**
   * Argon2 type I.
   */
  int ARGON2_TYPE_I = 1;

  /**
   * Argon2 type ID.
   */
  int ARGON2_TYPE_ID = 2;

  /**
   * Argon2 version 1.0.
   */
  int ARGON2_VERSION_10 = 0x10;

  /**
   * Argon2 version 1.3.
   */
  int ARGON2_VERSION_13 = 0x13;

  /**
   * The type of Argon2 function used by this engine.
   *
   * @return the type, one of {@link #ARGON2_TYPE_D}, {@link #ARGON2_TYPE_I}, {@link #ARGON2_TYPE_ID}
   */
  int type();

  /**
   * The Argon2 version used by this engine.
   *
   * @return the version, one of {@link #ARGON2_VERSION_10}, {@link #ARGON2_VERSION_13}
   */
  int version();

  @NonNull
  byte[] hash(int tCost, int mCost, int parallelism, @NonNull byte[] password, @NonNull byte[] salt, @NonNull byte[] out) throws Argon2Exception;

}
