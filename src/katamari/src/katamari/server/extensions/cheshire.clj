(ns katamari.server.extensions.cheshire
  "Cheshire extensions / injections required for Katamari's server."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [cheshire.generate :refer [add-encoder encode-str remove-encoder]]))

;; Monkeypatching the Cheshire JSON serializer

(add-encoder clojure.lang.Namespace
             (fn [ns jsonGenerator]
               (.writeString jsonGenerator (pr-str ns))))

