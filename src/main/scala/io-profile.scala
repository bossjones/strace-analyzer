/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 *                                                                                               *
 *  Copyright  (C)  2015-2016  Christian Krause                                                  *
 *                                                                                               *
 *  Christian Krause  <kizkizzbangbang@gmail.com>                                                *
 *                                                                                               *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 *                                                                                               *
 *  This file is part of strace-analyzer.                                                        *
 *                                                                                               *
 *  strace-analyzer is free software: you can redistribute it and/or modify it under the terms   *
 *  of the GNU General Public License as published by the Free Software Foundation, either       *
 *  version 3 of the License, or any later version.                                              *
 *                                                                                               *
 *  strace-analyzer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; *
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.    *
 *  See the GNU General Public License for more details.                                         *
 *                                                                                               *
 *  You should have received a copy of the GNU General Public License along with                 *
 *  strace-analyzer. If not, see <http://www.gnu.org/licenses/>.                                 *
 *                                                                                               *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */


package strace
package analyze

import scalax.chart.api._

import scalaz.concurrent.Task
import scalaz.stream._
import scalaz.std.anyVal._
import scalaz.std.map._

import org.jfree.data.time.Second

object IOProfile extends Analysis {
  def analyze(implicit config: Config): Unit =
    for ((log,entries) <- parseLogs) {
      saveChart(log, entries, op = "read") {
        case entry: LogEntry.Read => entry
      }

      saveChart(log, entries, op = "write") {
        case entry: LogEntry.Write => entry
      }
    }

  def saveChart(log: String, entries: Process[Task,LogEntry], op: String)
               (pf: PartialFunction[LogEntry,LogEntry with HasBytes with HasFD]): Unit = {

    import util._

    val filtered = entries.collect(pf)

    val analysis = filtered.runGroupByFoldMap(_.fd) { entry =>
      Map(new Second(new java.util.Date(entry.jepoch)) -> entry.bytes)
    }

    for ((file,data) <- analysis.run) {
      val filename = new java.io.File(file).getName
      val logname = new java.io.File(log).getName

      val chart = genChart(data)

      chart.saveAsPNG (
        file = s"""strace-analyzer-profile-$op-$logname-$filename.png""",
        resolution = (1920,1080)
      )
    }
  }

  def genChart[A <: LogEntry with HasBytes](data: Map[Second,Long]) = {
    import java.text._
    import org.jfree.chart.axis.NumberAxis

    val chart = XYBarChart(data.toTimeSeries(""), legend = false)
    chart.plot.range.axis.label.text = "bytes"
    chart.plot.range.axis.peer match {
      case axis: NumberAxis =>
        axis setNumberFormatOverride new NumberFormat {
          def format(value: Long, buf: StringBuffer, fp: FieldPosition): StringBuffer =
            buf append Memory.humanize(value)
          def format(value: Double, buf: StringBuffer, fp: FieldPosition): StringBuffer =
            format(value.round, buf, fp)
          def parse(value: String, pp: ParsePosition): Number = ???
        }

      case _ =>
    }

    chart
  }
}
