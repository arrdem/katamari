(ns roll.extensions.clj
  "A definition of `clojure-library`."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [clojure.spec.alpha :as s]
            [roll.specs :as rs]
            [roll.extensions :as ext]))

;;;; Clojure library

;; It doesn't really get simpler than this.
;;
;; Unless there's AOT, which this target doesn't support yet.

(ext/defmanifest clojure-library
  (s/keys* :opt-un [::rs/deps
                    ::rs/paths]))

(defmethod ext/rule-inputs 'clojure-library
  [config {:keys [targets] :as buildgraph} target rule]

  ;; We only need buildgraph internal targets to be built for us.
  {:targets (->> (:deps rule)
                 keys
                 (filter #(contains? targets %)))})

(defmethod ext/rule-build 'clojure-library
  [config buildgraph target rule products inputs]
  (merge
   {:type ::product
    :from target
    :mvn/manifest :roll}
   (select-keys rule [:paths :deps])))
