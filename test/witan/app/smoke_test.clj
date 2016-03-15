(ns witan.app.smoke-test
  (:require
   [witan.app.db-setup :refer :all]
   [user :refer :all]
   [witan.app.test-data :refer [load-test-data!]]
   [clojure.test :refer :all]
   [clj-http.client :as client]
   [witan.app.config :as c]
   [witan.app.s3 :as s3]
   [witan.app.user :as usr]
   [witan.system :as ws]
   [clojure.tools.logging :as log]
   [com.stuartsierra.component :as component]
   [cheshire.core :as json]))

(def test-config
  {:cassandra-session {:host "localhost"
                       :keyspace "witan_test"
                       :replication 1}
   :s3 {:bucket "witan-dummy-data"}})

(def test-server-port 3002)

(def test-conn (atom nil))

(defn test-data-fixture
  "test everything in isolation - new keyspace, different server, different s3 bucket"
  [f]
  (with-redefs [c/exec (fn [body]
                         (let [conn-fn (or @test-conn (reset! test-conn (c/store-execute test-config)))]
                           (conn-fn body)))
                ws/system (fn []
                            (-> (component/system-map
                                 :jetty-server (ws/->JettyServer witan.app.handler/app test-server-port)
                                 :repl-server (Object.))))
                s3/bucket (-> test-config :s3 :bucket)]
    (load-db-schema! test-config)
    (load-test-data!)
    (try
      (stop)
      (catch Exception e "Server is not running")
      (finally (go)))

    (f)

    (stop)))

(use-fixtures :once test-data-fixture)

(defn app-url
  [path]
  (str "http://localhost:" test-server-port path))

(defn auth-header
  [token]
  {"Authorization" (str "Token " token)})

