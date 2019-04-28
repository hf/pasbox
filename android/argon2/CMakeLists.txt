cmake_minimum_required(VERSION 3.10.2)

# Derived from argon2's Makefile: https://github.com/P-H-C/phc-winner-argon2/blob/20171227/Makefile

add_library(argon2 SHARED
  src/main/cpp/argon2-20171227/src/ref.c # opt.c is optimized for Intel only, so no need
  src/main/cpp/argon2-20171227/src/argon2.c
  src/main/cpp/argon2-20171227/src/core.c
  src/main/cpp/argon2-20171227/src/encoding.c
  src/main/cpp/argon2-20171227/src/blake2/blake2b.c
  src/main/cpp/java/thread.c
  src/main/cpp/java/utils.cpp
  src/main/cpp/java/me_stojan_pasbox_argon2_NativeArgon2.cpp)

target_include_directories(argon2 PRIVATE
  src/main/cpp/argon2-20171227/include
  src/main/cpp/argon2-20171227/src)

target_compile_options(argon2 PRIVATE
  -O3 -Wall -fPIC -fvisibility=hidden -pthread -DA2_VISCTL=1)