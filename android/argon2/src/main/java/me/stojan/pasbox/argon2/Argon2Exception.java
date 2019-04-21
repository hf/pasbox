package me.stojan.pasbox.argon2;

import android.os.Build;
import androidx.annotation.RequiresApi;

/**
 * An exception concerning the Argon2 execution.
 */
public final class Argon2Exception extends RuntimeException {
  // Used from native code.

  public Argon2Exception(String message) {
    super(message);
  }

  public Argon2Exception(String message, Throwable cause) {
    super(message, cause);
  }

  public Argon2Exception(Throwable cause) {
    super(cause);
  }

  @RequiresApi(api = Build.VERSION_CODES.N)
  public Argon2Exception(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
