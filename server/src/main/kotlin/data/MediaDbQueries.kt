/**
 * AnyStream
 * Copyright (C) 2021 Drew Carlson
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
package anystream.data

import anystream.models.*
import anystream.models.api.EpisodeResponse
import anystream.models.api.MovieResponse
import anystream.models.api.SeasonResponse
import anystream.models.api.TvShowResponse
import com.mongodb.MongoException
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.CoroutineDatabase

class MediaDbQueries(
    mongodb: CoroutineDatabase
) {

    private val moviesDb = mongodb.getCollection<Movie>()
    private val tvShowDb = mongodb.getCollection<TvShow>()
    private val episodeDb = mongodb.getCollection<Episode>()
    private val mediaRefsDb = mongodb.getCollection<MediaReference>()
    private val playbackStatesDb = mongodb.getCollection<PlaybackState>()

    suspend fun createIndexes() {
        try {
            moviesDb.ensureIndex(Movie::title.textIndex())
            moviesDb.ensureIndex(Movie::tmdbId)
            tvShowDb.ensureIndex(TvShow::name.textIndex())
            tvShowDb.ensureIndex(TvShow::tmdbId)
            episodeDb.ensureIndex(Episode::name.textIndex())
            episodeDb.ensureIndex(Episode::showId)
            mediaRefsDb.ensureIndex(MediaReference::contentId)
            playbackStatesDb.ensureIndex(PlaybackState::userId)
        } catch (e: MongoException) {
            println("Failed to create search indexes")
            e.printStackTrace()
        }
    }

    suspend fun findMovieAndMediaRefs(movieId: String): MovieResponse? {
        val movie = moviesDb.findOneById(movieId) ?: return null
        val mediaRefs = mediaRefsDb.find(MediaReference::contentId eq movieId).toList()

        return MovieResponse(
            movie = movie,
            mediaRefs = mediaRefs
        )
    }

    suspend fun findShowAndMediaRefs(showId: String): TvShowResponse? {
        val show = tvShowDb.findOneById(showId) ?: return null
        val seasonIds = show.seasons.map(TvSeason::id)
        val searchList = seasonIds + showId
        val mediaRefs = mediaRefsDb.find(
            or(
                MediaReference::rootContentId `in` searchList,
                MediaReference::contentId `in` seasonIds + showId,
            )
        ).toList()
        return TvShowResponse(
            tvShow = show,
            mediaRefs = mediaRefs,
        )
    }

    suspend fun findSeasonAndMediaRefs(seasonId: String): SeasonResponse? {
        val tvShow = tvShowDb.findOne(TvShow::seasons elemMatch (TvSeason::id eq seasonId))
            ?: return null
        val season = tvShow.seasons.find { it.id == seasonId }
            ?: return null
        val episodes = episodeDb
            .find(
                and(
                    Episode::showId eq tvShow.id,
                    Episode::seasonNumber eq season.seasonNumber,
                )
            )
            .toList()
        val episodeIds = episodes.map(Episode::id)
        val mediaRefs = mediaRefsDb
            .find(MediaReference::contentId `in` episodeIds)
            .toList()
        return SeasonResponse(
            show = tvShow,
            season = season,
            episodes = episodes,
            mediaRefs = mediaRefs.associateBy(MediaReference::contentId)
        )
    }

    suspend fun findEpisodeAndMediaRefs(episodeId: String): EpisodeResponse? {
        val episode = episodeDb.findOneById(episodeId) ?: return null
        val show = tvShowDb.findOneById(episode.showId) ?: return null
        val mediaRefs = mediaRefsDb
            .find(MediaReference::contentId eq episode.id)
            .toList()
        return EpisodeResponse(
            episode = episode,
            show = show,
            mediaRefs = mediaRefs,
        )
    }

    suspend fun findCurrentlyWatching(userId: String, limit: Int): CurrentlyWatchingQueryResults {
        val allPlaybackStates = playbackStatesDb
            .find(PlaybackState::userId eq userId)
            .sort(descending(PlaybackState::updatedAt))
            .limit(limit)
            .toList()

        val playbackMediaIds = allPlaybackStates.map(PlaybackState::mediaId)

        val playbackStateMovies = moviesDb
            .find(Movie::id `in` playbackMediaIds)
            .toList()
            .associateBy { movie ->
                allPlaybackStates.first { it.mediaId == movie.id }.id
            }

        val playbackStateEpisodes = episodeDb
            .find(Episode::id `in` playbackMediaIds)
            .toList()
            .distinctBy(Episode::showId)

        val playbackStateTv = tvShowDb
            .find(TvShow::id `in` playbackStateEpisodes.map(Episode::showId))
            .toList()
            .associateBy { show ->
                playbackStateEpisodes.first { it.showId == show.id }
            }
            .toList()
            .associateBy { (episode, _) ->
                allPlaybackStates.first { it.mediaId == episode.id }.id
            }

        val playbackStates = allPlaybackStates
            .filter { state ->
                playbackStateEpisodes.any { it.id == state.mediaId } ||
                        playbackStateMovies.any { (_, movie) -> movie.id == state.mediaId }
            }

        return CurrentlyWatchingQueryResults(
            playbackStates = playbackStates,
            currentlyWatchingMovies = playbackStateMovies,
            currentlyWatchingTv = playbackStateTv,
        )
    }

    suspend fun findRecentlyAddedMovies(limit: Int): Map<Movie, MediaReference?> {
        val recentlyAddedMovies = moviesDb
            .find()
            .sort(descending(Movie::added))
            .limit(limit)
            .toList()
        val recentlyAddedRefs = mediaRefsDb
            .find(MediaReference::contentId `in` recentlyAddedMovies.map(Movie::id))
            .toList()
        return recentlyAddedMovies.associateWith { movie ->
            recentlyAddedRefs.find { it.contentId == movie.id }
        }
    }

    suspend fun findRecentlyAddedTv(limit: Int): List<TvShow> {
        return tvShowDb
            .find()
            .sort(descending(TvShow::added))
            .limit(limit)
            .toList()
    }

    data class CurrentlyWatchingQueryResults(
        val playbackStates: List<PlaybackState>,
        val currentlyWatchingMovies: Map<String, Movie>,
        val currentlyWatchingTv: Map<String, Pair<Episode, TvShow>>,
    )
}