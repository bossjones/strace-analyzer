package strace

object Main extends App {
  // TODO if no file or "-" read from STDIN
  if (args.size == 0) {
    Console.err.println("usage: strace-analyzer /path/to/strace.log")
    sys exit 1
  }

  // TODO scalaz-stream / fs2
  val log = io.Source.fromFile(args(0))

  val fdDB = collection.mutable.Map[String,String]()

  val entries: List[LogEntry] = log.getLines.collect({
    case LogEntry.Close(close) if close.status >= 0 =>
      fdDB -= close.fd
      close

    case LogEntry.Creat(creat) if creat.status >= 0 =>
      fdDB += (creat.fd -> creat.file)
      creat

    case LogEntry.Dup(dup) if dup.status >= 0 =>
      fdDB get dup.oldFd match {
        case Some(file) =>
          fdDB += (dup.newFd -> file)
        case None =>
          fdDB += (dup.newFd -> "no entry for dup found, probably PIPE")
      }
      dup

    case LogEntry.Open(open) if open.status >= 0 =>
      fdDB += (open.fd -> open.file)
      open

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
    entries.foldLeft(ReadAnalysis.empty)(_ + ReadAnalysis(_))
  }

  readAnalysis.toSeq sortBy { _._2.bps } foreach {
    case (file,analysis) =>
      println(s"""$file ${analysis.msg}""")
  }

  val writes = entries collect {
    case entry: LogEntry.Write => entry
  }

  val writeAnalysis = writes.groupBy(_.fd) mapValues { entries =>
    entries.foldLeft(WriteAnalysis.empty)(_ + WriteAnalysis(_))
  }

  writeAnalysis.toSeq sortBy { _._2.bps } foreach {
    case (file,analysis) =>
      println(s"""$file ${analysis.msg}""")
  }
}

case class ReadAnalysis(bytes: Long, seconds: Double) {
  def +(that: ReadAnalysis): ReadAnalysis = ReadAnalysis (
    bytes = this.bytes + that.bytes,
    seconds = this.seconds + that.seconds
  )

  def bps = bytes / seconds

  def msg = s"""read $bytes bytes in $seconds seconds"""
}

object ReadAnalysis {
  val empty = ReadAnalysis(0L, 0.0)

  def apply(read: LogEntry.Read): ReadAnalysis = ReadAnalysis (
    bytes = read.bytes,
    seconds = read.time.toDouble
  )
}

case class WriteAnalysis(bytes: Long, seconds: Double) {
  def +(that: WriteAnalysis): WriteAnalysis = WriteAnalysis (
    bytes = this.bytes + that.bytes,
    seconds = this.seconds + that.seconds
  )

  def bps = bytes / seconds

  def msg = s"""wrote $bytes bytes in $seconds seconds"""
}

object WriteAnalysis {
  val empty = WriteAnalysis(0L, 0.0)

  def apply(write: LogEntry.Write): WriteAnalysis = WriteAnalysis (
    bytes = write.bytes,
    seconds = write.time.toDouble
  )
}
