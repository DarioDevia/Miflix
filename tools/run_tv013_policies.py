"""TV 0.1.12 regression plus TV 0.1.13 pure policies. Not an Android/Gradle build.
Supply the existing Kotlin/JUnit jars plus Gson 2.10.1 and coroutines-core-jvm 1.7.3.
Android I/O and Media3 observer tests are run separately by the Gradle CI job.
"""
import run_tv012_policies as runner
runner.POLICIES += ['TvPersonalLibrary','TvWatchCoverage','TvContinueWatching','TvHomeDiscovery',
                    'TvFeatured','EpisodeNavigation','CatalogSearch']
runner.TESTS += ['TvPersonalLibrary','TvWatchCoverage','TvLibraryContinueWatching','TvHomeDiscovery']
if __name__=='__main__': runner.main()
