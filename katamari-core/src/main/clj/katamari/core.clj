(ns katamari.core
  "Katamari core API as exposed to users.

  Provides functions for \"building up\" builds from targets."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.spec.alpha :as spec]
            [clojure.tools.deps.alpha :as deps]
            [hasch.core :refer [uuid]]
            [potemkin.namespaces :refer [import-vars]])
  (:import java.io.PushbackReader))

(def ^:private EMPTY-BUILD
  {:type :katamari/build
   :profiles
   {:katamari/default
    [:base :system :user :provided]

    ;; FIXME: How are dev, test, repl and soforth defined not magically?

    :base
    {;; Options used by all Maven targets
     :maven
     {:repo "~/.m2"
      :repositories [{:names ["central", "maven-central"]
                      :urls ["https://repo1.maven.org/maven2"]
                      :snapshots false}
                     {:names ["clojars"]
                      :urls ["https://repo.clojars.org"]}]}}}
   :targets
   {}

   :tasks
   {}})

(defn set-default-target
  "Sets the ID of the default target.
  This target is used by `test`, `repl` and other tasks when one is
  not explicitly provided."
  [build target]
  (assoc build :default-target target))

(defn load-profiles [build path]
  (let [f (io/file path)
        profiles (if (.exists f)
                   (edn/read (PushbackReader. (io/reader f)))
                   {})]
    ;; FIXME (arrdem 2018-04-13):
    ;;   Use something more carefully considered than merge

    ;; FIXME (arrdem 2018-04-13):
    ;;   Validate the read profile map

    (update build :profiles merge profiles)))

(defn default-build
  "Returns \"the\" default build configuration.

  Provides the documented default profiles, and loads system settings."
  []
  (-> EMPTY-BUILD
      (load-profiles "/etc/katamari.edn")
      (load-profiles (str (System/getProperty "user.home")
                          "/.katamari/profiles.edn"))))

;; Ah yes
(import-vars
 [katamari.targets.mvn
  mvn-dep mvn-artifact]
 [katamari.targets.jvm
  jar uberjar]
 [katamari.targets.clj
  clojure-library clojure-tests])

(def ^:dynamic *build*
  "Implementation detail of `#'roll!` not intended for public use."
  nil)

(defn ^:dynamic roll!
  "Finalizes a build descriptor, returning it to Katamari for execution."
  [build]
  (if (instance? clojure.lang.Atom *build*)
    (reset! *build* build)
    (binding [*out* *err*]
      (println "katamari.core] Warning: tried to `roll!` without a `*build*` context!")))
  build)
