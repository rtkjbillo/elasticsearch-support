![Support](https://github.com/jprante/elasticsearch-support/raw/master/src/site/resources/support.jpg)

# Elasticsearch support plugin

This plugin offers some Java helper classes for easier use of Elasticsearch API.

![Travis](https://travis-ci.org/jprante/elasticsearch-support.png)

## Resilience fork

As of July 2014, I (@rtkjbillo) had some issues with reliability and consistency of record import. The following changes were made to elasticsearch-knapsack and elasticsearch-support, and suit our particular purposes:

In elasticsearch-knapsack (https://github.com/rtkjbillo/elasticsearch-knapsack):

* Add `_site` directory to plugin build (avoid warnings in ElasticSearch logs)
* Don't flush records every 5 seconds (bulk requests were not containing all records)
* Don't set a maximum data size per bulk request
* We repack .tar.gz archives with data; the recreated .tar files have leading directory entries (index/type). Ignore them.
* Ignore cases where the index already exists when running with createIndex=true

In elasticsearch-support (https://github.com/rtkjbillo/elasticsearch-support):

* Wait 60 seconds for individual bulk import threads to close themselves (avoids terminating threads early)
* Ignore 'client closed' errors and continue to try and import

The version number was changed to 1.2.1.1 and this fork works with ES 1.2.1.

## Versions

| Elasticsearch version    | Plugin      | Release date |
| ------------------------ | ----------- | -------------|
| 1.2.1                    | 1.2.1.0     | Jun  4, 2014 |
| 1.2.0                    | 1.2.0.1     | May 28, 2014 |
| 1.2.0                    | 1.2.0.0     | May 22, 2014 |
| 1.1.0                    | 1.1.0.7     | May 11, 2014 |
| 1.0.0.RC2                | 1.0.0.RC2.1 | Feb  3, 2014 |
| 0.90.7                   | 0.90.7.1    | Dec  3, 2013 |
| 0.20.6                   | 0.20.6.1    | Feb  4, 2014 |
| 0.19.11.2                | 0.19.11.2   | Feb  1, 2013 |

## Installation

```
./bin/plugin -install support -url http://xbib.org/repository/org/xbib/elasticsearch/plugin/elasticsearch-support/1.2.1.0/elasticsearch-support-1.2.1.0.zip
```

Do not forget to restart the node after installing.

## Checksum

| File                                          | SHA1                                     |
| --------------------------------------------- | -----------------------------------------|
| elasticsearch-support-1.2.1.0.zip             | 2013659572ea9e81249e3c80493e967d4940f44a |
| elasticsearch-support-1.2.0.1.zip             | 8c4a631eb62e1616e886451c582f60a3248927c0 |
| elasticsearch-support-1.2.0.0.zip             | 63de4c8dbfb15ae3db0f6e7f2708cc3710c88ca6 |
| elasticsearch-support-1.1.0.7.zip             | 05e7194cd2a1f508d071bf74564621176684e598 |

## Project docs

The Maven project site is available at `Github <http://jprante.github.io/elasticsearch-support>`_

## Issues

All feedback is welcome! If you find issues, please post them at `Github <https://github.com/jprante/elasticsearch-support/issues>`_

# License

Elasticsearch Support Plugin

Copyright (C) 2013 Jörg Prante

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
