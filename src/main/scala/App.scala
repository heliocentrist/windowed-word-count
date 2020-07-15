import java.time.{LocalDateTime, ZoneOffset}

import MainApp.GroupingKey
import cats.effect.IO
import cats.effect._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import org.http4s.dsl.io._
import org.http4s.implicits._
import monix.reactive.Observable
import monix.execution.Scheduler.Implicits.global

import scala.collection.concurrent._
import scala.io.StdIn
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.http4s.HttpRoutes
import org.http4s.dsl.impl.Root
import org.http4s.server.blaze.BlazeServerBuilder

import scala.util.Try

case class InputLine(event_type: String, data: String, timestamp: Long)

case class WindowedInputLine(windowStart: LocalDateTime, line: InputLine)

object WordCountService extends LazyLogging {

  /**
   * Takes a map of word counts grouped by (Window start, event type, word),
   * and returns its JSON representation.
   * @param map A map of word counts.
   * @return JSON
   */
  def printWordCount(map: collection.Map[GroupingKey, Int]): Json = {

    val windows = map.keys.map(key => key._1).toList.sorted

    val groupedByWindow = map.groupBy(pair => pair._1._1)

    val wordCountGroupedByEventTypeAndWindow = windows.map(window => {
      val windowMap = groupedByWindow.getOrElse(window, collection.Map.empty[GroupingKey, Int])

      val eventTypes = windowMap.keys.map(key => key._2)

      val groupedByEventType = windowMap.groupBy(pair => pair._1._2)

      val wordCountGroupedByEventType = eventTypes.map(eventType => {
        val eventTypeMap = groupedByEventType.getOrElse(eventType, collection.Map.empty[GroupingKey, Int])

        val words = eventTypeMap.keys.map(key => key._3).toSeq

        val wordsCount: Seq[(String, Int)] = words.map(s => s -> eventTypeMap.getOrElse((window, eventType, s), 0))

        eventType -> wordsCount.toMap
      })

      window.toString -> wordCountGroupedByEventType.toMap
    }).toMap

    wordCountGroupedByEventTypeAndWindow.asJson
  }

  /**
   * Builds and returns a service that exposes word count data over HTTP.
   *
   * @param config Configuration containing `host` and `port`.
   * @param map Map containing the word count data.
   * @return IO containing the service.
   */
  def getService(config: Config, map: TrieMap[GroupingKey, Int])(implicit
                                                 F: ConcurrentEffect[IO],
                                                 timer: Timer[IO]): IO[Unit] = {

    val port = config.getInt("port")
    val host = config.getString("host")

    val helloWorldService = HttpRoutes.of[IO] {
      case GET -> Root / "wordcount" =>
        logger.debug(Thread.currentThread().getName)
        Ok(printWordCount(map.readOnlySnapshot()).toString())
    }.orNotFound

    BlazeServerBuilder[IO](global)
      .bindHttp(port, host)
      .withHttpApp(helloWorldService)
      .serve
      .compile
      .drain
  }
}

object MainApp extends IOApp with LazyLogging {

  type GroupingKey = (LocalDateTime, String, String)

  private val inputLines = Observable.repeatEval(StdIn.readLine)

  private val map: TrieMap[GroupingKey, Int] = TrieMap.empty[GroupingKey, Int]

  private val config = ConfigFactory.load

  def run(args: List[String]): IO[ExitCode] = {

    val monixio = IO.fromFuture(IO(inputLines
      .map(s => processLine(s).toOption)
      .filter(_.isDefined)
      .map(_.get)
      .foreach(wil => addToMap(map, wil))))

    val http4sio = WordCountService.getService(config.getConfig("http"), map)

    IO.race(monixio, http4sio).as(ExitCode.Success)
  }

  def processLine(s: String): Either[Throwable, WindowedInputLine] = {
    for {
      decodedInputLine <- decode[InputLine](s)
      windowedInputLine <- window(decodedInputLine)
    } yield windowedInputLine
  }

  /**
   * Takes a line of input, and wraps it into a WindowedInputLine based on its timestamp.
   * The window duration is one minute.
   *
   * @param line Input line with a timestamp.
   * @return Either a Windowed input line, or a Throwable if the timestamp is invalid.
   */
  def window(line: InputLine): Either[Throwable, WindowedInputLine] = {

    val timestampDateTime = Try { LocalDateTime.ofEpochSecond(line.timestamp, 0, ZoneOffset.UTC) }.toEither

    timestampDateTime.map(dateTime => {
      val windowStartDateTime = dateTime.withSecond(0)

      WindowedInputLine(windowStartDateTime, line)
    })
  }

  /**
   * Adds a new word occurrence to the word count map.
   *
   * @param map Map containing the word counts.
   * @param wil Input line with the window.
   */
  def addToMap(map: Map[GroupingKey, Int], wil: WindowedInputLine): Unit = {
    map.get((wil.windowStart, wil.line.event_type, wil.line.data)) match {
      case None => map.putIfAbsent((wil.windowStart, wil.line.event_type, wil.line.data), 1)
      case Some(count) => map.replace((wil.windowStart, wil.line.event_type, wil.line.data), count + 1)
    }
  }
}
