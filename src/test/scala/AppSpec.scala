import java.time.{LocalDateTime, ZoneOffset}

import org.scalatest._
import flatspec._
import matchers._

class AppSpec extends AnyFlatSpec with should.Matchers {

  it should "apply correct window to an input line" in {

    val timestamp = 1594807419

    val line = InputLine("foo", "lorem", timestamp)

    val windowedLine = MainApp.window(line)

    val expectedWindow = LocalDateTime.ofEpochSecond(timestamp, 0, ZoneOffset.UTC).withSecond(0)

    windowedLine should be (Right(WindowedInputLine(expectedWindow, line)))
  }

  it should "return an error in case a timestamp is invalid" in {

    val timestamp = Long.MaxValue

    val line = InputLine("foo", "lorem", timestamp)

    val windowedLine = MainApp.window(line)

    windowedLine.isLeft should be (true)
  }

  it should "correctly print word counts as JSON" in {

    val timestamp1 = 1594807419
    val window1 = LocalDateTime.ofEpochSecond(timestamp1, 0, ZoneOffset.UTC).withSecond(0)

    val timestamp2 = 1594807519
    val window2 = LocalDateTime.ofEpochSecond(timestamp2, 0, ZoneOffset.UTC).withSecond(0)

    val wordCounts = Map(
      (window1, "foo", "lorem") -> 1,
      (window1, "foo", "ipsum") -> 2,
      (window1, "bar", "ipsum") -> 2,
      (window2, "foo", "lorem") -> 2,
    )

    val renderedJson = WordCountService.printWordCount(wordCounts).noSpacesSortKeys

    val expectedJson = """{"2020-07-15T10:03":{"bar":{"ipsum":2},"foo":{"ipsum":2,"lorem":1}},"2020-07-15T10:05":{"foo":{"lorem":2}}}"""

    renderedJson.replace(" ", "") should be (expectedJson)
  }
}
