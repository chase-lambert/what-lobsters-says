(ns lobsters-ext.api
  (:require [cljs.core.async :refer [go <!]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [clojure.string :as str]))

(defn extract-domain [url]
  (try
    (let [url-obj  (js/URL. url)
          hostname (.-hostname url-obj)]
      (str/replace hostname #"^www\." ""))
    (catch js/Error _
      nil)))

(def ^:private tracking-params
  #{"utm_source" "utm_medium" "utm_campaign" "utm_term" "utm_content"
    "utm_id" "utm_cid" "gclid" "fbclid" "msclkid" "clid"
    "ref" "ref_src" "ref_url" "source" "mc_cid" "mc_eid"})

(defn- strip-tracking-params [url]
  (try
    (let [url-obj (js/URL. url)
          params (.-searchParams url-obj)]
      (doseq [param tracking-params]
        (.delete params param))
      (.toString url-obj))
    (catch js/Error _
      url)))

(defn clean-url [url]
  (-> url
      strip-tracking-params
      (str/replace #"(^\w+:|^)//" "")
      (str/replace #"#.+$" "")
      (str/replace #"index\.(php|html?)$" "")
      (as-> u
            (if (and (str/ends-with? u "/")
                     (< (count (str/split u #"/")) 3))
              (str/replace u #"/+$" "")
              u))))

(defn fetch-json [url]
  (go
    (try
      (let [response (<p! (js/fetch url))]
        (if (.-ok response)
          (let [data (<p! (.json response))]
            {:success true
             :data (js->clj data :keywordize-keys true)})
          {:success false
           :error (str "HTTP " (.-status response))}))
      (catch js/Error e
        {:success false
         :error (.-message e)}))))

(defn fetch-stories-by-domain [domain]
  (go
    (when domain
      (let [url (str "https://lobste.rs/domains/" domain ".json")
            result (<! (fetch-json url))]
        (if (:success result)
          {:success true
           :stories (:data result)}
          {:success false
           :stories []})))))

(defn url-matches? [story-url target-url]
  (when (and (seq story-url) (seq target-url))
    (let [clean-story (str/lower-case (clean-url story-url))
          clean-target (str/lower-case (clean-url target-url))
          norm-story (str/replace clean-story #"/+$" "")
          norm-target (str/replace clean-target #"/+$" "")]
      (or
       (= norm-story norm-target)
       (and (> (count norm-story) 10)
            (or (str/starts-with? norm-target norm-story)
                (str/starts-with? norm-story norm-target)))))))

(defn search-stories [stories target-url]
  (->> stories
       (filter #(seq (:url %)))
       (filter #(url-matches? (:url %) target-url))))

(defn fetch-lobsters-stories []
  (go
    (let [hottest-result (<! (fetch-json "https://lobste.rs/hottest.json"))
          newest-result (<! (fetch-json "https://lobste.rs/newest.json"))
          active-result (<! (fetch-json "https://lobste.rs/active.json"))]

      (if (and (:success hottest-result)
               (:success newest-result)
               (:success active-result))
        (let [hottest (:data hottest-result)
              newest (:data newest-result)
              active (:data active-result)
              all-stories (concat hottest newest active)
              unique-stories (vals (reduce (fn [acc story]
                                             (assoc acc (:short_id story) story))
                                           {}
                                           all-stories))]
          {:success true
           :stories unique-stories})
        {:success false
         :error "Failed to fetch stories from Lobsters"}))))

(defn time-since
  "Convert a datetime string to relative time (e.g., '2 hours ago')"
  [datetime-str]
  (try
    (let [then (js/Date. datetime-str)
          now (js/Date.)
          seconds (/ (- (.getTime now) (.getTime then)) 1000)]
      (cond
        (< seconds 60) "Just now"
        (< seconds 120) "1 minute ago"
        (< seconds 3600) (str (Math/floor (/ seconds 60)) " minutes ago")
        (< seconds 7200) "1 hour ago"
        (< seconds 86400) (str (Math/floor (/ seconds 3600)) " hours ago")
        (< seconds 172800) "Yesterday"
        (< seconds 604800) (str (Math/floor (/ seconds 86400)) " days ago")
        (< seconds 1209600) "Last week"
        (< seconds 2419200) (str (Math/floor (/ seconds 604800)) " weeks ago")
        (< seconds 4838400) "Last month"
        (< seconds 29030400) (str (Math/floor (/ seconds 2419200)) " months ago")
        (< seconds 58060800) "Last year"
        :else (str (Math/floor (/ seconds 29030400)) " years ago")))
    (catch js/Error _
      "Unknown")))
