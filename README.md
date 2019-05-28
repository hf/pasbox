Pasbox
======

**This is a work in progress and no official app has been published yet.**

Pasbox is a simple, highly secure password manager centered around your mobile
device. 

## Features

1. Works only with attested devices.
2. All encryption keys are secured on/with the device's Trusted Execution
   Environment.
3. Symmetric encryption with AES-256-GCM.
4. Asymmetric encryption with Curve25519 ECDH + HKDF-256 + AES-256-GCM.
5. Master password derived from Argon2 with specific time and memory
   requirements. Hashing done on-device.
6. Master password encrypts a large HKDF key (and some NIST EC pairs). All
   subsequent keys are derived from this key. 
7. Second-factor recovery: print recovery keys and store in a safe. Keys are
   managed by the device only.
8. Anonymous recieve-only email addresses.
9. Open-source under MIT License forever, including all infrastructure-as-code.
10. Plan on sustainability to prevent abandonware.

## License

All resources in this distribution are Copyright &copy; Stojan Dimitrovski
2019, and licensed under the MIT license.

All graphics, including logos and icons are licensed under the [CreativeCommons 
Attribution-NonCommercial-NoDerivatives 4.0 International
License](https://creativecommons.org/licenses/by-nc-nd/4.0/).

You may not use the name Pasbox and the application(s) visual identity including
colors, logos, icons, fonts or otherwise in derivative works.

