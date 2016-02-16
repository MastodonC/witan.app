(ns witan.app.components.kafka
  (:require [com.stuartsierra.component :as component]
            [witan.app.mq               :refer [MQSendMessage]]
            [clojure.tools.logging      :as log]
            [clj-kafka.producer         :as kafka]
            [clj-kafka.zk               :as zk]
            [clojure.core.async         :as async :refer [go-loop chan <! close! put!]]))

(defrecord KafkaProducer [host port]
  MQSendMessage
  (send-message! [component topic message]
    (if-let [conn (:connection component)]
      (if-let [error (kafka/send-message conn (kafka/message topic (.getBytes message)))]
        (log/error "Failed to send message to Kafka:" error)
        (log/debug "Message was sent to Kafka:" topic message))
      (log/error "There is no connection to Kafka.")))

  component/Lifecycle
  (start [component]
    (log/info "Starting Kafka producer...")
    (log/info "Building broker list from ZooKeeper:" host port)
    (let [broker-string (->>
                         (zk/brokers {"zookeeper.connect" (str host ":" port)})
                         (map (juxt :host :port))
                         (map (partial interpose \:))
                         (map (partial apply str))
                         (interpose \,)
                         (apply str))
          _ (log/debug "Broker list" broker-string)
          connection (kafka/producer {"metadata.broker.list" broker-string
                                      "serializer.class" "kafka.serializer.DefaultEncoder"
                                      "partitioner.class" "kafka.producer.DefaultPartitioner"})]
      (assoc component :connection connection)))

  (stop [component]
    (log/info "Stopping Kafka producer..." (:queue component))
    (assoc component :connection nil)))

(defn new-kafka-producer [host port]
  (map->KafkaProducer {:host host :port port}))
