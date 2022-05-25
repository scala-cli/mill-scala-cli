//> using scala "2.13"
//> using lib "com.lihaoyi::mill-scalalib:0.10.4"
//> using option "-deprecation"

package scala.cli.mill

import coursier.cache.{ArchiveCache, FileCache}
import coursier.cache.loggers.{FallbackRefreshDisplay, ProgressBarRefreshDisplay, RefreshLogger}
import coursier.util.Artifact
import mill._
import mill.scalalib.ScalaModule
import mill.scalalib.api.CompilationResult

import java.io.File
import java.util.Locale

import scala.util.Properties

trait ScalaCliCompile extends ScalaModule {

  protected object ScalaCliInternal {
    // disable using scala-cli to build scala-cli on unsupported architectures
    lazy val isSupportedArch: Boolean = {
      val supportedArch = Seq("x86_64", "amd64")
      val osArch        = sys.props("os.arch").toLowerCase(Locale.ROOT)
      supportedArch.exists(osArch.contains(_))
    }

    lazy val compileScalaCliImpl = compileScalaCliUrl.map { url =>
      val logger = RefreshLogger.create(
        if (coursier.paths.Util.useAnsiOutput())
          ProgressBarRefreshDisplay.create()
        else
          new FallbackRefreshDisplay
      )
      val cache = FileCache().withLogger(logger)
      val artifact = Artifact(url).withChanging(compileScalaCliIsChanging)
      val archiveCache = ArchiveCache()
        .withCache(cache)
      if (compileScalaCliIsCompressed)
        archiveCache.get(artifact).unsafeRun()(cache.ec) match {
          case Left(e) => throw new Exception(e)
          case Right(f) =>
            if (Properties.isWin)
              os.list(os.Path(f, os.pwd)).filter(_.last.endsWith(".exe")).headOption match {
                case None      => sys.error(s"No .exe found under $f")
                case Some(exe) => exe
              }
            else {
              f.setExecutable(true)
              os.Path(f, os.pwd)
            }
        }
      else
        cache.file(artifact).run.unsafeRun()(cache.ec) match {
          case Left(e) => throw new Exception(e)
          case Right(f) =>
            if (!Properties.isWin)
              f.setExecutable(true)
            os.Path(f, os.pwd)
        }
    }
  }
  import ScalaCliInternal._

  def enableScalaCli: Boolean =
    System.getenv("CI") == null && isSupportedArch
  def scalaCliVersion: String =
    "0.1.5"

  def compileScalaCliUrl: Option[String] = {
    val ver = scalaCliVersion
    if (Properties.isLinux) Some(
      s"https://github.com/VirtusLab/scala-cli/releases/download/v$ver/scala-cli-x86_64-pc-linux.gz"
    )
    else if (Properties.isWin) Some(
      s"https://github.com/VirtusLab/scala-cli/releases/download/v$ver/scala-cli-x86_64-pc-win32.zip"
    )
    else if (Properties.isMac) Some(
      s"https://github.com/VirtusLab/scala-cli/releases/download/v$ver/scala-cli-x86_64-apple-darwin.gz"
    )
    else None
  }
  def compileScalaCliIsChanging: Boolean = false
  def compileScalaCliIsCompressed: Boolean =
    compileScalaCliUrl.exists(url => url.endsWith(".gz") || url.endsWith(".zip"))

  def compileScalaCli: Option[os.Path] = compileScalaCliImpl

  def extraScalaCliOptions: T[List[String]] =
    T {
      List.empty[String]
    }

  override def compile: T[CompilationResult] =
    if (enableScalaCli)
      compileScalaCli match {
        case None => super.compile
        case Some(cli) =>
          T.persistent {
            val out = os.pwd / ".scala-build" / ".unused"

            val sourceFiles = allSources()
              .map(_.path)
              .filter(os.exists(_))
            val workspace = T.dest / "workspace"
            os.makeDir.all(workspace)
            val classFilesDir =
              if (sourceFiles.isEmpty) out / "classes"
              else {
                def asOpt[T](opt: String, values: IterableOnce[T]): Seq[String] =
                  values.iterator.toList.flatMap(v => Seq(opt, v.toString))

                val proc = os.proc(
                  cli,
                  Seq("compile", "--classpath"),
                  Seq("-S", scalaVersion()),
                  asOpt("-O", scalacOptions()),
                  asOpt("--jar", compileClasspath().map(_.path)),
                  asOpt("-O", scalacPluginClasspath().map(p => s"-Xplugin:${p.path}")),
                  extraScalaCliOptions(),
                  // "--strict-bloop-json-check=false", // don't check Bloop JSON files at each run
                  workspace,
                  sourceFiles
                )

                val compile = proc.call()
                val out     = compile.out.trim()

                os.Path(out.split(File.pathSeparator).head)
              }

            CompilationResult(out / "unused.txt", PathRef(classFilesDir))
          }
      }
    else
      super.compile

}
