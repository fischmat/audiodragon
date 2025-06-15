package de.matthiasfisch.audiodragon.library.peristence

import de.matthiasfisch.audiodragon.library.peristence.LibraryItemSortField.UPDATED_AT
import de.matthiasfisch.audiodragon.library.peristence.SortOrder.DESC
import mu.KotlinLogging
import org.flywaydb.core.Flyway
import org.jooq.Configuration
import org.jooq.Field
import org.jooq.Record
import org.jooq.SortField
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType.*
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.DriverManager
import java.sql.Timestamp
import javax.imageio.ImageIO
import kotlin.io.path.absolutePathString
import kotlin.time.Duration.Companion.milliseconds

private val LOGGER = KotlinLogging.logger {}

class LibraryRepository(dbFilePath: Path) {
    private val jdbcUrl = "jdbc:sqlite:${dbFilePath.absolutePathString()}"

    init {
        System.setProperty("org.jooq.no-logo", "true")
        val flyway = Flyway.configure().dataSource(jdbcUrl, "", "").load()
        LOGGER.info { "Applying database migrations" }
        flyway.migrate()
        LOGGER.info { "Done applying database migrations" }
    }

    fun upsertItem(item: LibraryItem) = txn { ctx ->
        upsertItem(ctx, item)
    }

    fun getItem(filePath: Path) = txn { ctx ->
        val pathString = filePath.absolutePathString()
        val itemRow = DSL.using(ctx)
            .selectFrom(Tables.LibraryItems.table)
            .where(Tables.LibraryItems.FILE_PATH_FIELD.eq(pathString))
            .fetchOne()
            ?: return@txn null

        recordToLibraryItem(itemRow, getGenres(ctx, filePath), getLabels(ctx, filePath))
    }

    fun getItems(
        search: String? = null,
        titleSearch: String? = null,
        artistSearch: String? = null,
        albumSearch: String? = null,
        genres: List<String>? = null,
        page: Int? = null,
        pageSize: Int = 10,
        sortBy: LibraryItemSortField = UPDATED_AT,
        sortOrder: SortOrder = DESC
    ): List<LibraryItem> = txn { ctx ->
        val searchLikeExp = "%${search?.lowercase()}%"
        val conditions = listOfNotNull(
            search?.let {
                DSL.lower(Tables.LibraryItems.TITLE_FIELD).like(searchLikeExp, '\\')
                    .or(
                        DSL.lower(Tables.LibraryItems.ARTIST_FIELD).like(searchLikeExp, '\\')
                            .or(DSL.lower(Tables.LibraryItems.ALBUM_FIELD).like(searchLikeExp, '\\'))
                    )
            },
            titleSearch?.let { DSL.lower(Tables.LibraryItems.TITLE_FIELD).like("%$it%", '\\') },
            artistSearch?.let { DSL.lower(Tables.LibraryItems.ARTIST_FIELD).like("%$it%", '\\') },
            albumSearch?.let { DSL.lower(Tables.LibraryItems.ALBUM_FIELD).like("%$it%", '\\') },
            genres?.map { it.lowercase() }?.let { DSL.lower(Tables.Genres.NAME_FIELD).`in`(it) }
        )

        DSL.using(ctx)
            .selectDistinct(Tables.LibraryItems.allFields)
            .from(Tables.LibraryItems.table)
            .leftJoin(Tables.Genres.table).on(Tables.LibraryItems.FILE_PATH_FIELD.eq(Tables.Genres.PATH_FIELD))
            .where(conditions)
            .orderBy(sortOrder.jooqOrder(sortBy.tableField), Tables.LibraryItems.UPDATED_AT_FIELD.desc())
            .let {
                if (page != null) {
                    it.offset(pageSize * (page - 1)).limit(pageSize)
                } else {
                    it
                }
            }
            .fetch()
            .map {
                val path = Paths.get(it.get(Tables.LibraryItems.FILE_PATH_FIELD))
                recordToLibraryItem(it, getGenres(ctx, path), getLabels(ctx, path))
            }
    }

    fun replaceAllItems(items: List<LibraryItem>) = txn { ctx ->
        DSL.using(ctx)
            .truncateTable(Tables.LibraryItems.table)
            .execute()
        items.forEach { upsertItem(ctx, it) }
    }

    fun deleteItem(filePath: Path) = txn { ctx ->
        DSL.using(ctx)
            .deleteFrom(Tables.LibraryItems.table)
            .where(Tables.LibraryItems.FILE_PATH_FIELD.eq(filePath.absolutePathString()))
            .execute() > 0
    }

