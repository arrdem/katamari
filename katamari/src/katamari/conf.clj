(ns katamari.conf
  "Tools for loading a Unix-like conf file

  Conf files are much like INI files, although only a regular subset is used so that programatic
  access from shell scripting tools isn't much more involved than access from Clojure.

  An example of the supported conf syntax

  ```
  # This is a comment
  # Empty lines are ignored

  # This is a global key + value pair
  foo=bar
  # Only simple values involving no whitespace or commas are allowed

  # key + value pairs may be grouped under sections
  [baz]
  # this heading creates the section baz
  # headings must be unique

  # This key + value pair is in the context of the [baz] heading
  something=else

  # Some simple forms of strings are allowed. Escape sequences are not supported.
  string1='this is some text'
  string2=\"this is some other text\"

  # Lists are supported as values
  list=[a, b, c d]
  # The commas are optional, which is why they aren't legal in words
  ```

  This document would parse to

  ```clj
  {\"foo\" \"bar\",
   \"baz\" {\"something\" \"else\",
            \"string1\" \"this is some text\",
            \"string2\" \"this is some other text\",
            \"list\" [\"a\" \"b\" \"c\" \"d\"]}}
  ```
  "
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:refer-clojure :exclude [load load-string])
  (:require [clojure.java.io :as jio]
            [instaparse.core :as insta]))

(def +parser+
  (insta/parser (slurp (jio/resource "katamari/conf.abnf"))))

(defn transformers [key-fn]
  {:WORD identity
   :VALUE identity
   :LIST vector
   :KEYVALUE (fn [k v] (assoc {} (key-fn k) v))
   :HEADER identity
   :SECTION (fn [header & kvs] {(key-fn header) (apply merge kvs)})
   :INI (fn [& maps-or-section-maps] (apply merge maps-or-section-maps))})

(defn load-string
  "Given a string of CONF style text, parse it returning a map.

  See the ns docstring for an extended discussion of the supported grammar."
  ([str]
   (load-string str identity))
  ([str key-fn]
   (->> str
        (insta/parse +parser+)
        (insta/transform (transformers key-fn)))))

(defn load
  "Given a URI or something else which can be slurped, slurp it and return the load of its contents.

  See the ns docstring for an extended discussion of the supported grammar."
  ([uri]
   (load uri identity))
  ([uri key-fn]
   (load-string (slurp uri) key-fn)))

(comment
  (load-string "
# A comment
global_foo=bar
[test]
# Another comment
foo=bar
baz=[
  foo,
  bar,
  baz,
]
")

  (load-string "
# This is a comment
# Empty lines are ignored

# This is a global key + value pair
foo=bar
# Only simple values involving no whitespace or commas are allowed

# key + value pairs may be grouped under sections
[baz]
# this heading creates the section baz
# headings must be unique

# This key + value pair is in the context of the [baz] heading
something=else

# Some simple forms of strings are allowed. Escape sequences are not supported.
string1='this is some text'
string2=\"this is some other text\"

# Lists are supported as values
list=[a, b, c d]
# The commas are optional, which is why they aren't legal in words
"))
