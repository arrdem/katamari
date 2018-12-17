(ns demo
  (:import demo.Demo)
  (:gen-class))

(defn -main [& args]
  (println "Hit the clj main!")
  (Demo/main (into-array java.lang.String args)))
