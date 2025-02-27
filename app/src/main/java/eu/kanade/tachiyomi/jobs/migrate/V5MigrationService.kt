package eu.kanade.tachiyomi.jobs.migrate

import android.content.Context
import android.net.Uri
import androidx.core.text.isDigitsOnly
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.model.isMergedChapter
import eu.kanade.tachiyomi.source.online.models.dto.LegacyIdDto
import eu.kanade.tachiyomi.source.online.utils.MdUtil
import eu.kanade.tachiyomi.util.storage.getUriCompat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * This class will perform migration of old mangaList ids to the new v5 mangadex.
 */
class V5MigrationService(
    private val db: DatabaseHelper = Injekt.get(),
    private val trackManager: TrackManager = Injekt.get(),
    private val networkHelper: NetworkHelper = Injekt.get(),
) {

    // List containing failed updates
    private val failedUpdatesMangaList = mutableMapOf<Manga, String?>()
    private val failedUpdatesChapters = mutableMapOf<Chapter, String?>()
    private val failedUpdatesErrors = mutableListOf<String>()

    private var totalManga: Int = 0
    private var actualMigrated: Int = 0

    /**
     * This will migrate the mangaList in the library to the new ids
     */
    suspend fun migrateLibraryToV5(
        context: Context,
        progressNotification: (String, Int, Int) -> Unit,
        completeNotificaton: (Int) -> Unit,
        errorNotification: (List<String>, Uri?) -> Unit,
    ) {
        val mangaList = db.getMangaList().executeAsBlocking()
        totalManga = mangaList.size
        mangaList.forEachIndexed { index, manga ->

            // Return if job was canceled
            /*  if (job?.isCancelled == true) {
                  totalManga--
                  return
              }*/

            // Update progress bar
            progressNotification(manga.title, index + 1, totalManga)

            // Get the old id and check if it is a number
            val oldMangaId = MdUtil.getMangaId(manga.url)
            val isNumericId = oldMangaId.isDigitsOnly()

            // Get the new id for this manga
            // We skip mangaList which have already been converted (non-numeric ids)
            var mangaErroredOut = false
            if (isNumericId) {
                val responseDto = networkHelper.service.legacyMapping(
                    LegacyIdDto(
                        type = "manga",
                        listOf(oldMangaId.toInt())
                    )
                )

                if (responseDto.isSuccessful) {
                    val newId = responseDto.body()!!.first().data.attributes.newId
                    manga.url = "/title/$newId"
                    manga.initialized = false
                    manga.thumbnail_url = null
                    db.insertManga(manga).executeAsBlocking()
                    val tracks = db.getTracks(manga).executeAsBlocking()
                    tracks.firstOrNull { it.sync_id == trackManager.mdList.id }?.let {
                        it.tracking_url = MdUtil.baseUrl + manga.url
                        db.insertTrack(it).executeAsBlocking()
                    }
                    actualMigrated++
                } else {
                    failedUpdatesMangaList[manga] = "unable to find new manga id"
                    failedUpdatesErrors.add(manga.title + ": unable to find new manga id, MangaDex might have removed this manga or the id changed")
                    mangaErroredOut = true
                }
            }

            // Now loop through the chapters for this manga and update them
            val chapters = db.getChapters(manga).executeAsBlocking()
            val chapterErrors = mutableListOf<String>()
            if (!mangaErroredOut) {
                val chapterMap = chapters.filter { it.isMergedChapter().not() }
                    .filter { it.mangadex_chapter_id.isDigitsOnly() }
                    .map { it.mangadex_chapter_id.toInt() to it }
                    .toMap()
                val chapterChunks = chapters.filter { it.isMergedChapter().not() }
                    .filter { it.mangadex_chapter_id.isDigitsOnly() }
                    .map { it.mangadex_chapter_id.toInt() }
                    .chunked(100)

                chapterChunks.asSequence().forEach { legacyIds ->
                    val responseDto =
                        networkHelper.service.legacyMapping(LegacyIdDto("chapter", legacyIds))
                    if (responseDto.isSuccessful) {
                        responseDto.body()!!.forEach { legacyMappingDto ->
                            /*   if (job?.isCancelled == true) {
                                   return
                               }*/
                            val oldId = legacyMappingDto.data.attributes.legacyId
                            val newId = legacyMappingDto.data.attributes.newId
                            val chapter = chapterMap[oldId]!!
                            chapter.mangadex_chapter_id = newId
                            chapter.url = MdUtil.chapterSuffix + newId
                            chapter.old_mangadex_id = oldId.toString()
                            db.insertChapter(chapter).executeAsBlocking()
                        }
                    } else {
                        legacyIds.forEach {
                            val failedChapter = chapterMap[it]!!
                            failedUpdatesChapters[failedChapter] =
                                "unable to find new chapter V5 id deleting chapter"
                            chapterErrors.add(
                                "\t- unable to find new chapter id for " +
                                    "vol ${failedChapter.vol} - ${failedChapter.chapter_number} - ${failedChapter.name}"
                            )
                            db.deleteChapter(failedChapter).executeAsBlocking()
                        }
                    }
                }

                // Append chapter errors if we have them
                if (chapterErrors.size > 0) {
                    failedUpdatesErrors.add(manga.title + ": has chapter conversion errors")
                    chapterErrors.forEach {
                        failedUpdatesErrors.add(it)
                    }
                }
            }
        }
        finishUpdates(context, errorNotification, completeNotificaton)
    }

    /**
     * Finall function called when we have finished / requested to stop the update
     */
    private fun finishUpdates(
        context: Context,
        errorNotification: (List<String>, Uri?) -> Unit,
        completeNotificaton: (Int) -> Unit,
    ) {
        if (failedUpdatesMangaList.isNotEmpty() || failedUpdatesChapters.isNotEmpty()) {
            val errorFile = writeErrorFile(context, failedUpdatesErrors)
            errorNotification(failedUpdatesMangaList.map { it.key.title } +
                failedUpdatesChapters.map { it.key.chapter_title }, errorFile.getUriCompat(context))
        }
        completeNotificaton(actualMigrated)
    }

    /**
     * Writes basic file of update errors to cache dir.
     */
    private fun writeErrorFile(context: Context, errors: MutableList<String>): File {
        try {
            if (errors.isNotEmpty()) {
                val destFile = File(context.externalCacheDir, "neko_v5_migration_errors.txt")
                destFile.bufferedWriter().use { out ->
                    errors.forEach { error ->
                        out.write("$error\n")
                    }
                }
                return destFile
            }
        } catch (e: Exception) {
            // Empty
        }
        return File("")
    }
}
