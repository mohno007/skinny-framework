resolvers += Classpaths.sbtPluginReleases
resolvers += "sonatype releases" at "https://oss.sonatype.org/content/repositories/releases"
// https://github.com/sbt/sbt/issues/2217
fullResolvers ~= { _.filterNot(_.name == "jcenter") }

addSbtPlugin("io.get-coursier"      % "sbt-coursier"            % "1.0.0-RC6")
addSbtPlugin("org.scalatra.scalate" % "sbt-scalate-precompiler" % "1.8.0.1")
addSbtPlugin("org.skinny-framework" % "sbt-servlet-plugin"      % "2.1.5")
addSbtPlugin("com.lucidchart"       % "sbt-scalafmt"            % "1.8")
addSbtPlugin("com.jsuereth"         % "sbt-pgp"                 % "1.0.0")
addSbtPlugin("net.virtual-void"     % "sbt-dependency-graph"    % "0.8.2")
addSbtPlugin("com.timushev.sbt"     % "sbt-updates"             % "0.3.0")

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")
