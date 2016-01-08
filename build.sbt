enablePlugins(GitVersioning)
enablePlugins(JavaAppPackaging)
enablePlugins(UniversalPlugin)

organization in ThisBuild := "com.github.wookietreiber"

scalaVersion in ThisBuild := "2.11.8"

git.baseVersion in ThisBuild := "0.3.0"

lazy val root = (project in file(".")).
  enablePlugins(BuildInfoPlugin).
  settings (
    name := "strace-analyzer",
    libraryDependencies += "com.github.wookietreiber" %% "scala-chart" % "0.5.0",
    libraryDependencies += "org.scalaz.stream" %% "scalaz-stream" % "0.8",
    buildInfoKeys := Seq[BuildInfoKey](name, version),
    buildInfoPackage := "strace.analyze",
    mappings in Universal <++= name in Universal map { name =>
      val license = file("LICENSE")
      val notice = file("NOTICE.md")
      val manPage = file("strace-analyzer.1")
      val completion = file("bash-completion.sh")
      Seq (
        license -> ("share/" + name + "/LICENSE"),
        notice -> ("share/" + name + "/NOTICE.md"),
        manPage -> ("share/man/man1/" + manPage),
        completion -> ("share/bash-completion/completions/" + name)
      )
    }
  )
