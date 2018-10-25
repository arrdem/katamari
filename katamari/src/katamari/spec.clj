(ns katamari.spec
  "Helpers for working with spec."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [clojure.spec.alpha :as s]))

;; Adapted from detritus.spec
(defn- spec-in-tag [tag spec]
  (keyword (str (namespace tag) "$" (name tag))
           (name spec)))

(s/def ::field-spec
  (s/cat :field symbol?
         :turnstyle #{:- :?}
         :spec any?))

(s/def ::fields
  (s/and vector?
         (s/* ::field-spec)))

(s/fdef defkeys
  :args (s/cat :tag-name qualified-keyword?
               :fields ::fields))

(defmacro defkeys
  "Somewhat comparable to `#'guten-tag.core/deftag`, except that instead
  of generating a custom tagged value type, it just generates a
  `clojure.spec(.alpha)` keys spec from a deftype like sequence of
  field specs.
  Field specs are sequences field-name (:- | :?) spec, where the :-
  \"turnstyle\" operator signifies a req-un key and :? operator
  signifies an opt-un key.
  This macro exists because the ergonomics of Clojure's keys forms
  optimize for union types and key-reuse, not for records. I (@arrdem)
  a big believer that locally scoped single use specs (types) are
  valuable and should be easy to knock out. This helps with that,
  especially when you just want to alias some existing specs for your
  keys.
  ```clj
  (defkeys ::foo
    [bar :- int?
     baz :? keyword?])
  ;; => ::foo
  (s/valid? ::foo {:bar 1})
  ;; => true
  (s/valid? ::foo {:bar :not-a-num})
  ;; => false
  (s/valid? ::foo {:bar 1 :baz :a-legal-keyword})
  ;; => true
  ```"
  [keys-name fields]
  (let [fields+specs (s/conform ::fields fields)]
    `(do ~@(for [{:keys [field spec]} fields+specs]
             `(s/def ~(spec-in-tag keys-name field) ~spec))
         (s/def ~keys-name
           (s/keys :req-un [~@(keep #(when (= :- (:turnstyle %))
                                       (spec-in-tag keys-name (:field %)))
                                    fields+specs)]
                   :opt-un ~(vec (keep #(when (= :? (:turnstyle %))
                                          (spec-in-tag keys-name (:field %)))
                                       fields+specs)))))))

(defn valid!
  "`#'s/valid?` but throws an `ex-info` with `#'s/explain-data` of the
  failure when `val` doesn't conform to `spec`.
  Intended for always-on postconditions because `#'s/assert` is off by
  default."
  [spec val]
  (if (s/valid? spec val)
    true
    (throw (ex-info (format "Failed to validate value %s as spec %s"
                            (pr-str val) spec)
                    (s/explain-data spec val)))))

(defmacro if-conform-let
  "If-let, but for values conformed with `clojure.spec(.alpha)`.
  Attempts to conform the result of evaluating `expr` to `spec`. If
  the value conforms, binds the conform of `expr` to `binding` and
  executes `then`. Otherwise executes `else` with no bindings."
  {:style/indent 1
   :arglists '([[binding spec expr] then]
               [[binding spec expr] then else])}
  [[binding spec expr] then & [else]]
  `(let [conform# (s/conform ~spec ~expr)]
     (if (not= conform# :clojure.spec.alpha/invalid)
       (let [~binding conform#]
         ~then)
       ~else)))
