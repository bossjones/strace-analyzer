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

abstract class Analysis {

  val bufSize = math.pow(2,20).toInt

  /** Analyzes strace logs and prints the result to STDOUT. */
  def analyze(implicit config: Config): Unit

  /** Returns the log entries grouped by log input. */
  def parseLogs(implicit config: Config): Iterator[(String,Process[Task,LogEntry])] =
    if (config.logs.isEmpty)
      Iterator("STDIN" -> parseLog(io.stdInLines))
    else {
      val xs = for {
        log <- config.logs.distinct.toIterator
        source = io.linesR(log) // TODO use appropriate buffer size (using **bufSize**)
        entries = parseLog(source)
      } yield log -> entries

      xs
    }

  /** Returns a parsed strace log. */
  def parseLog(log: Process[Task,String]): Process[Task,LogEntry] = {

    // initialize file descriptor database with standard streams
    val fdDB = collection.mutable.Map[String,String] (
      "0" -> "STDIN",
      "1" -> "STDOUT",
      "2" -> "STDERR"
    )

    log collect {
      case LogEntry.Close(close) if close.status >= 0 =>
        fdDB -= close.fd
        close

      case LogEntry.Creat(creat) if creat.status >= 0 =>
        fdDB += (creat.fd -> creat.file)
        creat

      case LogEntry.Dup(dup) if dup.status >= 0 =>
        val where = fdDB get dup.oldFd
        val file = where.fold("no entry for dup found, probably PIPE")(identity)
        fdDB += (dup.newFd -> file)
        dup

      case LogEntry.Dup2(dup2) if dup2.status >= 0 =>
        val where = fdDB get dup2.oldFd
        val file = where.fold("no entry for dup2 found, probably PIPE")(identity)
        fdDB += (dup2.newFd -> file)
        dup2

      case LogEntry.Open(open) if open.status >= 0 =>
        fdDB += (open.fd -> open.file)
        open

      case LogEntry.OpenAt(openat) if openat.status >= 0 =>
        val where = fdDB get openat.wherefd
        val file = where.fold(openat.filename)(openat.file)
        fdDB += (openat.fd -> file)
        openat

      case LogEntry.Pipe(pipe) if pipe.status >= 0 =>
        fdDB += (pipe.read -> "PIPE")
        fdDB += (pipe.write -> "PIPE")
        pipe

        // TODO ignore exit status 0?
      case LogEntry.Read(read) if read.status >= 0 =>
        fdDB.get(read.fd).fold(read)(file => read.copy(fd = file))

        // TODO ignore exit status 0?
      case LogEntry.Write(write) if write.status >= 0 =>
        fdDB.get(write.fd).fold(write)(file => write.copy(fd = file))
    }
  }
}
