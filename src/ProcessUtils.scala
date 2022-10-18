package scala.cli.mill

import mill.main.client.InputPumper
import os.SubProcess
import java.io.PipedInputStream

// Adapted from Mill Jvm.scala https://github.com/com-lihaoyi/mill/blob/f96162ecb41a9dfbac0bc524b77e09093fd61029/main/src/mill/modules/Jvm.scala#L37
// Changes:
//   - return Either[Unit, os.SubProcess] in runSubprocess instead of Unit and to receive os.Shellable* instead of Seq[String]
//   - receive os.Shellable* instead of Seq[String]
//   - avoid receiving env and cwd since we don't pass them
object ProcessUtils {
  /**
   * Runs a generic subprocess and waits for it to terminate.
   */
  def runSubprocess(command: os.Shellable*): Either[Unit, os.SubProcess] = {
    val process = spawnSubprocess(command)
    val shutdownHook = new Thread("subprocess-shutdown") {
      override def run(): Unit = {
        System.err.println("Host JVM shutdown. Forcefully destroying subprocess ...")
        process.destroy()
      }
    }
    Runtime.getRuntime().addShutdownHook(shutdownHook)
    try {
      process.waitFor()
    } catch {
      case e: InterruptedException =>
        System.err.println("Interrupted. Forcefully destroying subprocess ...")
        process.destroy()
        // rethrow
        throw e
    } finally {
      Runtime.getRuntime().removeShutdownHook(shutdownHook)
    }
    if (process.exitCode() == 0) Right(process)
    else Left(())
  }

  /**
   * Spawns a generic subprocess, streaming the stdout and stderr to the
   * console. If the System.out/System.err have been substituted, makes sure
   * that the subprocess's stdout and stderr streams go to the subtituted
   * streams
   */
  def spawnSubprocess(
      command: os.Shellable*
  ): SubProcess = {
    // If System.in is fake, then we pump output manually rather than relying
    // on `os.Inherit`. That is because `os.Inherit` does not follow changes
    // to System.in/System.out/System.err, so the subprocess's streams get sent
    // to the parent process's origin outputs even if we want to direct them
    // elsewhere
    if (System.in.isInstanceOf[PipedInputStream]) {
      val process = os.proc(command).spawn(
        stdin = os.Pipe,
        stdout = os.Pipe,
        stderr = os.Pipe
      )

      val sources = Seq(
        (process.stdout, System.out, "spawnSubprocess.stdout", false, () => true),
        (process.stderr, System.err, "spawnSubprocess.stderr", false, () => true),
        (System.in, process.stdin, "spawnSubprocess.stdin", true, () => process.isAlive())
      )

      for ((std, dest, name, checkAvailable, runningCheck) <- sources) {
        val t = new Thread(
          new InputPumper(std, dest, checkAvailable, () => runningCheck()),
          name
        )
        t.setDaemon(true)
        t.start()
      }

      process
    } else {
      os.proc(command).spawn(
        stdin = os.Inherit,
        stdout = os.Inherit,
        stderr = os.Inherit
      )
    }
  }

}
