;; Rollfile.clj
;;
;; This file is a normal Clojure program which produces a katamari build configuration.
;; The state it produces is represented in Rollfile.edn

(require '[katamari.core :as kat])

(defn contrib-style-clojure-library [build name options]
  (-> build
      ;; The library itself
      (kat/clojure-library name
        (-> options
            (update-in [:base :source-paths] (fnil into [])
                       [(str name "/src/main/clj")
                        (str name "/src/main/cljc")])
            (update-in [:base :resource-paths] (fnil into [])
                       [(str name "/src/main/resources")])
            (update-in [:dev :source-paths] (fnil into [])
                       [(str name "/src/dev/clj")
                        (str name "/src/dev/cljc")])
            (update-in [:dev :resource-paths] (fnil into [])
                       [(str name "/src/dev/resources")])))
      ;; Automatic test target
      (kat/clojure-tests (symbol (namespace name) (str (clojure.core/name name) "+tests"))
         {:base {:source-paths [(str name "/src/test/clj")
                                (str name "/src/test/cljc")]
                 :resource-paths [(str name "/src/test/resources")]}})))

(-> (kat/default-build)

    ;; Make dependencies available as targets
    (kat/mvn-dep '[org.clojure/clojure "1.9.0"])
    (kat/mvn-dep '[org.clojure/specs.alpha "0.1.143"])
    (kat/mvn-dep '[org.clojure/tools.deps.alpha "0.5.417"])
    (kat/mvn-dep '[org.clojure/test.check "0.9.0"])
    (kat/mvn-dep '[org.clojure/tools.nrepl "0.2.12"])
    (kat/mvn-dep '[io.replikativ/hasch "0.3.4"])

    ;; Clojure libraries
    (contrib-style-clojure-library 'katamari-core
      '{:base {:dependencies [org.clojure/clojure
                              org.clojure/specs.alpha
                              org.clojure/tools.deps.alpha
                              io.replikativ/hasch]}
        :test {:dependencies [org.clojure/test.check]}})

    (contrib-style-clojure-library 'katamari-server
      '{:dependencies [katamari-core
                       org.clojure/tools.nrepl]})

    ;; Roll everything up for easy deployment
    (kat/mvn-artifact 'me.arrdem/katamari
      '{:base {:dependencies [katamari-server]
               :version "0.0.0"}})

    (kat/set-default-target 'me.arrdem/katamari)

    ;; Lets do it!
    (kat/roll!))
