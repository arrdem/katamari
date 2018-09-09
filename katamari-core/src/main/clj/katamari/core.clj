(ns katamari.core
  "Katamari's entry point.

  WARNING:
    This namespace MUST be kept svelt.
    Everything goes through here.
    Use lazy calls wherever possible."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [detritus.spec :refer [if-conform-let]]
            [katamari.lazy :refer [lazy-call]])
  ;; As we declare this to be the main, we need to gen-class
  (:gen-class))

;; FIXME (arrdem 2018-09-09):
;;   Requires I'll want elsewhere eventually

;; [clojure.tools.deps.alpha :as deps]
;; [hasch.core :refer [uuid]]
;; [clojure.java.io :as io]
;; [clojure.edn :as edn]
;; [clojure.string :as str]
;; [detritus.update :refer [fix]]

(s/def ::main-args
  (s/or :version-command
        (s/cat :flag (s/or :simple #{"-v"}
                           :long #{"--version"}
                           :word #{"version"}))

        :help-command
        (s/cat :flag (s/or :simple #{"-h"}
                           :long #{"--help"}
                           :word #{"help"}))

        :command
        (s/cat :command-name string?
               :unparsed-args (s/+ string?))))

;;;; Helpers so -main is trivial

(declare -main)

(defn -help
  "Helper to -main, used to implement the help behavior"
  [args?]
  (when args?
    (println "Unrecognized args:")
    (prn args?)
    (println "--------------------------------------------------------------------------------"))
  (println
   (str/replace (:doc (meta #'-main))
                #"(?sm)^[\s&&[^\n\r]]{2}" "")))

(defn -version
  "Helper to -main, used to implement the version command"
  []
  (println
   (slurp
    (lazy-call clojure.java.io/resource "katamari/VERSION"))))

;;;; -main itself

(defn -main
  "
  Usage:
    kat [command] [flag ....]

  Top level flags:
    -h, --help - print this message
    -v --version - print katamari build & version information

  Commands:
    prep    [flags] (target ...) - perform prep tasks required by the target(s)
    lint    [flags] (target ...) - perform configured lint actions on the target(s)
    compile [flags] (target ...) - prepare and compile the target(s)
    test    [flags] (target ...) - execute any configured test runners for the target(s)
    pprint  [flags] (target ...) - pretty-print data about available tasks & target(s

    Note that, depending on user and build configuration additional tasks may be available.
    See your build configuration and the output of the pprint tasks for an exhaustive list."
  [& args]
  (if-conform-let [[command {:keys [command-name unparsed-args]}] ::main-args args]
    (case command
      :version-command (-version)
      :help-command (-help nil)
      :command (try (lazy-call katamari.command/-command command-name unparsed-args)
                    :version-command (-version)
                    (catch katamari.ex.CommandNotFoundException e
                      (-help args)
                      (System/exit 1))))
    (do (-help args)
        (System/exit 1))))
