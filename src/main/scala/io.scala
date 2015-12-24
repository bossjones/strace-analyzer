package strace
package analyze

import java.io.File

// TODO scalaz-stream / fs2
// TODO scopt apparently incapable of handling - as a file
class IO(config: Config) {

  if (config.logs.isEmpty)
    handleLog(io.Source.stdin)
  else
    config.logs.distinct foreach { logFile =>
      val source = io.Source.fromFile(logFile)

      try {
        handleLog(source)
      } finally {
        source.close()
      }
    }

  def handleLog(log: io.Source): Unit = {
    val fdDB = collection.mutable.Map[String,String]()

    val entries: List[LogEntry] = log.getLines.collect({
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

      case LogEntry.Read(read) if read.status >= 0 =>
        fdDB.get(read.fd).fold(read)(file => read.copy(fd = file))

      case LogEntry.Write(write) if write.status >= 0 =>
        fdDB.get(write.fd).fold(write)(file => write.copy(fd = file))
    }).toList

    log.close()

    val reads = entries collect {
      case entry: LogEntry.Read => entry
    }

    val readAnalysis = reads.groupBy(_.fd) mapValues { entries =>
      entries.foldLeft(FileSummary.empty("read"))(_ + FileSummary(_))
    }

    readAnalysis.toSeq sortBy { _._2.bytes } foreach {
      case (file,analysis)
          if config.regex.map(_.findFirstIn(file).isDefined).orElse(config.filter.map(file.contains)).getOrElse(true) =>
        println(s"""$file ${analysis.msg}""")
      case _ =>
    }

    val writes = entries collect {
      case entry: LogEntry.Write => entry
    }

    val writeAnalysis = writes.groupBy(_.fd) mapValues { entries =>
      entries.foldLeft(FileSummary.empty("write"))(_ + FileSummary(_))
    }

    writeAnalysis.toSeq sortBy { _._2.bytes } foreach {
      case (file,analysis)
          if config.regex.map(_.findFirstIn(file).isDefined).orElse(config.filter.map(file.contains)).getOrElse(true) =>
        println(s"""$file ${analysis.msg}""")
      case _ =>
    }
  }

  case class FileSummary(op: String, bytes: Long, ops: Long, seconds: Double) {
    def +(that: FileSummary): FileSummary = FileSummary (
      op = this.op,
      bytes = this.bytes + that.bytes,
      ops = this.ops + that.ops,
      seconds = this.seconds + that.seconds
    )

    def bps = bytes / seconds

    def bpo = bytes.toDouble / ops

    def hBytes = Memory.humanize(bytes)

    def hSeconds = Duration.humanize(seconds)

    def hbps = Memory.humanize(bps.round)

    def hbpo = Memory.humanize(bpo.round)

    def msg = s"""$op $hBytes in $hSeconds (~$hbps/s) with $ops ops (~$hbpo/o)"""
  }

  object FileSummary {
    def empty(op: String) = FileSummary(op, bytes = 0L, ops = 0L, seconds = 0.0)

    def apply(read: LogEntry.Read): FileSummary =
      FileSummary(op = "read", bytes = read.bytes, ops = 1, seconds = read.time.toDouble)

    def apply(write: LogEntry.Write): FileSummary =
      FileSummary(op = "write", bytes = write.bytes, ops = 1, seconds = write.time.toDouble)
  }

}
