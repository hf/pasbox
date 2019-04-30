package me.stojan.pasbox.dev

import com.google.protobuf.MessageLite
import javax.crypto.Cipher

inline fun MessageLite.encipher(cipher: Cipher) =
  FixedSizeByteArrayOutputStream(cipher.getOutputSize(serializedSize))
    .let { byteOut ->
      CleaningCipherOutputStream(byteOut, cipher)
        .let { cipherOut ->
          writeTo(cipherOut)
          cipherOut.flush()
          cipherOut.close()
        }

      byteOut.toByteString()
    }

