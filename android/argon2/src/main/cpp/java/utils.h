//
// Created by stojan on 4/21/19.
//

#ifndef ANDROID_UTILS_H
#define ANDROID_UTILS_H

#include <jni.h>
#include <argon2.h>
#include <string.h>

#if defined(__clang__)
#if __has_attribute(optnone)
#define NOT_OPTIMIZED __attribute__((optnone))
#endif
#elif defined(__GNUC__)
#define GCC_VERSION                                                            \
    (__GNUC__ * 10000 + __GNUC_MINOR__ * 100 + __GNUC_PATCHLEVEL__)
#if GCC_VERSION >= 40400
#define NOT_OPTIMIZED __attribute__((optimize("O0")))
#endif
#endif
#ifndef NOT_OPTIMIZED
#define NOT_OPTIMIZED
#endif

#define JNI_ARGON2_THROW_MSG(env, msg) { \
  if (NULL != msg) { \
    ((env)->ThrowNew((env)->FindClass("me/stojan/pasbox/argon2/Argon2Exception"), (msg))); \
  } \
}

#define JNI_ARGON2_THROW(env, code) { \
  if (ARGON2_OK != code) { \
    JNI_ARGON2_THROW_MSG(env, argon2_error_message(code)); \
  } \
}

#ifdef __cplusplus
extern "C" {
#endif
void NOT_OPTIMIZED jni_secure_wipe(jbyte *, jsize);
#ifdef __cplusplus
}
#endif

#endif //ANDROID_UTILS_H
