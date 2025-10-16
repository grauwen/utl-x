stdlib/
├── src/main/kotlin/org/apache/utlx/stdlib/
│   ├── json/
│   │   └── JSONCanonicalization.kt        ✅ JCS (RFC 8785)
│   │
│   ├── jws/
│   │   └── JWSBasicFunctions.kt           ✅ JWS decode (RFC 7515)
│   │
│   └── jwt/
│       └── JWTFunctions.kt                ✅ JWT decode (RFC 7519)
│
stdlib-security/                           ⚠️ OPTIONAL MODULE
└── src/main/kotlin/org/apache/utlx/stdlib/
    ├── jws/
    │   ├── JWSSignature.kt                ⚠️ Sign & verify
    │   ├── JWSAlgorithms.kt               ⚠️ Algorithm implementations
    │   └── JWKSProvider.kt                ⚠️ JWKS support
    │
    └── jwt/
        └── JWTVerification.kt             ⚠️ JWT verification
