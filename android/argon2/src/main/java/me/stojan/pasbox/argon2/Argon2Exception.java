package me.stojan.pasbox.argon2;

import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * An exception concerning the Argon2 execution.
 */
public final class Argon2Exception extends RuntimeException {
  // Used from native code.

  private int code = Integer.MAX_VALUE;

  public Argon2Exception(@NonNull String message) {
    super(message);
  }

  public Argon2Exception(int code, @NonNull String message) {
    super("(" + code + ") " + message);

    this.code = code;
  }

  public Argon2Exception(@NonNull String message, Throwable cause) {
    super(message, cause);
  }

  public Argon2Exception(Throwable cause) {
    super(cause);
  }

  @RequiresApi(api = Build.VERSION_CODES.N)
  public Argon2Exception(@NonNull String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

  /**
   * The Argon2 engine code. Negative are the codes from the Argon2 implementation, positive are from the bindings.
   *
   * @return the code if set
   */
  public int code() {
    return this.code;
  }
}
