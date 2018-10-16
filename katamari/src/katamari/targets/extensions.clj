(ns katamari.targets.extensions
  "The API by which to implement targets"
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [clojure.spec.alpha :as s]))

(defmulti parse-target
  "Multi-spec. Given a target expression, being a list `(tag & kvs)`,
  dispatch on tag to methods returning an `s/keys*` form for the
  remaining kvs. These kvs together with the tag will constitute a
  target record."
  {:arglists '([target-expr])}
  first)

;; FIXME (arrdem 2018-10-15):
;;   Reread build systems ala carte before going down this road.
;;
;;   Recursive compilation if inputs have changed would work, but I want to nail the representation
;;   of the compile step as an introspectable / data first treeable operation.
#_(defmulti compile
    "Given a config (including buildgraph), a target name and its descriptor, "
    {:arglists '([config])}
    )

(defmacro deftarget
  "Helper for defining parsable target types."
  [target-name keys-form]
  `(defmethod parse-target '~target-name [~'_]
     (s/and
      (s/cat :type (set ['~target-name])
             :kvs ~keys-form)
      (s/conformer
       (fn [v#]
         (if-not (= ::s/invalid v#)
           (assoc (:kvs v#) :type (:type v#)) ::s/invalid))))))
