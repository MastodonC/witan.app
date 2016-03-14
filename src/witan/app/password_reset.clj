(ns witan.app.password-reset
  (:require [witan.app.util :refer [password-reset-token]]
            [witan.app.email :refer [send-password-reset!]]
            [witan.app.config :refer [exec]]
            [witan.app.user :as usr]
            [qbits.hayt :as hayt]
            [clojure.tools.logging :as log]
            [clojure.core.async :as async]))

(defn begin-reset-password!
  "Starts the password reset process by issuing a token and storing"
  [username]
  (let [user-str (str \[ username \])]
    (if (not-empty (usr/retrieve-user-by-username username))
      (let [token (password-reset-token)]
        (log/debug "Generating a password reset email for" user-str)
        (async/go
          (try
            (if-let [{:keys [message]} (send-password-reset! username token)]
              (do
                (exec (hayt/insert :password_reset_tokens
                                   (hayt/values :username username
                                                :password_reset_token token)
                                   (hayt/using :ttl 86400))) ;; 86400 == one day
                (log/info "Reset password email was sent to" user-str))
              (throw (Exception. "send-password-reset! failed to send")))
            (catch Exception e (log/error "An error occurred when trying to send email:" (.getMessage e))))))
      (log/warn "A password reset attempt was made for" user-str "but this user doesn't exist."))))

(defn complete-reset-password!
  "Completes the password reset process by checking token, processing password and deleting"
  [{:keys [username password password-reset-token]}]
  (let [username (clojure.string/lower-case username)]
    (if (not-empty (exec (hayt/select :password_reset_tokens
                                      (hayt/where {:username username :password_reset_token password-reset-token}))))
      (do
        (usr/change-password! username password)
        (exec (hayt/delete :password_reset_tokens (hayt/where {:username username :password_reset_token password-reset-token})))
        (log/info "Password reset completed for user" (str \[ username \])))
      (log/warn "A password reset completion was attempted using the following invalid details:" username password-reset-token))))
