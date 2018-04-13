(ns katamari.core
  "Katamari core API as exposed to users."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.spec.alpha :as spec]
            [clojure.tools.deps.alpha :as deps]
            [hasch.core :refer [uuid]])
  (:import java.io.PushbackReader))

(defn default-build
  "Returns \"the\" default build configuration.

  Provides the documented default profiles."
  []
  {:type :katamari/build
   :profiles {}
   :targets {}})

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

(defn mvn-dep
  "Enters a Maven jar dependency into the build.

  FIXME: describe options. needs a spec."
  ([build coordinate]
   (mvn-dep build coordinate {}))
  ([build [name version & more :as coordinate] options]
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
  (assoc-in build [:targets name]
            {:type :clojure-tests
             :options options}))

(defn ^:dynamic roll!
  "Finalizes a build descriptor, returning it to Katamari for execution."
  [build] build)
