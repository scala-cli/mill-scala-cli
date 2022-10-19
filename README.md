# mill-scala-cli

[![Build status](https://github.com/scala-cli/mill-scala-cli/workflows/CI/badge.svg)](https://github.com/scala-cli/mill-scala-cli/actions?query=workflow%3ACI)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.alexarchambault.mill/mill-scala-cli_mill0.10_2.13.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.alexarchambault.mill/mill-scala-cli_mill0.10_2.13)

*mill-scala-cli* is a Mill plugin, that allows to compile Scala modules
with [Scala CLI](https://github.com/VirtusLab/scala-cli) rather than with Mill's
[`ZincWorker`](https://github.com/com-lihaoyi/mill/blob/4d94945c463b4f4b2aac3d74e0d75511714e00f0/scalalib/src/ZincWorkerModule.scala).

*mill-scala-cli* was used in [Scala CLI](https://github.com/VirtusLab/scala-cli)'s own build. The motivation for writing it originated
from incremental compilation issues seen with Mill's `ZincWorker` in the Scala CLI build *at the time*. Note that these issues now [seem
to be addressed](https://github.com/com-lihaoyi/mill/issues/2003).

When using *mill-scala-cli*, compilation is delegated to
Scala CLI, which delegates it to Bloop, who might be using Zinc slightly differently. Incremental compilation issues
went away with *mill-scala-cli* at the time. (Later on, Mill >= 0.10.7 didn't suffer from these issues, so Scala CLI stopped using mill-scala-cli.)

## Usage

Add a dependency towards *mill-scala-cli* in your `build.sc` file (or any Mill build file you want to use `ScalaCliCompile` from),
and import `ScalaCliCompile`:
```scala
import $ivy.`io.github.alexarchambault.mill::mill-scala-cli::0.1.2`
import scala.cli.mill.ScalaCliCompile
```

Note that mill-scala-cli only supports Mill 0.10.x.

Then have your Scala modules extend `ScalaCliCompile`:
```scala
object foo extends SbtModule with ScalaCliCompile {
  def scalaVersion = "2.13.8"
}
```

`ScalaCliCompile` can be added to modules extending `mill.scalalib.ScalaModule`, which includes `SbtModule`, `CrossScalaModule`, `CrossSbtModule`, …

Note that Scala CLI is disabled by default on CIs, where incremental compilation is somewhat less sollicited.

Note also that Scala CLI is only enabled on CPUs / OSes that have native Scala CLI launchers (as of writing this, Linux / Windows / macOS on x86_64).

## Customization

### Change Scala CLI version

```scala
def scalaCliVersion = "0.1.6"
```

### Pass custom options to Scala CLI

Add them to `extraScalaCliOptions`:
```scala
def extraScalaCliOptions = T {
  super.extraScalaCliOptions() ++ Seq("-v", "-v")
}
```

### Force Scala CLI use

```scala
def enableScalaCli = true
```

By default, this is false on CIs and on unsupported CPUs / OSes (see above).

### Change the URL Scala CLI is downloaded from

```scala
def compileScalaCliUrl = Some("https://…/scala-cli-x86_64-pc-linux.gz")
```

Both compressed and non-compressed Scala CLI launchers are accepted (compressed launchers should have the right compression method extension).
Decompression is handled by the ArchiveCache capabilities of coursier.

## Benefits / drawbacks / limitations

Benefits:
- under-the-hood, relies on a different codebase interfacing with Zinc, which can address issues you might see in Mill (or it might suffer from different issues!)

Drawbacks:
- no-op incremental compilation (when no sources changed, and nothing new needs to be compiled) has a small but noticeable cost - it takes a small amount of time (maybe in the ~100s of ms), which adds up when running Mill tasks involving numerous modules

Limitations:
- even though *mill-scala-cli* uses Bloop under-the-hood, the dependencies between modules compiled via *mill-scala-cli* are not "seen" by Bloop - each module lives in its own workspace, and dependencies between modules simply consist in putting a module byte code directory in the class path of its dependees, which trumps some Bloop optimizations

## Authors

Compilation via Scala CLI from Mill was originally added in the Scala CLI build by [Krzysztof Romanowski](https://github.com/romanowski). It was
later customized, then extracted and moved to the repository here by [Alex Archambault](https://github.com/alexarchambault).
