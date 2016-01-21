# sparrows

A Clojure library provides miscellaneous utils. This library also loads `com.taoensso/timbre` logging as dependency.

## Dependencies

AES encyption/decryption requres unlimited JCE extension for JVM, see http://stackoverflow.com/questions/6481627/java-security-illegal-key-size-or-default-parameters/6481658#6481658

## Install

```
cd sparrows
lein install
```

## Usage

Include the following in `project.clj`,

```
[sparrows "0.1.3"]
```

## TODO

* Update encryption
* Update dissoc-empty-vals, date-time utils, md5sum should auto close opened file

## License

Copyright © 2015 CL

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.


