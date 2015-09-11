(ns witan.app.user
  (:require [buddy.core
             [codecs :as codecs]
             [nonce :as nonce]]
            [buddy.hashers :as hs]
            [qbits.hayt :as hayt]
            [witan.app.config :refer [store-execute config]]))

(defn random-token
  []
  (let [randomdata (nonce/random-bytes 16)]
    (codecs/bytes->hex randomdata)))

(defn find-user [username]
  (hayt/select :Users (hayt/where {:username username})))

(defn create-user [user password]
  (let [hash (hs/encrypt password)]
    (hayt/insert :Users, (hayt/values :username user :password_hash hash))))

(defn add-user! [username password]
  (let [exec (store-execute config)
        existing-users (exec (find-user username))]
    (when (empty? existing-users)
      (exec (create-user username password)))))

(defn password-ok? [existing-user password]
  (hs/check password (:password_hash existing-user)))

(defn user-valid? [username password]
  (let [exec (store-execute config)
        existing-users (exec (find-user username))]
    (if-not (empty? existing-users)
      (password-ok? (first existing-users) password)
      false)))
