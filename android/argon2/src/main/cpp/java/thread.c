/*
 * Argon2 reference source code package - reference C implementations
 *
 * Copyright 2015
 * Daniel Dinu, Dmitry Khovratovich, Jean-Philippe Aumasson, and Samuel Neves
 *
 * You may use this work under the terms of a Creative Commons CC0 1.0
 * License/Waiver or the Apache Public License 2.0, at your option. The terms of
 * these licenses can be found at:
 *
 * - CC0 1.0 Universal : http://creativecommons.org/publicdomain/zero/1.0
 * - Apache 2.0        : http://www.apache.org/licenses/LICENSE-2.0
 *
 * You should have received a copy of both of these licenses along with this
 * software. If not, they may be obtained at the above URLs.
 */

#if !defined(ARGON2_NO_THREADS)

#include "thread.h"

int argon2_thread_create(argon2_thread_handle_t *handle,
                         argon2_thread_func_t func, void *args) {
  if (NULL == handle || func == NULL) {
    return -1;
  }

  pthread_attr_t attrs;
  struct sched_param sched_params;

  pthread_attr_init(&attrs);
  pthread_attr_getschedparam(&attrs, &sched_params);

  pthread_attr_setschedpolicy(&attrs, SCHED_NORMAL);
  sched_params.sched_priority = sched_get_priority_min(SCHED_NORMAL);

  pthread_attr_setschedparam(&attrs, &sched_params);

  return pthread_create(handle, &attrs, func, args);
}

int argon2_thread_join(argon2_thread_handle_t handle) {
  return pthread_join(handle, NULL);
}

void argon2_thread_exit(void) {
  pthread_exit(NULL);
}

#endif /* ARGON2_NO_THREADS */