    private fun upsertItem(ctx: Configuration, item: LibraryItem) {
        DSL.using(ctx)
            .insertInto(Tables.LibraryItems.table)
            .columns(
                Tables.LibraryItems.FILE_PATH_FIELD,
                Tables.LibraryItems.ADDED_AT_FIELD,
                Tables.LibraryItems.UPDATED_AT_FIELD,
                Tables.LibraryItems.TITLE_FIELD,
                Tables.LibraryItems.ARTIST_FIELD,
                Tables.LibraryItems.ALBUM_FIELD,
                Tables.LibraryItems.RELEASE_YEAR_FIELD,
                Tables.LibraryItems.FRONT_COVER_FIELD,
                Tables.LibraryItems.BACK_COVER_FIELD,
                Tables.LibraryItems.LYRICS_FIELD,
                Tables.LibraryItems.LENGTH_FIELD
            )
            .values(
                item.filePath.absolutePathString(),
                Timestamp(item.addedAt?.toEpochMilli() ?: item.filePath.toFile().lastModified()),
                Timestamp(item.updatedAt?.toEpochMilli() ?: item.filePath.toFile().lastModified()),
                item.title,
                item.artist,
                item.album,
                item.releaseYear,
                item.frontCoverart.value?.let { imageBytes(it) },
                item.backCoverart.value?.let { imageBytes(it) },
                item.lyrics?.joinToString("\n"),
                item.length?.inWholeMilliseconds?.toInt()
            )
            .onConflict(Tables.LibraryItems.FILE_PATH_FIELD)
            .doUpdate()
            .set(
                mapOf(
                    Tables.LibraryItems.UPDATED_AT_FIELD to Timestamp(item.filePath.toFile().lastModified()),
                    Tables.LibraryItems.TITLE_FIELD to item.title,
                    Tables.LibraryItems.ARTIST_FIELD to item.artist,
                    Tables.LibraryItems.ALBUM_FIELD to item.album,
                    Tables.LibraryItems.RELEASE_YEAR_FIELD to item.releaseYear,
                    Tables.LibraryItems.FRONT_COVER_FIELD to item.frontCoverart.value?.let { imageBytes(it) },
                    Tables.LibraryItems.BACK_COVER_FIELD to item.backCoverart.value?.let { imageBytes(it) },
                    Tables.LibraryItems.LYRICS_FIELD to item.lyrics?.joinToString("\n"),
                    Tables.LibraryItems.LENGTH_FIELD to item.length?.inWholeMilliseconds?.toInt()
                )
            )
            .execute()

        DSL.using(ctx)
            .deleteFrom(Tables.Genres.table)
            .where(Tables.Genres.PATH_FIELD.eq(item.filePath.absolutePathString()))
            .execute()
        DSL.using(ctx)
            .deleteFrom(Tables.Labels.table)
            .where(Tables.Labels.PATH_FIELD.eq(item.filePath.absolutePathString()))
            .execute()

        item.genres.forEach {
            DSL.using(ctx)
                .insertInto(Tables.Genres.table)
                .columns(
                    Tables.Genres.PATH_FIELD,
                    Tables.Genres.NAME_FIELD
                )
                .values(item.filePath.absolutePathString(), it)
                .execute()
        }

        item.labels.forEach {
            DSL.using(ctx)
                .insertInto(Tables.Labels.table)
                .columns(
                    Tables.Labels.PATH_FIELD,
                    Tables.Labels.NAME_FIELD
                )
                .values(item.filePath.absolutePathString(), it)
                .execute()
        }
    }

    private fun getGenres(ctx: Configuration, path: Path) = txn {
        DSL.using(ctx)
            .selectFrom(Tables.Genres.table)
            .where(Tables.Genres.PATH_FIELD.eq(path.absolutePathString()))
            .fetch()
            .toList()
            .map {
                it.get(Tables.Genres.NAME_FIELD)
            }
    }

    private fun getLabels(ctx: Configuration, path: Path) = txn {
        DSL.using(ctx)
            .selectFrom(Tables.Labels.table)
            .where(Tables.Labels.PATH_FIELD.eq(path.absolutePathString()))
            .fetch()
            .toList()
            .map {
                it.get(Tables.Labels.NAME_FIELD)
            }
    }

