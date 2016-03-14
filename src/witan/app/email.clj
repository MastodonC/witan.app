(ns witan.app.email
  (:require [amazonica.aws.simpleemail :as ses]))

(defn send-password-reset!
  [username token]
  (let [url (str "http://witan-alpha.mastodonc.com/#/password-reset/" token)]
    (ses/send-email {:endpoint "eu-west-1"}
                    {:destination {:to-addresses [username]}
                     :source "witan@mastodonc.com"
                     :message {:subject "Reset your password for Witan"
                               :body {:html (str "<a href=\"" url "\">" url "</a>")
                                      :text url}}})))
