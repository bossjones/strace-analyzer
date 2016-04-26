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

object Summary extends Analysis with HasFileOpSummary {
  def analyze(implicit config: Config): Unit =
    for ((log,entries) <- parseLogs) { // TODO don't use for comprehension but why/tee depending on
                                       //which one it was so the parsing has to be done only once
      printSummary(log, entries, "read" ) { case entry: LogEntry.Read  => entry }
      printSummary(log, entries, "write") { case entry: LogEntry.Write => entry }
    }

  def printSummary(log: String, entries: Process[Task,LogEntry], op: String)
                  (pf: PartialFunction[LogEntry,LogEntry with HasBytes]): Unit = {

    val filtered = entries.collect(pf)

    val accumulated = filtered.foldMap(FileOpSummary(_))

    val humanized = accumulated map { acc =>
      s"""$log ${acc.humanized(op)}"""
    }

    humanized.to(io.stdOutLines).run.run
  }
}
