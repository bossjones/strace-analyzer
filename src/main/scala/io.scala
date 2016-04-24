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

import scalaz.concurrent.Task
import scalaz.stream._

trait PerFileSummary extends HasFileOpSummary {
  def analysis(entries: Process[Task,LogEntry], op: String)
              (pf: PartialFunction[LogEntry,LogEntry with HasBytes with HasFD]): Unit = {

    import util._

    val filtered = entries.collect(pf)

    val analysis = filtered.runGroupByFoldMap(_.fd)(FileOpSummary(_)).run

    for ((file,analysis) <- analysis) {
      val output = analysis.humanized(op)
      println(s"""$output $file""")
    }
  }
}

object IO extends Analysis with PerFileSummary {
  def analyze(implicit config: Config): Unit =
    for ((_,entries) <- parseLogs) {
      analysis(entries, op = "read") { case entry: LogEntry.Read => entry }
      analysis(entries, op = "write") { case entry: LogEntry.Write => entry }
    }
}

object Read extends Analysis with PerFileSummary {
  def analyze(implicit config: Config): Unit =
    for ((_,entries) <- parseLogs) {
      analysis(entries, op = "read") { case entry: LogEntry.Read => entry }
    }
}

object Write extends Analysis with PerFileSummary {
  def analyze(implicit config: Config): Unit =
    for ((_,entries) <- parseLogs) {
      analysis(entries, op = "write") { case entry: LogEntry.Write => entry }
    }
}
