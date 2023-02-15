package eu.kanade.tachiyomi.source

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.DelegatedHttpSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.all.Cubari
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.source.online.english.KireiCake
import eu.kanade.tachiyomi.source.online.english.MangaPlus
import rx.Observable
import uy.kohesive.injekt.injectLazy

open class SourceManager(private val context: Context) {

    private val sourcesMap = mutableMapOf<Long, Source>()

    private val stubSourcesMap = mutableMapOf<Long, StubSource>()

    protected val extensionManager: ExtensionManager by injectLazy()

    private val delegatedSources = listOf(
        DelegatedSource(
            "reader.kireicake.com",
            5509224355268673176,
            KireiCake(),
        ),
        DelegatedSource(
            "mangadex.org",
            2499283573021220255,
            MangaDex(),
        ),
        DelegatedSource(
            "mangaplus.shueisha.co.jp",
            1998944621602463790,
            MangaPlus(),
        ),
        DelegatedSource(
            "cubari.moe",
            6338219619148105941,
            Cubari(),
        ),
    ).associateBy { it.sourceId }

    init {
        createInternalSources().forEach { registerSource(it) }
    }

    open fun get(sourceKey: Long): Source? {
        return sourcesMap[sourceKey]
    }

    fun getOrStub(sourceKey: Long): Source {
        return sourcesMap[sourceKey] ?: stubSourcesMap.getOrPut(sourceKey) {
            StubSource(sourceKey)
        }
    }

    fun isDelegatedSource(source: Source): Boolean {
        return delegatedSources.values.count { it.sourceId == source.id } > 0
    }

    fun getDelegatedSource(urlName: String): DelegatedHttpSource? {
        return delegatedSources.values.find { it.urlName == urlName }?.delegatedHttpSource
    }

    fun getOnlineSources() = sourcesMap.values.filterIsInstance<HttpSource>()

    fun getCatalogueSources() = sourcesMap.values.filterIsInstance<CatalogueSource>()

    internal fun registerSource(source: Source, overwrite: Boolean = false) {
        if (overwrite || !sourcesMap.containsKey(source.id)) {
            delegatedSources[source.id]?.delegatedHttpSource?.delegate = source as? HttpSource
            sourcesMap[source.id] = source
        }
    }

    internal fun unregisterSource(source: Source) {
        sourcesMap.remove(source.id)
    }

    private fun createInternalSources(): List<Source> = listOf(
        LocalSource(context),
    )

    @Suppress("OverridingDeprecatedMember")
    inner class StubSource(override val id: Long) : Source {

        override val name: String
            get() = extensionManager.getStubSource(id)?.name ?: id.toString()

        override suspend fun getMangaDetails(manga: SManga): SManga {
            throw getSourceNotInstalledException()
        }

        override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
            return Observable.error(getSourceNotInstalledException())
        }

        override suspend fun getChapterList(manga: SManga): List<SChapter> {
            throw getSourceNotInstalledException()
        }

        override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
            return Observable.error(getSourceNotInstalledException())
        }

        override suspend fun getPageList(chapter: SChapter): List<Page> {
            throw getSourceNotInstalledException()
        }

        override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
            return Observable.error(getSourceNotInstalledException())
        }

        override fun toString(): String {
            return name
        }

        fun getSourceNotInstalledException(): Exception {
            return SourceNotFoundException(
                context.getString(
                    R.string.source_not_installed_,
                    extensionManager.getStubSource(id)?.name ?: id.toString(),
                ),
                id,
            )
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as StubSource
            return id == other.id
        }
    }

    private data class DelegatedSource(
        val urlName: String,
        val sourceId: Long,
        val delegatedHttpSource: DelegatedHttpSource,
    )
}

class SourceNotFoundException(message: String, val id: Long) : Exception(message)
