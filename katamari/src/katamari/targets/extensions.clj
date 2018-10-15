(ns katamari.targets.extensions
  "The API by which to implement targets"
  (:require [clojure.spec.alpha :as s]))

(defmulti parse-target first)

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
