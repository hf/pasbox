#include <cstring>

#include <argon2.h>
#include "me_stojan_pasbox_argon2_NativeArgon2.h"

#include "utils.h"

JNIEXPORT void JNICALL Java_me_stojan_pasbox_argon2_NativeArgon2_nhash(JNIEnv *env,
                                                                       jclass,
                                                                       jint type,
                                                                       jint version,
                                                                       jint tCost,
                                                                       jint mCost,
                                                                       jint parallelism,
                                                                       jbyteArray password,
                                                                       jbyteArray salt,
                                                                       jbyteArray hash) {

  if (env->ExceptionCheck()) {
    return;
  }

  jboolean pwdIsCopy = JNI_FALSE;
  auto pwd = env->GetByteArrayElements(password, &pwdIsCopy);

  if (NULL == pwd) {
    JNI_ARGON2_THROW_MSG(env, "(JNI) Unable to get password bytes");
    return;
  }

  auto pwdLen = env->GetArrayLength(password);

  jboolean sltIsCopy = JNI_FALSE;
  auto slt = env->GetByteArrayElements(salt, &sltIsCopy);

  if (NULL == slt) {
    if (pwdIsCopy) {
      jni_secure_wipe(pwd, pwdLen);
    }
    env->ReleaseByteArrayElements(password, pwd, JNI_ABORT);
    JNI_ARGON2_THROW_MSG(env, "(JNI) Unable to get salt bytes");
    return;
  }

  auto sltLen = env->GetArrayLength(salt);

  jboolean hshIsCopy = JNI_FALSE;
  auto hsh = env->GetByteArrayElements(hash, &hshIsCopy);

  if (NULL == hsh) {
    if (pwdIsCopy) {
      jni_secure_wipe(pwd, pwdLen);
    }
    env->ReleaseByteArrayElements(password, pwd, JNI_ABORT);
    env->ReleaseByteArrayElements(salt, slt, JNI_ABORT);

    JNI_ARGON2_THROW_MSG(env, "(JNI) Unable to get hash bytes");
    return;
  }

  auto hshLen = env->GetArrayLength(hash);

  auto result = argon2_hash(
    static_cast<const uint32_t>(tCost),
    static_cast<const uint32_t>(mCost),
    static_cast<const uint32_t>(parallelism),
    pwd, static_cast<const size_t>(pwdLen),
    slt, static_cast<const size_t>(sltLen),
    hsh, static_cast<const size_t>(hshLen),
    NULL, 0, // encoded
    static_cast<argon2_type>(type),
    static_cast<const uint32_t>(version));

  if (pwdIsCopy) {
    jni_secure_wipe(pwd, pwdLen);
  }

  env->ReleaseByteArrayElements(password, pwd, JNI_ABORT);
  env->ReleaseByteArrayElements(salt, slt, JNI_ABORT);

  if (hshIsCopy) {
    // copy back hash elements elements, but don't release native copy
    env->ReleaseByteArrayElements(hash, hsh, JNI_COMMIT);

    // securely wipe native copy of hash
    jni_secure_wipe(hsh, hshLen);

    // release native copy of hash
    env->ReleaseByteArrayElements(hash, hsh, JNI_ABORT);
  } else {
    // just release the array
    env->ReleaseByteArrayElements(hash, hsh, 0);
  }

  JNI_ARGON2_THROW(env, result);
}
