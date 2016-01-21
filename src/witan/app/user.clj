(ns witan.app.user
  (:require [buddy.core
             [codecs :as codecs]
             [nonce :as nonce]]
            [buddy.hashers :as hs]
            [qbits.hayt :as hayt]
            [qbits.alia.uuid :as uuid]
            [witan.app.config :as c]
            [witan.app.schema :as ws]
            [witan.app.util :as util]
            [schema.core :as s]))

(defn random-token
  []
  (let [randomdata (nonce/random-bytes 16)]
    (codecs/bytes->hex randomdata)))

(defn find-user-by-username [username]
  (hayt/select :Users (hayt/where {:username (clojure.string/lower-case username)})))

(defn find-user [id]
  (hayt/select :Users (hayt/where {:id id})))

(defn find-invite-token [username invite-token]
  (hayt/select :invite_tokens (hayt/where {:username (clojure.string/lower-case username)
                                           :invite_token invite-token})))

(defn invited? [username invite-token]
  (not (nil? (first (c/exec (find-invite-token username invite-token))))))

(defn create-invite-token
  [username invite-token]
  (hayt/insert :invite_tokens
               (hayt/values :username (clojure.string/lower-case username)
                            :invite_token invite-token)))

(defn create-user [{:keys [password username name]}]
  (let [hash (hs/encrypt password)]
    (hayt/insert :Users
                 (hayt/values :id (uuid/random)
                              :username (clojure.string/lower-case username)
                              :password_hash hash
                              :name name))))

(defn update-password [id password]
  (let [hash (hs/encrypt password)]
    (hayt/update :Users
                 (hayt/set-columns {:password_hash hash})
                 (hayt/where {:id id}))))

(defn retrieve-user-by-username [username]
  (first (c/exec (find-user-by-username username))))

(defn change-password! [username password]
  (let [user (retrieve-user-by-username username)]
    (c/exec (update-password (:id user) password))))

(defn add-invite-token!
  [username]
  (let [invite-token (util/user-friendly-token)]
    (c/exec (create-invite-token username invite-token))
    invite-token))

(defn add-user! [raw-user]
  (let [{:keys [username] :as user} (-> raw-user
                                        (update :username clojure.string/trim)
                                        (update :name clojure.string/trim))]
    (s/validate ws/SignUp user)
    (let [existing-users (retrieve-user-by-username username)]
      (when (empty? existing-users)
        (c/exec (create-user user))
        (retrieve-user-by-username username)))))

(defn password-ok? [existing-user password]
  (hs/check password (:password_hash existing-user)))

(defn user-valid? [username password]
  (let [existing-user (retrieve-user-by-username username)]
    (if (and (not (nil? existing-user)) (password-ok? existing-user password))
      existing-user
      false)))

(defn retrieve-user [id]
  (first (c/exec (find-user id))))
