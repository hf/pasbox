package me.stojan.pasbox.master;

interface IMasterPasswordHashService {
    const int OK = 0;
    const int ERROR_RETRYABLE = 1;
    const int ERROR_FAILURE = 2;

    int measure(int duration, int hashSize, in byte[] salt, in byte[] password, inout int[] params);
    int hash(out byte[] hash, in byte[] salt, in byte[] password, inout int[] params);
}
