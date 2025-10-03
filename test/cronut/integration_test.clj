(ns cronut.integration-test
  (:require [clojure.java.io :as io]
            [cronut.integrant :as cig]
            [integrant.core :as ig]
            [job]))

(defn init-system
  "Example of starting integrant cronut systems with data-readers"
  ([]
   (init-system (slurp (io/resource "config.edn"))))
  ([config]
   (init-system config nil))
  ([config readers]
   (ig/init (ig/read-string {:readers (merge cig/data-readers readers)} config))))

(defn halt-system
  "Example of stopping integrant cronut systems"
  [system]
  (ig/halt! system))