(deftest smoke-tests
  (testing "POST /api/login"
    (let [_ (usr/change-password! "support+witan@mastodonc.com" "secret123")
          {:keys [status body]} (client/post (app-url "/api/login") {:body (json/generate-string {:username "support+witan@mastodonc.com" :password "secret123"}) :content-type :json})]
      (is (== status 200))))
  (testing "authenticated calls"
    (let [login (client/post (app-url "/api/login") {:body (json/generate-string {:username "support+witan@mastodonc.com" :password "secret123"})  :content-type :json})
          token (get (json/parse-string (:body login)) "token")]
      (testing "login worked"
        (is (not (nil? token))))
      (testing "unauthenticated"
        (let [{:keys [status body]} (client/get (app-url "/api/me") {:throw-exceptions false})]
          (is (== status 401))))
      (testing "GET /api/me"
        (let [{:keys [status body]} (client/get (app-url "/api/me") {:headers (auth-header token)})]
          (is (== status 200))))
      (testing "GET /api/models"
        (let [{:keys [status body]} (client/get (app-url "/api/models") {:headers (auth-header token)})
              model (first (json/parse-string body))]
          (is (== status 200))
          (testing "GET /api/models/:model-id"
            (let [{:keys [status body]} (client/get (app-url (str "/api/models/" (get model "model-id"))) {:headers (auth-header token)})]
              (is (== status 200))))))
      (testing "POST /api/forecasts"
        (let [{:keys [status body]} (client/get (app-url "/api/models") {:headers (auth-header token)})
              parsed-body (json/parse-string body)
              housing-model (some #(when (>= (.indexOf (get % "name") "Housing") 0) %) parsed-body)
              trend-model (some #(when (>= (.indexOf (get % "name") "Trend") 0) %) parsed-body)
              housing-req-body (json/generate-string
                                {:name "Housing Test Forecast"
                                 :model-id (get housing-model "model-id")
                                 :model-properties [{:name "borough" :value "Bexley"}
                                                    {:name "fertility-assumption" :value "Standard Fertility"}
                                                    {:name "variant" :value "DCLG"}]
                                 :public? false})
              housing-resp (client/post (app-url "/api/forecasts")
                                        {:headers (auth-header token)
                                         :content-type :json
                                         :body housing-req-body})
              trend-req-body (json/generate-string
                              {:name "Trend Test Forecast"
                               :model-id (get trend-model "model-id")
                               :model-properties [{:name "borough" :value "Bexley"}
                                                  {:name "fertility-assumption" :value "High Fertility"}]
                               :public? false})
              trend-resp (client/post (app-url "/api/forecasts")
                                      {:headers (auth-header token)
                                       :content-type :json
                                       :body trend-req-body})]
          (is (== (:status housing-resp) 201))
          (is (== (:status trend-resp) 201))))
      (testing "GET /api/forecasts"
        (let [{:keys [status body]} (client/get (app-url "/api/forecasts") {:headers (auth-header token)})
              forecast (first (json/parse-string body))
              forecast-id (get forecast "forecast-id")
              version (get forecast "version")]
          (is (== status 200))
          (is (not (nil? forecast-id)))
          (testing "GET /api/forecasts/:forecast-id"
            (let [{:keys [status body]}  (client/get (app-url (str "/api/forecasts/" forecast-id)) {:headers (auth-header token)})]
              (is (== status 200))))
          (testing "GET /api/forecasts/:id/latest"
            (let [{:keys [status body]}  (client/get (app-url (str "/api/forecasts/" forecast-id "/latest")) {:headers (auth-header token)})]
              (is (== status 200))))
          (testing "GET /api/forecasts/:id/:version"
            (let [{:keys [status body]}  (client/get (app-url (str "/api/forecasts/" forecast-id "/" version)) {:headers (auth-header token)})]
              (is (== status 200))))
          (testing "POST /api/data"
            (let [{:keys [status body]} (client/post (app-url "/api/data")
                                                     {:headers (auth-header token)
                                                      :multipart [{:name "file" :content (clojure.java.io/file "test-data/Template_DevelopmentData_Bexley_WithData.csv") :content-type "text/csv"}
                                                                  {:name "filename":content "development.csv"}
                                                                  {:name "name" :content "development.csv"}
                                                                  {:name "category" :content "development-data"}
                                                                  {:name "public?" :content "true"}]})
                  data-id (get (json/parse-string body) "data-id")]
              (is (== status 201))
              (testing "POST /api/forecasts/:id/versions"
                (let [{:keys [body]} (client/get (app-url "/api/forecasts") {:headers (auth-header token)})
                      parsed-body (json/parse-string body)
                      housing-forecast (some #(when (>= (.indexOf (get % "name") "Housing") 0) %) parsed-body)
                      trend-forecast (some #(when (>= (.indexOf (get % "name") "Trend") 0) %) parsed-body)
                      housing-resp (client/post (app-url (str "/api/forecasts/" (get housing-forecast "forecast-id") "/versions"))
                                                {:headers (auth-header token)
                                                 :body (json/generate-string {:inputs {:development-data {:data-id data-id}}})
                                                 :content-type :json})
                      trend-resp (client/post (app-url (str "/api/forecasts/" (get trend-forecast "forecast-id") "/versions"))
                                              {:headers (auth-header token)
                                               :body (json/generate-string {:inputs {:development-data {:data-id data-id}}})
                                               :content-type :json})]
                  (is (== (:status housing-resp) 201))
                  (is (== (:status trend-resp) 201))
                  ;; checking models haven't errored
                  (loop []
                    (Thread/sleep 4000)
                    (let [{:keys [body]}
                          (client/get (app-url (str "/api/forecasts/" (get housing-forecast "forecast-id"))) {:headers (auth-header token)})
                          forecast (first (json/parse-string body keyword))]
                      (if (:in-progress? forecast)
                        (recur)
                        (is (nil? (:error forecast))))))
                  (loop []
                    (Thread/sleep 4000)
                    (let [{:keys [body]}
                          (client/get (app-url (str "/api/forecasts/" (get trend-forecast "forecast-id"))) {:headers (auth-header token)})
                          forecast (first (json/parse-string body keyword))]
                      (if (:in-progress? forecast)
                        (recur)
                        (is (nil? (:error forecast))))))))))))
      (testing "GET /api/data/:category"
        (let [{:keys [status body]} (client/get (app-url "/api/data/development-data") {:headers (auth-header token)})])))))