    private fun recordToLibraryItem(record: Record, genres: List<String>, labels: List<String>): LibraryItem {
        val filePath = Paths.get(record.get(Tables.LibraryItems.FILE_PATH_FIELD))

        return LibraryItem(
            filePath,
            record.get(Tables.LibraryItems.ADDED_AT_FIELD).toInstant(),
            record.get(Tables.LibraryItems.UPDATED_AT_FIELD).toInstant(),
            record.get(Tables.LibraryItems.TITLE_FIELD),
            record.get(Tables.LibraryItems.ARTIST_FIELD),
            record.get(Tables.LibraryItems.ALBUM_FIELD),
            genres,
            labels,
            record.get(Tables.LibraryItems.RELEASE_YEAR_FIELD),
            imageDataLoader(filePath, Tables.LibraryItems.FRONT_COVER_FIELD),
            imageDataLoader(filePath, Tables.LibraryItems.BACK_COVER_FIELD),
            record.get(Tables.LibraryItems.LYRICS_FIELD)?.split("\n"),
            record.get(Tables.LibraryItems.LENGTH_FIELD)?.milliseconds,
        )
    }

    private fun imageDataLoader(filePath: Path, field: Field<ByteArray>) = lazy {
        txn { ctx ->
            DSL.using(ctx)
                .select(field)
                .from(Tables.LibraryItems.table)
                .where(Tables.LibraryItems.FILE_PATH_FIELD.eq(filePath.absolutePathString()))
                .fetchOne()
                ?.get(field)
                ?.let { buffer ->
                    ByteArrayInputStream(buffer).use {
                        ImageIO.read(it)
                    }
                }
        }
    }

    private fun imageBytes(image: BufferedImage): ByteArray {
        return ByteArrayOutputStream().use {
            ImageIO.write(image, "png", it)
            it.toByteArray()
        }
    }

    private fun <T> txn(action: (Configuration) -> T): T =
        DriverManager.getConnection(jdbcUrl, "", "").use {
            DSL.using(it).transactionResult(action)
        }
}

enum class LibraryItemSortField(val tableField: Field<*>) {
    FILE_PATH(Tables.LibraryItems.FILE_PATH_FIELD),
    ADDED_AT(Tables.LibraryItems.ADDED_AT_FIELD),
    UPDATED_AT(Tables.LibraryItems.UPDATED_AT_FIELD),
    TITLE(Tables.LibraryItems.TITLE_FIELD),
    ARTIST(Tables.LibraryItems.ARTIST_FIELD),
    ALBUM(Tables.LibraryItems.ALBUM_FIELD),
    LENGTH(Tables.LibraryItems.LENGTH_FIELD)
}

enum class SortOrder(val jooqOrder: (Field<*>) -> SortField<*>) {
    ASC({ it.asc() }), DESC({ it.desc() })
}
private object Tables {
    object LibraryItems {
        val table = DSL.table("LibraryItems")

        val FILE_PATH_FIELD = DSL.field("filePath", CLOB)
        val ADDED_AT_FIELD = DSL.field("addedAt", TIMESTAMP)
        val UPDATED_AT_FIELD = DSL.field("updatedAt", TIMESTAMP)
        val TITLE_FIELD = DSL.field("title", CLOB)
        val ARTIST_FIELD = DSL.field("artist", CLOB)
        val ALBUM_FIELD = DSL.field("album", CLOB)
        val RELEASE_YEAR_FIELD = DSL.field("releaseYear", CLOB)
        val FRONT_COVER_FIELD = DSL.field("frontCoverart", BLOB)
        val BACK_COVER_FIELD = DSL.field("backCoverart", BLOB)
        val LYRICS_FIELD = DSL.field("lyrics", CLOB)
        val LENGTH_FIELD = DSL.field("lengthMillis", INTEGER)

        val allFields = listOf(
            FILE_PATH_FIELD,
            ADDED_AT_FIELD,
            UPDATED_AT_FIELD,
            TITLE_FIELD,
            ARTIST_FIELD,
            ALBUM_FIELD,
            RELEASE_YEAR_FIELD,
            FRONT_COVER_FIELD,
            BACK_COVER_FIELD,
            LYRICS_FIELD,
            LENGTH_FIELD
        )
    }

    object Genres {
        val table = DSL.table("LibraryItemGenres")

        val PATH_FIELD = DSL.field("itemFilePath", CLOB)
        val NAME_FIELD = DSL.field("genreName", CLOB)
    }

    object Labels {
        val table = DSL.table("LibraryItemLabels")

        val PATH_FIELD = DSL.field("itemFilePath", CLOB)
        val NAME_FIELD = DSL.field("labelName", CLOB)
    }
}