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
package anystream.service.stream

import anystream.db.MediaDao
import anystream.db.MediaReferencesDao
import anystream.db.PlaybackStatesDao
import anystream.db.UsersDao
import anystream.db.model.MediaDb
import anystream.db.model.PlaybackStateDb
import anystream.db.model.UserDb
import anystream.models.*
import kotlinx.datetime.Clock
import org.jdbi.v3.core.JdbiException
import org.slf4j.LoggerFactory

class StreamServiceQueriesJdbi(
    private val usersDao: UsersDao,
    private val mediaDao: MediaDao,
    private val mediaRefDao: MediaReferencesDao,
    private val playbackStatesDao: PlaybackStatesDao,
) : StreamServiceQueries {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun fetchUsersByIds(ids: List<Int>): List<User> {
        if (ids.isEmpty()) return emptyList()
        return try {
            usersDao.findByIds(ids).map(UserDb::toUserModel)
        } catch (e: JdbiException) {
            logger.error("Failed to load Users ids=$ids", e)
            emptyList()
        }
    }

    override suspend fun fetchPlaybackStatesByIds(ids: List<String>): List<PlaybackState> {
        if (ids.isEmpty()) return emptyList()
        return try {
            playbackStatesDao.findByGids(ids).map { it.toStateModel() }
        } catch (e: JdbiException) {
            logger.error("Failed to load PlaybackStates ids=${ids.joinToString()}", e)
            emptyList()
        }
    }

    override suspend fun fetchMovieById(id: String): Movie? {
        return try {
            mediaDao.findByGidAndType(id, MediaDb.Type.MOVIE)?.toMovieModel()
        } catch (e: JdbiException) {
            logger.error("Failed to load Movie id='$id'", e)
            null
        }
    }

    override suspend fun fetchEpisodeById(id: String): Pair<Episode, TvShow>? {
        return try {
            val dbEpisode = mediaDao.findByGidAndType(id, MediaDb.Type.TV_EPISODE)
            return if (dbEpisode == null) {
                null
            } else {
                val tvShowId = checkNotNull(dbEpisode.rootId)
                val tvShow = checkNotNull(mediaDao.findById(tvShowId)).toTvShowModel()
                Pair(dbEpisode.toTvEpisodeModel(), tvShow)
            }
        } catch (e: JdbiException) {
            logger.error("Failed to load Movie id='$id'", e)
            null
        }
    }

    override fun fetchMediaRef(mediaRefId: String): MediaReference? {
        return try {
            mediaRefDao.findByGid(mediaRefId)?.toMediaRefModel()
        } catch (e: JdbiException) {
            logger.error("Failed to find MediaReference '$mediaRefId'")
            null
        }
    }

    override suspend fun fetchPlaybackState(mediaRefId: String, userId: Int): PlaybackState? {
        return try {
            playbackStatesDao.findByUserIdAndMediaRefGid(userId, mediaRefId)?.toStateModel()
        } catch (e: JdbiException) {
            logger.error("Failed to load PlaybackState, mediaRefId='$mediaRefId' userId='$userId'")
            null
        }
    }

    override suspend fun insertPlaybackState(playbackState: PlaybackState): Boolean {
        return try {
            playbackStatesDao.insertState(PlaybackStateDb.from(playbackState), Clock.System.now())
            true
        } catch (e: JdbiException) {
            logger.error("Failed to update PlaybackState, id='${playbackState.id}'", e)
            false
        }
    }

    override suspend fun updatePlaybackState(stateId: String, position: Double): Boolean {
        return try {
            playbackStatesDao.updatePosition(stateId, position, Clock.System.now())
            true
        } catch (e: JdbiException) {
            logger.error("Failed to update PlaybackState position", e)
            false
        }
    }

    override suspend fun deletePlaybackState(playbackStateId: String): Boolean {
        return try {
            playbackStatesDao.deleteByGid(playbackStateId)
            true
        } catch (e: JdbiException) {
            logger.error("Failed to delete PlaybackState, id='$playbackStateId'", e)
            false
        }
    }
}
