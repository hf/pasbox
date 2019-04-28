package me.stojan.pasbox.argon2;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.Objects;

final class NativeArgon2 implements Argon2Engine {
  private final int type;
  private final int version;

  NativeArgon2(int type, int version) {
    switch (type) {
      case ARGON2_TYPE_D:
      case ARGON2_TYPE_I:
      case ARGON2_TYPE_ID:
        break;

      default:
        throw new Argon2Exception("Unknown Argon2 type " + type);
    }

    switch (version) {
      case ARGON2_VERSION_10:
      case ARGON2_VERSION_13:
        break;

      default:
        throw new Argon2Exception("Unknown Argon2 version " + version);
    }

    this.type = type;
    this.version = version;
  }

  static native void nhash(int type, int version, int tCost, int mCost, int parallelism, byte[] password, byte[] salt, byte[] out) throws Argon2Exception;

  @Override
  public int type() {
    return type;
  }

  @Override
  public int version() {
    return version;
  }

  @NonNull
  @Override
  public byte[] hash(int tCost, int mCost, int parallelism, @NonNull byte[] password, @NonNull byte[] salt, @NonNull byte[] out) {
    nhash(type, version, tCost, mCost, parallelism, password, salt, out);
    return out;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NativeArgon2 that = (NativeArgon2) o;
    return type == that.type &&
      version == that.version;
  }

  @Override
  public int hashCode() {
    return getClass().hashCode() ^ Objects.hash(type, version);
  }

  @NonNull
  @Override
  public String toString() {
    return String.format(Locale.US, "NativeArgon2@%x(type=%d, version=%d)", System.identityHashCode(this) & 0xFF, type, version);
  }
}
