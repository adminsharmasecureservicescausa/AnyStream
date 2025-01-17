/**
 * AnyStream
 * Copyright (C) 2021 AnyStream Maintainers
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package anystream.routes

import anystream.AnyStreamConfig
import anystream.data.MediaDbQueries
import anystream.db.*
import anystream.db.extensions.pooled
import anystream.media.MediaImporter
import anystream.media.processor.MovieImportProcessor
import anystream.media.processor.TvImportProcessor
import anystream.metadata.MetadataManager
import anystream.metadata.MetadataProvider
import anystream.metadata.providers.TmdbMetadataProvider
import anystream.models.Permission
import anystream.service.search.SearchService
import anystream.service.stream.StreamService
import anystream.service.stream.StreamServiceQueriesJdbi
import anystream.service.user.UserService
import anystream.service.user.UserServiceQueriesJdbi
import app.moviebase.tmdb.Tmdb3
import com.github.kokorin.jaffree.ffmpeg.FFmpeg
import com.github.kokorin.jaffree.ffprobe.FFprobe
import drewcarlson.qbittorrent.QBittorrentClient
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kjob.core.job.JobExecutionType
import kjob.core.kjob
import kjob.jdbi.JdbiKJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.drewcarlson.ktor.permissions.withAnyPermission
import org.drewcarlson.ktor.permissions.withPermission
import org.jdbi.v3.core.Jdbi
import torrentsearch.TorrentSearch
import java.nio.file.Path

fun Application.installRouting(jdbi: Jdbi, config: AnyStreamConfig) {
    val kjob = kjob(JdbiKJob) {
        this.jdbi = jdbi
        defaultJobExecutor = JobExecutionType.NON_BLOCKING
    }.start()
    environment.monitor.subscribe(ApplicationStopped) { kjob.shutdown() }
    val tmdb by lazy { Tmdb3(config.tmdbApiKey) }

    val torrentSearch = TorrentSearch()

    val qbClient = QBittorrentClient(
        baseUrl = config.qbittorrentUrl,
        username = config.qbittorrentUser,
        password = config.qbittorrentPass,
    )
    val ffmpeg = { FFmpeg.atPath(Path.of(config.ffmpegPath)) }
    val ffprobe = { FFprobe.atPath(Path.of(config.ffmpegPath)) }

    val mediaDao = jdbi.pooled<MediaDao>()
    val tagsDao = jdbi.pooled<TagsDao>()
    val playbackStatesDao = jdbi.pooled<PlaybackStatesDao>()
    val mediaReferencesDao = jdbi.pooled<MediaReferencesDao>()
    val usersDao = jdbi.pooled<UsersDao>()
    val invitesDao = jdbi.pooled<InvitesDao>()
    val permissionsDao = jdbi.pooled<PermissionsDao>()
    val searchableContentDao = jdbi.pooled<SearchableContentDao>().apply { createTable() }

    val queries = MediaDbQueries(searchableContentDao, mediaDao, tagsDao, mediaReferencesDao, playbackStatesDao)

    val providers = listOf<MetadataProvider>(
        TmdbMetadataProvider(tmdb, queries)
    )
    val metadataManager = MetadataManager(providers, log)

    val importScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val processors = listOf(
        MovieImportProcessor(metadataManager, queries, log),
        TvImportProcessor(metadataManager, queries, importScope, log),
    )
    val importer = MediaImporter(ffprobe, processors, mediaReferencesDao, importScope, log)

    val userService = UserService(UserServiceQueriesJdbi(usersDao, permissionsDao, invitesDao))

    val streamQueries = StreamServiceQueriesJdbi(usersDao, mediaDao, mediaReferencesDao, playbackStatesDao)
    val streamService = StreamService(this, streamQueries, ffmpeg, ffprobe, config.transcodePath)
    val searchService = SearchService(log, searchableContentDao, mediaDao, mediaReferencesDao)

    installWebClientRoutes(config)

    routing {
        route("/api") {
            addUserRoutes(userService)
            authenticate {
                addHomeRoutes(tmdb, queries)
                withAnyPermission(Permission.ViewCollection) {
                    addImageRoutes(config.dataPath)
                    addTvShowRoutes(queries)
                    addMovieRoutes(queries)
                    addSearchRoutes(searchService)
                    addMediaViewRoutes(metadataManager, queries)
                }
                withAnyPermission(Permission.ManageTorrents) {
                    addTorrentRoutes(qbClient, mediaReferencesDao)
                }
                withAnyPermission(Permission.ManageCollection) {
                    addMediaManageRoutes(tmdb, torrentSearch, importer, queries)
                }
                withPermission(Permission.ConfigureSystem) {
                    addAdminRoutes()
                }
            }

            addStreamRoutes(streamService)
            addStreamWsRoutes(streamService)
            addTorrentWsRoutes(qbClient)
            addUserWsRoutes(userService)
            addAdminWsRoutes()
        }
    }
}
