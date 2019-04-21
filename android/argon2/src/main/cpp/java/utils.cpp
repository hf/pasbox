#include "utils.h"

void NOT_OPTIMIZED jni_secure_wipe(jbyte *array, jsize n) {
#if defined memset_s
  memset_s(v, n, 0, n);
#else
  static void *(*const volatile memset_sec)(void *, int, size_t) = &memset;
  memset_sec(array, 0, (size_t) n);
#endif
}

