package me.stojan.pasbox.argon2;

final class NativeArgon2 implements Argon2Engine {
  private final int type;
  private final int version;

  NativeArgon2(int type, int version) {
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

  @Override
  public byte[] hash(int tCost, int mCost, int parallelism, byte[] password, byte[] salt, byte[] out) {
    nhash(type, version, tCost, mCost, parallelism, password, salt, out);
    return out;
  }
}
