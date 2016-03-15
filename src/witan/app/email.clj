(ns witan.app.email
  (:require [amazonica.aws.simpleemail :as ses]
            [clojure.java.io :as io]
            [clostache.parser :as parser]))

(defn send-password-reset!
  [username token]
  (let [url (str "witan-alpha.mastodonc.com/#/password-reset/" token "/" username)
        ctx {:link_to_reset_password url}
        render #(-> (str "email-templates/reset-your-password" %)
                    (io/resource)
                    (slurp)
                    (parser/render ctx))]
    (ses/send-email {:endpoint "eu-west-1"}
                    {:destination {:to-addresses [username]}
                     :source "witan@mastodonc.com"
                     :message {:subject "Reset your password for Witan"
                               :body {:html (render ".html")
                                      :text (render ".txt")}}})))
