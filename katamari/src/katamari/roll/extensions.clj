(ns katamari.roll.extensions
  "The API by which to implement targets and participate in rolling."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.deps.alpha.extensions :refer [coord-type]]))

;;; Manifests

(defmulti parse-manifest
  "Multi-spec. Given a rule, being a list `(manifest & kvs)`,
  dispatch on the manifest to methods returning an `s/keys*` form for the
  remaining kvs. These kvs together with the manifest will define a build rule."
  {:arglists '([rule-expr])}
  first)

(defmacro defmanifest
  "Helper for defining rule manifests and their handling."
  [manifest-name keys-form]
  `(defmethod parse-target '~manifest-name [~'_]
     (s/and
      (s/cat :roll/manifest (set ['~manifest-name])
             :kvs ~keys-form)
      (s/conformer
       (fn [v#]
         (if-not (= ::s/invalid v#)
           (merge (:kvs v#) (selet-keys v# [:roll/manifest]))
           ::s/invalid))))))

(defn rule-manifest [{:keys [roll/manifest]}]
  manifest)

(defmulti
  ^{:arglists '([config buildgraph manifest])
    :doc "A task used to perform any required preparation for building a roll manifest.

Invoked once per roll for each unqiue manifest type in the buildgraph.

Implementations must return a pair `[config, buildgraph]` which may be updated.

For instance this task could check to see that some required program is present
in the filesystem or on the path."}

  manifest-prep
  (fn [_conf _graph manifest]
    manifest))

(defmethod manifest-prep :default [config buildgraph manifest]
  [config buildgraph])

(defn- dispatch
  "Helper for the common pattern of dispatching on the rule manifest."
  ([config buildgraph target rule]
   (rule-manifest rule))
  ([config buildgraph target rule inputs]
   (rule-manifest rule)))

(defmulti
  ^{:arglists '([config buildgraph target rule])
    :doc "Return a map of keywords to depended targets.

This is used both to enumerate the dependencies of a rule for topological build
planning, and to define the keying of the inputs structure which the rule will
receive when built.

For instance a"}

  rule-inputs
  #'dispatch)

(defmethod rule-deps :default [config buildgraph target rule]
  (throw (ex-info "No `target-deps` implementation for manifest!"
                  {:target target
                   :rule rule
                   :manifest (rule-manifest rule)})))

(defmulti
  ^{:arglists '([config buildgraph target rule])
    :doc "Perform any required preparation for building a target.

Invoked once per rule in the buildgraph, in topological order.

Implementations must return a pair `[config, buildgraph]`, which may be updated.

By default, tasks require no preparation."}

  rule-prep
  #'dispatch)

(defmethod rule-prep :default [config buildgraph target rule]
  [config buildgraph])

(defmulti
  ^{:arglists '([config buildgraph target rule inputs])
    :doc "Apply the rule to its inputs, producing a build product."}
  rule-build
  #'dispatch)
