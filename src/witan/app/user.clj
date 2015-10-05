(ns witan.app.user
  (:require [buddy.core
             [codecs :as codecs]
             [nonce :as nonce]]
            [buddy.hashers :as hs]
            [qbits.hayt :as hayt]
            [qbits.alia.uuid :as uuid]
            [witan.app.config :refer [exec]]))

(defn random-token
  []
  (let [randomdata (nonce/random-bytes 16)]
    (codecs/bytes->hex randomdata)))

(defn find-user-by-username [username]
  (hayt/select :Users (hayt/where {:username username})))

(defn find-user [id]
  (hayt/select :Users (hayt/where {:id id})))

(defn create-user [user]
  (let [hash (hs/encrypt (:password user))]
    (hayt/insert :Users, (hayt/values :id (uuid/random) :username (:username user) :password_hash hash :name (:name user)))))

(defn add-user! [{:keys [username] :as user}]
  (let [existing-users ((exec) (find-user-by-username username))]
    (when (empty? existing-users)
      ((exec) (create-user user)))))

(defn password-ok? [existing-user password]
  (hs/check password (:password_hash existing-user)))

(defn user-valid? [username password]
  (let [existing-users ((exec) (find-user-by-username username))]
    (if (and (not (empty? existing-users)) (password-ok? (first existing-users) password))
      (first existing-users)
      false)))

(defn retrieve-user [id]
  ((exec) (find-user id)))
