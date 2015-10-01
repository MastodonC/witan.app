(ns witan.app.user
  (:require [buddy.core
             [codecs :as codecs]
             [nonce :as nonce]]
            [buddy.hashers :as hs]
            [qbits.hayt :as hayt]
            [qbits.alia.uuid :as uuid]
            [witan.app.config :refer [store-execute config]]))

(defn random-token
  []
  (let [randomdata (nonce/random-bytes 16)]
    (codecs/bytes->hex randomdata)))

(defn find-user-by-username [username]
  (hayt/select :Users (hayt/where {:username username})))

(defn create-user [user password name]
  (let [hash (hs/encrypt password)]
    (hayt/insert :Users, (hayt/values :id (uuid/random) :username user :password_hash hash :name name))))

(defn add-user! [username password]
  (let [exec (store-execute config)
        existing-users (exec (find-user-by-username username))]
    (when (empty? existing-users)
      (exec (create-user username password)))))

(defn password-ok? [existing-user password]
  (hs/check password (:password_hash existing-user)))

(defn user-valid? [username password]
  (let [exec (store-execute config)
        existing-users (exec (find-user-by-username username))]
    (if-not (empty? existing-users)
      (password-ok? (first existing-users) password)
      false)))
