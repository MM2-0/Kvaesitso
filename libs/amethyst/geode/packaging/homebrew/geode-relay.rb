# Reference Homebrew formula for geode, the standalone Nostr relay.
#
# Submit this to Homebrew/homebrew-core (new-formula PR) or drop it into a
# personal tap (`Formula/geode-relay.rb`) for an instant
# `brew install <tap>/geode-relay`.
#
# The url + sha256 below are kept in sync automatically on every stable release
# by .github/workflows/bump-homebrew-geode-formula.yml (it downloads the
# published `geode-<version>-jvm.tar.gz`, recomputes the sha256, and opens a
# PR). To refresh by hand instead:
#   curl -fsSL -o geode-jvm.tar.gz \
#     https://github.com/vitorpamplona/amethyst/releases/download/vX.Y.Z/geode-X.Y.Z-jvm.tar.gz
#   shasum -a 256 geode-jvm.tar.gz
#
# Why a pre-built jar bundle instead of building from source:
#   homebrew-core builds inside a network sandbox, so a Gradle build cannot
#   resolve its Maven dependencies there. The accepted pattern for JVM tools is
#   to download a pre-built, no-JRE jar bundle and depend on the system openjdk.
#   We publish exactly that as `geode-<version>-jvm.tar.gz` (bin/geode +
#   lib/*.jar, no bundled runtime) from .github/workflows/create-release.yml.
#
# Note: geode is a long-running relay daemon. Homebrew is a convenient way to
# INSTALL it on a workstation for local testing; for a production deployment
# prefer the Docker image or the .deb/.rpm + systemd unit (see geode/README.md).
#
# The homebrew-core token is `geode-relay`, NOT `geode`. That name is
# permanently reserved: homebrew-core's `formula_renames.json` maps
# "geode" -> "apache-geode" (Apache Geode's old name), so `brew info --formula
# geode` resolves to that package and a new formula could never claim it.
#
# The BINARY is still `geode` — users type `geode`, not `geode-relay`. That is
# safe: apache-geode installs `gfsh`, not `geode`, so there is no collision on
# PATH. Formula token and binary name differ here deliberately.
#
# As with amy.rb, do NOT strip the `livecheck` block or the comments to match
# the amethyst-nostr cask — homebrew-core conventions differ from
# homebrew-cask's, and `livecheck` is what drives automated version bumps.
class GeodeRelay < Formula
  desc "Standalone Nostr relay from the Amethyst project"
  homepage "https://github.com/vitorpamplona/amethyst"
  url "https://github.com/vitorpamplona/amethyst/releases/download/v1.21.0/geode-1.21.0-jvm.tar.gz"
  sha256 "dc6c397d30d5477cdae2e0c59ae9116b17514f94c02c0c04fa701699e7896c83"
  license "MIT"

  # Lets homebrew-core's BrewTestBot auto-open version-bump PRs when a new
  # stable GitHub release appears.
  livecheck do
    url :stable
    strategy :github_latest
  end

  depends_on "openjdk"

  def install
    # Tarball top level is bin/ and lib/ (the Gradle installDist layout).
    libexec.install Dir["*"]
    # Wrapper on PATH that pins JAVA_HOME to Homebrew's openjdk so geode runs
    # regardless of the user's own Java setup.
    (bin/"geode").write_env_script libexec/"bin/geode", JAVA_HOME: formula_opt_prefix("openjdk")
  end

  test do
    assert_match "geode", shell_output("#{bin}/geode --version 2>&1")
  end
end
