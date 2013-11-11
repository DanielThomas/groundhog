# Groundhog

> Don't drive angry...

Groundhog is a simple, high performance, HTTP traffic record and replay tool. It's designed to be deployed in production environments to allow real world traffic to be replayed against development and staging environments.

It provides a recording proxy server, which outputs captured entries in HAR format and a replay client, which attempts to replay the events exactly as recorded including timings, connection behaviour and request details.

## Features

### Record

* Proxy supports forward and reverse (gateway) configurations
* Traffic persisted in HAR format, using a high performance streaming writer
* Lightweight capture for replay or full capture equivalent to browser developer tool HARs
* Recording control via simple REST API
* Include and exclude URI patterns from recording

### Replay

* Dispatches requests using the time indexes in the HAR data
* Time dilation: speed up or slow down the replay
* Detection of unique user agents via cookies, which allows:
    * Cookies provided during the session to replace those from the recording
    * Overriding POST request parameters with those scraped from retrieved documents, supporting nonces and other generated fields
    * Rewriting XHR requests with container session information, to support frameworks such as DWR
* Simple error detection by comparing recorded response codes
* Label transaction names via a HTTP header
* Replay in JMeter using a replay HAR Sampler

## Documentation

See the [Wiki](https://github.com/blackboard/groundhog/wiki).

## Performance

The recording proxy shares same performance characteristics of [LittleProxy](https://github.com/adamfisk/LittleProxy).

Performance data to come.

## Releases

* 0.1-SNAPSHOT - Initial prototype

## How to build

* Run `gradlew`, the default build action will assemble the application
* Use the `distZip` target to generate the distribution
* Replay and record can also be run in place using `:record:run` or `:replay:run`

## Acknowledgements

Like any project, Groundhog wouldn't been possible without the hard work of others. Notably:

* Adam Fisk's [LittleProxy](https://github.com/adamfisk/LittleProxy)
* The [Netty Project](http://netty.io/)
* The [Guava Project](https://code.google.com/p/guava-libraries/)
