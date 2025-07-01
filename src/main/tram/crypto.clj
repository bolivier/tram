(ns tram.crypto
  "Convenience namespace for hashing/crypto functions.

  None of these are implemented from scratch, they all reference functions from
  Buddy (individual libs): https://github.com/funcool/buddy."
  (:require [buddy.hashers :as hashers]))

(defn hash-password
  "Dead simple password hasher.

  Calls out to `buddy.hashers/derive`. Uses their recommended algorithm
  `:bcrypt+blake2b-512`."
  [password]
  (hashers/derive password))

(defn verify-password
  "Like `tram.crypto/hash-password`, this also calls out to Buddy.
  `buddy.hashers/verify`.

  Uses the same algorithm."
  [password hashed-value]
  (hashers/verify password hashed-value))
