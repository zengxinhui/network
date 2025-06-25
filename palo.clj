;; Parses "show dev" output of Panorama.
;; Returns a list of maps. Each map contains keys defined in `_keys`

(ns palo
  (:require [clj-http.client :as http]
            [clojure.data.xml :as xml]))

(def _keys [:hostname
            :ip-address :model :sw-version :serial
            :connected :family :uptime :certificate-expiry :device-cert-expiry-date
            :vsys])

(def _config {"<ip1>" "<key>",
              "<ip2>" "<key>",})

(def today (.format (java.text.SimpleDateFormat. "yyyy-MM-dd ") (java.util.Date.)))

(defn call-api [host req]
  (let [rep (http/post (str "https://" host req)
                       {:headers {:Authorization (str "Basic " (_config host))}
                        :insecure? true})]
    (if (= 200 (:status rep))
      (:body rep))))

;; If api call is enabled:
;; (def s (call-api "<ip>" "/api/?type=op&cmd=<show><devices><all/></devices></show>"))
;; (def t (xml/parse-str s))
;; Or else parse a file:
(def t (xml/parse-str (slurp "showdev.xml")))

(defn findall [xmls tag]
  (mapcat #(if (= tag (:tag %)) (:content %)) xmls))

(defn parse-vsys [xmls]
  (->> (for [xml xmls]
         [(first (findall (:content xml) :display-name))
          (:name (:attrs xml))])
       (into {})))

(defn get-tags [xml target-tags]
  (let [target-tags-set (set target-tags)]
    (->> (:content xml)
         (keep #(if (target-tags-set (:tag %))
                  (if (= :vsys (:tag %))
                    [(:tag %) (parse-vsys (:content %))]
                    [(:tag %) (first (:content %))]))))))

(defn parse-devices [xml]
  (map #(into {} (get-tags % _keys))
       (reduce findall [xml] [:response :result :devices])))

(parse-devices t)

(comment
  "/api/?type=op&cmd=<show><devices><all/></devices></show>"
  "/api/?type=config&action=get&xpath=/config/devices/entry[@name='localhost.localdomain']/template")
