# Groundhog

> Don't drive angry...

Groundhog is a simple, high performance, HTTP traffic capture and replay tool for repeatable system performance testing.

While this is project has been somewhat battle tested, it's lacking niceities such as documentation, and has several hard coded configurations; I'm sharing this here as a sneak preview for some folks I met at Velocity.

An official release is planned that will fill those gaps, at that time the project will be available at the [Groundhog](https://github.com/groundhog) organisation and [groundhog.io](http://groundhog.io/). Stay tuned.

## Features

### Capture

Requests are captured in HAR format by either a proxy or servlet container component:

* The proxy supports forward and reverse (gateway) configurations, and MITM TLS
* Traffic persisted in HAR format, using a high performance streaming writer. The default format is lightweight, omitting unnecessary fields
* Control via simple REST API

### Replay

Replay of the captured requests is hanlded by either a standalone replay client, or a JMeter sampler. The same engine underpins both components.

* Dispatches requests using the original time indexes in the HAR data to calculate offsets
* Detection of unique user agents via cookies, which allows:
    * Requests that are expected to set cookies to be fired serially for that user agent, avoiding potential timing issues with cookies
    * Cookies provided during the session to replace those from the recording
    * Overriding POST request parameters with those scraped from retrieved documents, supporting nonces and other generated fields
    * Rewriting XHR requests with container session information, to support frameworks such as DWR
* Simple error detection by comparing recorded response codes
* Label transaction names via a HTTP header
* Replay in JMeter using a replay HAR Sampler

## Planned Features

* Time dilation - speed up or slow down replay
* More advanced user agent fingerprinting, allowing more than a session cookie to drive user agent identification

## Performance

The recording proxy shares same performance characteristics of [LittleProxy](https://github.com/adamfisk/LittleProxy).

Other performance data to come.

## How to build

* Clone the repository, Run `gradlew`, the default build action will assemble the application
* Use the `distZip` target to generate the distribution
* Replay and record can also be run in place using `:capture:run` or `:replay:run`
* Use ':jmeter:fatJar' to generate the JMeter plugin, and copy it to the plugins directory to use

## Acknowledgements

The project's name was inspired by the movie Groundhog Day. A _huge_ thank you to Molly Mattis for donating her username to the project.

The project wouldn't have been possible without the hard work of others. Notably Adam Fisk's [LittleProxy](https://github.com/adamfisk/LittleProxy), and the [Netty Project](http://netty.io/). I also can't imagine working on any project without [Guava](https://code.google.com/p/guava-libraries/).
