(ns katamari.core
  "Katamari core API as exposed to users.

  Provides functions for \"building up\" builds from targets."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.spec.alpha :as spec]
            [clojure.tools.deps.alpha :as deps]
            [hasch.core :refer [uuid]])
  (:import java.io.PushbackReader))

(def ^:private EMPTY-BUILD
  {:type :katamari/build
   :profiles
   {:katamari/default
    [:base :system :user :provided :dev]

    :base
    {;; Options used by all Maven targets
     :maven
     {:repo "~/.m2"
      :repositories [{:names ["central", "maven-central"]
                      :urls ["https://repo1.maven.org/maven2"]
                      :snapshots false}
                     {:names ["clojars"]
                      :urls ["https://repo.clojars.org"]}]}}
    :targets {}}})

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

(defn mvn-dep
  "Enters a Maven jar dependency into the build.

  FIXME: describe options. needs a spec."
  ([build coordinate]
   (mvn-dep build coordinate {}))
  ([build [name version & more :as coordinate] options]
   (require 'katamari.core.mvn)
   (assoc-in build [:targets name]
             {:type :maven-dep
              :coordinate coordinate
              :options options})))

(defn mvn-artifact
  "Enters a jar with Maven coordinates into the build.

  Targets which depend on this artifact directly or transitively will
  see this artifact included on classpaths when running Java tasks, or
  tasks which use the Java stack.

  FIXME: describe options. needs a spec."
  [build name options]
  (require 'katamari.core.mvn)
  (assoc-in build [:targets name]
            {:type :maven-artifact
             :options options}))

(defn clojure-library
  "Enters a Clojure library into the build.

  Targets which depend on this artifact directly or transitively will
  see this artifact included on classpaths when running Java tasks, or
  tasks which use the Java stack.

  FIXME: describe options. needs a spec."
  [build name options]
  (require 'katamari.core.clojure)
  (assoc-in build [:targets name]
            {:type :clojure-library
             :options options}))

(defn clojure-tests
  "Enters a Clojure test suite into the build.

  A Clojure test suite is just like a Clojure library, except that
  test running tasks will recognize it and the tests can be
  automatically run when the tests or any of their dependencies
  change.

  FIXME: describe options. needs a spec."
  [build name options]
  (require 'katamari.core.clojure)
  (assoc-in build [:targets name]
            {:type :clojure-tests
             :options options}))

(defn uberjar
  "Enters a \"jar\" into the build.

  Jars are produced incrementally, and contain only their direct
  dependencies.  For instance, a jar which depended on several Clojure
  targets would contain the

  FIXME: describe options. needs a spec."
  [build name options]
  (require 'katamari.core.uberjar)
  (assoc-in build [:targets name]
            {:type :uberjar
             :options options}))

(defn uberjar
  "Enters an \"uberjar\" into the build.

  Unlike normal jars, uberjars include all their transitive
  dependencies and can be executed stand-alone.

  FIXME: describe options. needs a spec."
  [build name options]
  (require 'katamari.core.uberjar)
  (assoc-in build [:targets name]
            {:type :uberjar
             :options options}))

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
