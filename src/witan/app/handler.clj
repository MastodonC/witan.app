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
            [schema.core :as s]
            [witan.schema :as w]
            [compojure.api.sweet :as sweet]
            [ring.util.http-response :refer :all])
  (:gen-class))
;; TODO move to witan.schema
(def User
  (merge w/LoginDetails
         {(s/required-key :name) s/Str}))

(def LoginReturn
  (s/either {(s/required-key :token) s/Str
             (s/required-key :id) s/Uuid}
            {:message s/Str}))

;; Global storage for store generated tokens.
(def tokens (atom {}))

;; Authenticate Handler
;; Respons to post requests in same url as login and is responsible to
;; identify the incoming credentials and set the appropiate authenticated
;; user into session. `authdata` will be used as source of valid users.

(defn login
  [{:keys [username password] :as body}]
  (if-let [valid-user (user/user-valid? username password)]
    (let [token (user/random-token)]
      (swap! tokens assoc (keyword token) (:id valid-user))
      (ok {:token token :id (:id valid-user)}))
    (ok {:message "login failed"})))

(defn signup
  [{:keys [username password name] :as body}]
  (if-let [new-user (user/add-user! body)]
    (let [token (user/random-token)]
      (swap! tokens assoc (keyword token) (keyword (:id new-user)))
      (created {:token token :id (:id new-user)}))
    (ok {:message "User already present"})))

(defn check-user [identity]
  (if-let [user (user/retrieve-user identity)]
    (ok (select-keys user [:id :username]))
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

(defn cors-mw [handler]
  (fn [request]
    (let [response (handler request)]
      (-> response
          (assoc-in [:headers "Access-Control-Allow-Origin"] "*")
          (assoc-in [:headers "Access-Control-Allow-Methods"] "GET, PUT, PATCH, POST, DELETE, OPTIONS")
          (assoc-in [:headers "Access-Control-Allow-Headers"] "Authorization, Content-Type")))))

(defn print-mw
  "for debugging purposes"
  [handler]
  (fn [request]
    (println "LOGGING REQUEST")
    (println (pr-str request))
    (handler request)))

(sweet/defapi app'
  (sweet/swagger-ui)
  (sweet/swagger-docs
   {:info {:title "Witan API"
           :description "Back-end API for the Witan project"}
    })
  (sweet/context* "/api" []
            (sweet/POST* "/login" []
                         :body [login-details w/LoginDetails]
                         :summary "log in"
                         :middlewares [cors-mw]
                         :return LoginReturn
                         (login login-details))
            (sweet/POST* "/reset-password" []
                         :summary "Resets a users password"
                         (not-implemented))
            (sweet/GET* "/" []
                        :middlewares [cors-mw token-auth-mw]
                        (ok {:message "hello"}))
            (sweet/POST* "/user" []
                           :body [user User]
                           :middlewares [cors-mw]
                           :summary "sign up"
                           (signup user))
            (sweet/GET* "/me" {:as request}
                        :middlewares [cors-mw token-auth-mw]
                        :summary "Get current logged in user"
                         (check-user (:identity request)))
            (sweet/GET* "/models" []
                        :summary "Get models available to a user"
                        (not-implemented))
            (sweet/GET* "/models/:id" []
                        :summary "Get model specified by ID"
                        (not-implemented))
            (sweet/GET* "/forecasts" []
                        :summary "Get forecasts available to a user"
                        (not-implemented))
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
                        (not-implemented)))
  (sweet/ANY* "/*" []
              (not-found {:message "These aren't the droids you're looking for."})))

(defn my-authfn
  [req token]
  (when-let [user (get @tokens (keyword token))]
    user))

;; Create an instance of auth backend.

(def auth-backend
  (token-backend {:authfn my-authfn}))

; the Ring app definition including the authentication backend
(def app (-> app'
             (wrap-authorization auth-backend)
             (wrap-authentication auth-backend)
             (wrap-cors :access-control-allow-origin [#".*"]
                        :access-control-allow-methods [:get :put :post :delete])))
