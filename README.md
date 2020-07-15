# windowed-word-count

This application performs windowed word count on data from its input stream.

- The input lines are expected to be in a specific JSON format. If a line is malformed, the application ignores it.
- The application exposes an HTTP endpoint at http://localhost:8080/wordcount. The response to this endpoint is the word count dataset collected so far.
- The window duration is one minute.

## Instructions 

1. Build the application with `sbt assembly`.
2. Run the resulting jar, passing the data stream to its input: `./blackboxexe | java -jar ./target/scala-2.13/windowed-word-count-assembly-0.1.jar`.

## Design and development notes

My goal was to implement the application with as much complexity as was needed to support the requirements, but not much more. I haven't used any data processing frameworks, like Flink or Beam, as they seem like an overkill for this particular job.

The main libraries I have chosen are:

- Monix for it's Observable, which provides a push based data stream with API for mapping and filtering.
- HTTP4S for the HTTP server.
- Circe for decoding and encoding JSON.

The basic structure of the application is as follows:

- Lines from the input stream are fed into an Observable.
- Each line is decoded into a case class.
- For each line the window is decided by rounding the date/time down to the start of the minute.
- Lines are then stored in a map, keyed with a tuple of window, event type and word. The value in the map is the word count.

An HTTP server is started together with the Observable, and on request reads the current data from the map.

All code is contained in a single file for readability, and because there's not much of it.

My original idea was to use Akka Streams to read and transform the input, and keep the word count data in an actor. This would allow to parallelise the input easily because the shared state is encapsulated in the actor. However, in the interest of reducing complexity I reconsidered and used Monix and a concurrent.Map instead.