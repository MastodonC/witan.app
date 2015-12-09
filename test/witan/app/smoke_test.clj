(ns witan.app.smoke-test
  (:require
   [db-setup :refer :all]
   [user :refer :all]
   [witan.app.test-data :refer [load-test-data!]]
   [clojure.test :refer :all]
   [clj-http.client :as client]
   [witan.app.config :as c]
   [witan.app.s3 :as s3]
   [witan.system :as ws]
   [clojure.tools.logging :as log]
   [com.stuartsierra.component :as component]))

(def test-config
  {:cassandra-session {:host "localhost"
                       :keyspace "witan_test"
                       :replication 1}
   :s3 {:bucket "witan-dummy-data"}})

(def test-conn (atom nil))

(defn test-data-fixture
  "test everything in isolation - new keyspace, different server, different s3 bucket"
  [f]
  (with-redefs [c/exec (fn [body]
                         (let [conn-fn (or @test-conn (reset! test-conn (c/store-execute test-config)))]
                           (conn-fn body)))
                ws/system (fn []
                         (-> (component/system-map
                                 :jetty-server (ws/->JettyServer witan.app.handler/app 3002)
                                 :repl-server (Object.))))
                s3/bucket "witan-dummy-data"]
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
    (let [{:keys [status body]} (client/post (app-url "/api/login") {:body "{\"username\": \"support@mastodonc.com\",\"password\":\"secret\"}" :content-type :json})]
      (is (== status 200))))
  (testing "authenticated calls"
    (let [login (client/post (app-url "/api/login") {:body "{\"username\": \"support@mastodonc.com\",\"password\":\"secret\"}" :content-type :json})
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
                                                      :multipart [{:name "file" :content (clojure.java.io/file "test-data/development.csv") :content-type "text/csv"}
                                                                  {:name "filename":content "development.csv"}
                                                                  {:name "name" :content "development.csv"}
                                                                  {:name "category" :content "development-data"}
                                                                  {:name "public?" :content "true"}]})
                  data-id (get (json/parse-string body) "data-id")]
              (is (== status 201))
              (testing "POST /api/forecasts/:id/versions"
                (let [{:keys [status body]} (client/post (app-url (str "/api/forecasts/" forecast-id "/versions"))
                                                         {:headers (auth-header token)
                                                          :body (json/generate-string {:inputs {:development-data {:data-id data-id}}})
                                                          :content-type :json})]))))))
      (testing "GET /api/data/:category"
        (let [{:keys [status body]} (client/get (app-url "/api/data/development-data") {:headers (auth-header token)})]))
)))
