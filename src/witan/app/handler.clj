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
             [json :refer [wrap-json-body wrap-json-response]]
             [multipart-params :refer [wrap-multipart-params]]]
            [witan.app.user :as u]
            [witan.app.forecast :as forecast]
            [witan.app.model :as model]
            [witan.app.util :refer [load-extensions!]]
            [witan.app.s3 :as s3]
            [witan.app.data :as data]
            [witan.app.password-reset :as pwd]
            [schema.core :as s]
            [witan.app.schema :as w]
            [compojure.api.sweet :as sweet]
            [compojure.api.upload :as upload]
            [ring.util.http-response :refer :all]
            [clj-time.core :as t]
            [overtone.at-at :as at]))

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
  (let [token (u/random-token)
        ttl (t/days 28)
        existing (some #(if (= user-id (-> % second :user)) %) @tokens)]
    (when existing ;; remove an existing token for this user
      (swap! tokens dissoc (first existing)))
    (swap! tokens assoc (keyword token) {:user user-id :expires (t/plus (t/now) ttl)})
    token))

(defn login
  [{:keys [username password] :as body}]
  (if-let [{:keys [id]} (u/user-valid? username password)]
    (let [token (add-token! id tokens)]
      (ok {:token token :id id}))
    (ok {:message "login failed"})))

(defn signup
  [{:keys [username password name invite-token] :as body}]
  (log/info "signup" body)
  (if (u/invited? username invite-token)
    (if-let [{:keys [id]} (u/add-user! body)]
      (let [token (add-token! id tokens)]
        (created {:token token :id id}))
      (ok {:message "User already present"}))
    (ok {:message "Please check your invite token - Witan only accepts invited users"})))

(defn check-user
  [identity]
  (if-let [user (u/retrieve-user identity)]
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
  (sweet/context* "/monitoring" []
                  (sweet/GET* "/_elb_status" []
                              :no-doc true
                              (ok {:status "ALL GOOD"}))
                  (sweet/POST* "/_elb_status" []
                               :no-doc true
                               (ok {:status "ALL GOOD"})))
  (sweet/context* "/api" []
                  (sweet/POST* "/login" []
                               :body [login-details w/LoginDetails]
                               :summary "log in"
                               :return w/LoginReturn
                               (login login-details))
                  (sweet/POST* "/request-password-reset" []
                               :body [payload w/Username]
                               :summary "Starts the process for resetting a users password"
                               (pwd/begin-reset-password! (clojure.string/lower-case (:username payload)))
                               {:status 200});; ALWAYS return a 200 for password resets, to deny attack vector on validating emails
                  (sweet/POST* "/complete-password-reset" []
                               :body [payload w/PasswordReset]
                               :summary "Completes the process for resetting a users password"
                               (pwd/complete-reset-password! payload))
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
                               :path-params [id :- java.util.UUID]
                               (model/model {:id id}))
                   (sweet/GET* "/forecasts" []
                               :summary "Get forecasts available to a user"
                               forecast/forecasts)
                   (sweet/POST* "/forecasts" []
                                :summary "Create a new forecast"
                                forecast/forecasts)
                   (sweet/GET* "/forecasts/:id" []
                               :path-params [id :- java.util.UUID]
                               :summary "retrieves the versions of a forecast"
                               (forecast/forecast {:id id}))
                   (sweet/GET* "/forecasts/:id/latest" []
                               :path-params [id :- java.util.UUID]
                               :summary "Returns a forecast of the specified id and latest version"
                               (forecast/forecast {:id id :latest-version? true}))
                   (sweet/GET* "/forecasts/:id/:version" []
                               :path-params [id :- java.util.UUID
                                             version :- java.lang.Long]
                               :summary "Returns a forecast of the specified id and version"
                               (forecast/forecast {:id id :version version}))
                   (sweet/POST* "/data" {:as request}
                                :multipart-params [file :- upload/TempFileUpload
                                                   filename :- String
                                                   name :- String
                                                   category :- String
                                                   public? :- Boolean]
                                :middlewares [wrap-multipart-params]
                                :summary "Upload,validate and save a data file"
                                (data/data {:category category
                                            :name name
                                            :file file
                                            :public public?
                                            :user-id (:identity request)}))
                   (sweet/POST* "/forecasts/:id/versions" {:as request}
                                :path-params [id :- java.util.UUID]
                                :summary "Creates a new version of this forecast with the specified updates and run it"
                                (forecast/version {:id id :user-id (:identity request)}))
                   (sweet/POST* "/tag" []
                                :summary "Creates a new tag from a forecast id and version"
                                (not-implemented))
                   (sweet/POST* "/share-request/:tag-id" []
                                :summary "Creates a request to update the sharing properties of a tag"
                                (not-implemented))
                   (sweet/GET* "/data/:category" []
                               :summary "get available data inputs by category"
                               :path-params [category :- String]
                               :query-params [{groups :- [String] []}]
                               (data/search {:category category :groups (set groups)}))
                   (sweet/GET* "/data/public/:filename" []
                               :summary "Downloads the data of a given filename from the public folder"
                               :path-params [filename :- String]
                               :query-params [{redirect :- Boolean true}]
                               (data/public filename redirect))))

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

(defn wrap-logger [handler]
  (fn [{:keys [uri headers] :as request}]
    (let [response (handler request)]
      (log/info {:uri uri
                 :headers headers
                 :status (:status response)})
      response)))

;; the Ring app definition including the authentication backend
(def app (try (-> app'
                  (wrap-authorization auth-backend)
                  (wrap-authentication auth-backend)
                  (wrap-cors :access-control-allow-origin [#".*"]
                             :access-control-allow-methods [:get :put :post :delete])
                  (wrap-logger))
              (catch Exception e (log/error e))))
