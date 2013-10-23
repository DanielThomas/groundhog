# Groundhog

> Don't drive angry...

Groundhog is a simple, but high performance, HTTP traffic recording and replay tool.

For more information, see the [Web Site](http://groundhog.io/)

## Features

### Record

* HTTP request capture to HAR format, either in JSON or BSON formats

### Replay

Replay attempts to replay the requests exactly as recorded, including timings and keep alives. It supports:

* User agent detection, to allow cookie storage by detected session
* Override recorded POST request parameters with scraped parameters
* Time dilation - speed up or slow down the replay
* Output of replay performance results in JMeter compatible format

## How to build

* Run `gradlew`, the default build action will assemble the application
* Use the `distZip` target to generate the distribution

## Thanks to

Like any project, Groundhog wouldn't have been possible without the hard work of others. Notably:

* The [Guava Project](https://code.google.com/p/guava-libraries/)
* The [Netty Project](http://netty.io/)
* Adam Fisk's [LittleProxy](https://github.com/adamfisk/LittleProxy)
