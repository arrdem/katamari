(ns katamari.roll.extensions.clj
  "A definition of `clojure-library`."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [clojure.spec.alpha :as s]
            [katamari.roll.specs :as rs]
            [katamari.roll.extensions :as ext]
            [katamari.roll.extensions.jvm :as rejvm]))

;;;; Clojure library

;; It doesn't really get simpler than this.
;;
;; Unless there's AOT, which this target doesn't support yet.

(ext/defmanifest clojure-library
  (s/keys* :opt-un [::rs/deps
                    ::rs/paths]))

(defmethod ext/rule-prep 'clojure-library [config buildgraph target rule]
  [config buildgraph])

(defmethod ext/rule-inputs 'clojure-library
  [config {:keys [targets] :as buildgraph} target rule]

  ;; We only need buildgraph internal targets to be built for us.
  {:targets (->> (:deps rule)
                 keys
                 (filter #(contains? targets %)))})

#_(defmethod ext/rule-id 'clojure-library [config buildgraph target inputs]
    )

(defmethod ext/rule-build 'clojure-library
  [config buildgraph target rule products inputs]

  (let [{:keys [classpath lib-map]}
        (rejvm/make-classpath config products
                              {:deps (:deps rule)})]

    (merge
     {:type ::product
      :from target
      :mvn/manifest :roll
      ;; FIXME (reid.mckenzie 2018-10-24):
      ;;   Better keyings?
      #_#_:classpath classpath
      #_#_:lib-map lib-map}
     (select-keys rule [:paths :deps]))))
