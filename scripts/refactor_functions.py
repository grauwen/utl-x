#!/usr/bin/env python3
"""
Script to systematically refactor all functions to use List<UDM> signature
"""

import re
import os

# Functions that need refactoring based on grep results
FUNCTIONS_TO_REFACTOR = {
    "DebugFunctions": [
        "setLogLevel", "setConsoleLogging", "log", "trace", "debug", 
        "info", "warn", "error", "logType", "logSize", "logPretty",
        "startTimer", "endTimer", "assert", "assertEqual"
    ],
    "EncodingFunctions": [
        "md5", "sha256", "sha512", "sha1", "hash", "hmac"
    ],
    "AdvancedCryptoFunctions": [
        "hmacMD5", "hmacSHA1", "hmacSHA256", "hmacSHA384", "hmacSHA512",
        "hmacBase64", "encryptAES", "decryptAES", "encryptAES256", 
        "decryptAES256", "sha224", "sha384", "sha3_256", "sha3_512",
        "generateIV", "generateKey"
    ],
    "XMLCanonicalizationFunctions": [
        "c14n", "c14nWithComments", "excC14n", "excC14nWithComments",
        "c14n11", "c14n11WithComments", "c14nPhysical", 
        "canonicalizeWithAlgorithm", "c14nSubset", "c14nHash",
        "c14nEquals", "c14nFingerprint", "prepareForSignature"
    ],
    "BinaryFunctions": [
        "bitwiseAnd", "bitwiseOr", "bitwiseXor", "bitwiseNot",
        "shiftLeft", "shiftRight", "equals"
    ]
}

def generate_refactor_commands():
    """Generate the refactoring commands for each function"""
    
    for class_name, functions in FUNCTIONS_TO_REFACTOR.items():
        print(f"\n# Refactoring {class_name}")
        print(f"# Functions to refactor: {', '.join(functions)}")
        
        # Generate the sed commands or manual refactor instructions
        for func in functions:
            print(f"# - {func}: Change signature from individual params to List<UDM>")
            
    print("\n# Update Functions.kt registrations:")
    print("# Replace lambda wrappers with direct method references")
    
if __name__ == "__main__":
    generate_refactor_commands()