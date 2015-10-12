(ns witan.app.handler
  (:require [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.backends.token :refer [token-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [clojure.tools.logging :as log]
            [compojure
             [core :refer :all]
             [route :as route]]
            [ring.middleware
             [cors :refer [wrap-cors]]
             [defaults :refer [api-defaults wrap-defaults]]
             [json :refer [wrap-json-body wrap-json-response]]]
            [witan.app.user :as user]
            [witan.app.forecast :as forecast]
            [witan.app.model :as model]
            [witan.app.util :refer [load-extensions!]]
            [schema.core :as s]
            [witan.app.schema :as w]
            [compojure.api.sweet :as sweet]
            [ring.util.http-response :refer :all]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [overtone.at-at :as at])
  (:gen-class))

;; Global storage for store generated tokens.
(defonce tokens (atom {}))

;; The at-at thread pool
(defonce at-at-pool (at/mk-pool))

;; Authenticate Handler
;; Respons to post requests in same url as login and is responsible to
;; identify the incoming credentials and set the appropiate authenticated
;; user into session. `authdata` will be used as source of valid users.

(defn clear-expired-tokens!
  [tokens]
  (->> @tokens
       (filter (fn [[k {:keys [user expires]}]] (t/before? (t/now) expires)))
       (into {})
       (reset! tokens)))

(defn add-token!
  [user-id tokens]
  (let [token (user/random-token)
        ttl (t/days 28)]
    (swap! tokens assoc (keyword token) {:user user-id :expires (t/plus (t/now) ttl)})
    token))

(defn login
  [{:keys [username password] :as body}]
  (if-let [{:keys [id]} (user/user-valid? username password)]
    (let [token (add-token! id tokens)]
      (ok {:token token :id id}))
    (ok {:message "login failed"})))

(defn signup
  [{:keys [username password name] :as body}]
  (log/info "signup" body)
  (if-let [{:keys [id]} (user/add-user! body)]
    (let [token (add-token! id tokens)]
      (created {:token token :id id}))
    (ok {:message "User already present"})))

(defn check-user [identity]
  (if-let [user (user/retrieve-user identity)]
    (ok (select-keys user [:id :username :name]))
    (unauthorized {:error "Unauthorized"})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Routes and Middlewares                           ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; copied from here https://github.com/JarrodCTaylor/authenticated-compojure-api/tree/master/src/authenticated_compojure_api/middleware
(defn token-auth-mw [handler]
  (fn [request]
    (if (authenticated? request)
      (handler request)
      (unauthorized {:error "Unauthorized"}))))

(defn print-mw
  "for debugging purposes"
  [handler]
  (fn [request]
    (println "LOGGING REQUEST")
    (println (pr-str request))
    (handler request)))

(defn debug-resource
  [resource req]
  (let [result (resource req)]
    (println "RESOURCE: " result)
    result))

(sweet/defapi app'
  (sweet/swagger-ui)
  (sweet/swagger-docs
   {:info {:title "Witan API"
           :description "Back-end API for the Witan project"}})
  (sweet/context* "/api" []
                  (sweet/POST* "/login" []
                               :body [login-details w/LoginDetails]
                               :summary "log in"
                               :return w/LoginReturn
                               (login login-details))
                  (sweet/POST* "/reset-password" []
                               :summary "Resets a users password"
                               (not-implemented))
                  (sweet/POST* "/user" []
                               :body [user w/SignUp]
                               :summary "sign up"
                               :return w/LoginReturn
                               (signup user))
                  (sweet/middlewares
                   [token-auth-mw]
                   (sweet/GET* "/" []
                               (ok {:message "hello"}))
                   (sweet/GET* "/me" {:as request}
                               :summary "Get current logged in user"
                               (check-user (:identity request)))
                   (sweet/GET* "/models" []
                               :summary "Get models available to a user"
                               model/models)
                   (sweet/GET* "/models/:id" []
                               :summary "Get model specified by ID"
                               (not-implemented))
                   (sweet/GET* "/forecasts" []
                               :summary "Get forecasts available to a user"
                               forecast/forecasts)
                   (sweet/POST* "/forecasts" []
                                :summary "Create a new forecast"
                                (not-implemented))
                   (sweet/GET* "/forecasts/:id" []
                               :summary "Redirects to /forecasts/:id/<version> where <version> is the latest version"
                               (not-implemented))
                   (sweet/GET* "/forecasts/:id/:version" []
                               :summary "Returns a forecast of the specified id and version"
                               (not-implemented))
                   (sweet/POST* "/forecasts/:id" []
                                :summary "Creates a new version of this forecast with the specified updated"
                                (not-implemented))
                   (sweet/GET* "/forecasts/:id/:version/output/:type" []
                               :summary "Downloads an output of the given type"
                               (not-implemented))
                   (sweet/POST* "/tag" []
                                :summary "Creates a new tag from a forecast id and version"
                                (not-implemented))
                   (sweet/POST* "/share-request/:tag-id" []
                                :summary "Creates a request to update the sharing properties of a tag"
                                (not-implemented))
                   (sweet/POST* "/data/upload" []
                                :summary "Upload endpoint for data items"
                                (not-implemented))
                   (sweet/GET* "/data" []
                               :summary "Expects a query string. Allows searching of data items"
                               (not-implemented))
                   (sweet/GET* "/data/download/:uuid" []
                               :summary "Downloads the data of a given id"
                               (not-implemented))))
  (sweet/ANY* "/*" []
              (not-found {:message "These aren't the droids you're looking for."})))

(defn my-authfn
  [req token]
  (when-let [{:keys [user expires]} (get @tokens (keyword token))]
    (if (t/before? (t/now) expires)
      user
      (do
        (swap! tokens dissoc (keyword token))
        false))))

;; Create an instance of auth backend.

(def auth-backend
  (token-backend {:authfn my-authfn}))

;; load extensions
(load-extensions!)

;; at-at jobs
(let [delay (t/in-millis (t/days 7))]
  (at/every delay #(clear-expired-tokens! tokens) at-at-pool :initial-delay delay))

;; the Ring app definition including the authentication backend
(def app (-> app'
             (wrap-authorization auth-backend)
             (wrap-authentication auth-backend)
             (wrap-cors :access-control-allow-origin [#".*"]
                        :access-control-allow-methods [:get :put :post :delete])))
