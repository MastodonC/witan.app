(ns witan.app.mq
  (:require [witan.application     :as app]
            [clojure.tools.logging :as log]
            [clojure.data.json     :as json]
            [schema.core           :as s]
            [witan.app.schema      :as ws]))

(defprotocol MQSendMessage
  (send-message! [this topic message]))

(def topics
  {:test    "test"
   :command "command"})

(def commands
  {:user-invited (merge ws/Username ws/InviteToken)
   :user-created (merge ws/Username ws/User)})

(defn send-msg!
  [topic message]
  (if-let [mq (:mq app/system)]
    (if-let [topic-str (get topics topic)]
      (let [msg (if (map? message) (json/write-str message) message)]
        (send-message! mq topic-str msg))
      (log/error "Couldn't send message -" topic "is not a valid topic."))
    (log/error "No message queue available. Topic:" topic)))

(defn send-command!
  [command args]
  (if-let [schema (get commands command)]
    (if-let [error (s/check schema args)]
      (log/error "Couldn't send command" command "- failed to match the command schema:" error)
      (send-msg! :command {:command command
                           :args    args
                           :time    (java.util.Date.)}))
    (log/error "Couldn't send command -" command "is not a valid command.")))
