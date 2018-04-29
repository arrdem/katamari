(defproject me.arrdem/katamari "_"
  :description "Roll your software up into artifacts!"
  :url "http://github.com/arrdem/katamari"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :exclusions [org.clojure/clojure]
  :dependencies [[me.arrdem/clojure "1.10.0-SNAPSHOT"]
                 [org.clojure/spec.alpha "0.1.143"]
                 [org.clojure/tools.deps.alpha "0.5.417"] ;; FIXME: am I actually gonna use this?
                 [com.cemerick/pomegranate "1.0.0"]
                 [me.arrdem/detritus "LATEST"]
                 [io.replikativ/hasch "0.3.4"]
                 [potemkin "0.4.5"]]

  :source-paths      ["src/main/clj"
                      "src/main/cljc"]
  :java-source-paths ["src/main/jvm"]
  :resource-paths    ["src/main/resources"]

  :profiles
  {:test
   {:test-paths        ["src/test/clj"
                        "src/test/cljc"]
    :java-source-paths ["src/test/jvm"]
    :resource-paths    ["src/test/resources"]}
   :dev
   {:source-paths      ["src/dev/clj"
                        "src/dev/cljc"]
    :java-source-paths ["src/dev/jvm"]
    :resource-paths    ["src/dev/resources"]
    :doc-paths         ["README.md" "docs"]}}

  :plugins [[me.arrdem/lein-git-version "2.0.5"]
            [me.arrdem/lein-auto "0.1.4"]
            [lein-cljfmt "0.5.7"]]

  :git-version
  {:status-to-version
   (fn [{:keys [tag version branch ahead ahead? dirty?] :as git}]
     (if (and tag (not ahead?) (not dirty?))
       (do (assert (re-find #"\d+\.\d+\.\d+" tag)
                   "Tag is assumed to be a raw SemVer version")
           tag)
       (if (and tag (or ahead? dirty?))
         (let [[_ prefix patch] (re-find #"(\d+\.\d+)\.(\d+)" tag)
               patch            (Long/parseLong patch)
               patch+           (inc patch)]
           (if (not= branch "master")
             (format "%s.%d-%s-SNAPSHOT" prefix patch+ branch)
             (format "%s.%d-SNAPSHOT" prefix patch+)))
         "0.1.0-SNAPSHOT")))}

  :auto
  {:default {:file-pattern #"\.(clj|cljs|cljx|cljc|edn|md)$"
             :paths        [:java-source-paths
                            :source-paths
                            :resource-paths
                            :test-paths
                            :doc-paths]}})
