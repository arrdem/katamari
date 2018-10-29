(ns katamari.server.extensions.cheshire
  "Cheshire extensions / injections required for Katamari's server."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [cheshire.generate :refer [add-encoder encode-str remove-encoder]]))

;; Monkeypatching the Cheshire JSON serializer

(add-encoder clojure.lang.Namespace
             (fn [ns jsonGenerator]
               (.writeString jsonGenerator (format "#namespace %s" (ns-name ns)))))

(add-encoder java.io.File
             (fn [^java.io.File f jsonGenerator]
               (.writeString jsonGenerator (format "#file %s" (.getAbsolutePath f)))))
