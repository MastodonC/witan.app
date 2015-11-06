(ns witan.application)

;; holder for a single instance of the application.
;; We create this here to allow reference from a cider(emacs) repl or a repl
;; started from a main method.
;;
;; See https://github.com/stuartsierra/component
(def system)
