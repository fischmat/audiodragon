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
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Timestamp
import javax.imageio.ImageIO
import kotlin.io.path.absolutePathString
import kotlin.time.Duration.Companion.milliseconds

private val LOGGER = KotlinLogging.logger {}

private val LIBRARY_ITEMS_TABLE = DSL.table("LibraryItems")
private val LIBITEM_FILE_PATH_FIELD = DSL.field("filePath", CLOB)
private val LIBITEM_ADDED_AT_FIELD = DSL.field("addedAt", TIMESTAMP)
private val LIBITEM_UPDATED_AT_FIELD = DSL.field("updatedAt", TIMESTAMP)
private val LIBITEM_TITLE_FIELD = DSL.field("title", CLOB)
private val LIBITEM_ARTIST_FIELD = DSL.field("artist", CLOB)
private val LIBITEM_ALBUM_FIELD = DSL.field("album", CLOB)
private val LIBITEM_RELEASE_YEAR_FIELD = DSL.field("releaseYear", CLOB)
private val LIBITEM_FRONT_COVER_FIELD = DSL.field("frontCoverart", BLOB)
private val LIBITEM_BACK_COVER_FIELD = DSL.field("backCoverart", BLOB)
private val LIBITEM_LYRICS_FIELD = DSL.field("lyrics", CLOB)
private val LIBITEM_LENGTH_FIELD = DSL.field("lengthMillis", INTEGER)
private val LIBRARY_ITEMS_TABLE_FIELDS = listOf(
    LIBITEM_FILE_PATH_FIELD,
    LIBITEM_ADDED_AT_FIELD,
    LIBITEM_UPDATED_AT_FIELD,
    LIBITEM_TITLE_FIELD,
    LIBITEM_ARTIST_FIELD,
    LIBITEM_ALBUM_FIELD,
    LIBITEM_RELEASE_YEAR_FIELD,
    LIBITEM_FRONT_COVER_FIELD,
    LIBITEM_BACK_COVER_FIELD,
    LIBITEM_LYRICS_FIELD,
    LIBITEM_LENGTH_FIELD
)

private val LIBITEM_GENRE_TABLE = DSL.table("LibraryItemGenres")
private val LIBITEM_GENRE_PATH_FIELD = DSL.field("itemFilePath", CLOB)
private val LIBITEM_GENRE_NAME_FIELD = DSL.field("genreName", CLOB)

private val LIBITEM_LABEL_TABLE = DSL.table("LibraryItemLabels")
private val LIBITEM_LABEL_PATH_FIELD = DSL.field("itemFilePath", CLOB)
private val LIBITEM_LABEL_NAME_FIELD = DSL.field("labelName", CLOB)

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
            .selectFrom(LIBRARY_ITEMS_TABLE)
            .where(LIBITEM_FILE_PATH_FIELD.eq(pathString))
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
                DSL.lower(LIBITEM_TITLE_FIELD).like(searchLikeExp, '\\')
                    .or(
                        DSL.lower(LIBITEM_ARTIST_FIELD).like(searchLikeExp, '\\')
                            .or(DSL.lower(LIBITEM_ALBUM_FIELD).like(searchLikeExp, '\\'))
                    )
            },
            titleSearch?.let { DSL.lower(LIBITEM_TITLE_FIELD).like("%$it%", '\\') },
            artistSearch?.let { DSL.lower(LIBITEM_ARTIST_FIELD).like("%$it%", '\\') },
            albumSearch?.let { DSL.lower(LIBITEM_ALBUM_FIELD).like("%$it%", '\\') },
            genres?.map { it.lowercase() }?.let { DSL.lower(LIBITEM_GENRE_NAME_FIELD).`in`(it) }
        )

        DSL.using(ctx)
            .selectDistinct(LIBRARY_ITEMS_TABLE_FIELDS)
            .from(LIBRARY_ITEMS_TABLE)
            .leftJoin(LIBITEM_GENRE_TABLE).on(LIBITEM_FILE_PATH_FIELD.eq(LIBITEM_GENRE_PATH_FIELD))
            .where(conditions.ifEmpty { listOf(DSL.trueCondition()) })
            .orderBy(sortOrder.jooqOrder(sortBy.tableField), LIBITEM_UPDATED_AT_FIELD.desc())
            .let {
                if (page != null) {
                    it.offset(pageSize * (page-1)).limit(pageSize)
                } else {
                    it
                }
            }
            .fetch()
            .map {
                val path = Paths.get(it.get(LIBITEM_FILE_PATH_FIELD))
                recordToLibraryItem(it, getGenres(ctx, path), getLabels(ctx, path))
            }
    }

    fun replaceAllItems(items: List<LibraryItem>) = txn { ctx ->
        DSL.using(ctx)
            .deleteFrom(LIBRARY_ITEMS_TABLE)
            .where(DSL.trueCondition())

        val insertRows = items.map {
            DSL.row(
                it.filePath.absolutePathString(),
                Timestamp(it.addedAt?.toEpochMilli() ?: it.filePath.toFile().lastModified()),
                Timestamp(it.updatedAt?.toEpochMilli() ?: it.filePath.toFile().lastModified()),
                it.title,
                it.artist,
                it.album,
                it.releaseYear,
                it.frontCoverart.value?.let { img -> imageBytes(img) },
                it.backCoverart.value?.let { img -> imageBytes(img) },
                it.lyrics?.joinToString("\n"),
                it.length?.inWholeMilliseconds?.toInt()
            )
        }
        DSL.using(ctx)
            .insertInto(LIBRARY_ITEMS_TABLE)
            .columns(
                LIBITEM_FILE_PATH_FIELD,
                LIBITEM_ADDED_AT_FIELD,
                LIBITEM_UPDATED_AT_FIELD,
                LIBITEM_TITLE_FIELD,
                LIBITEM_ARTIST_FIELD,
                LIBITEM_ALBUM_FIELD,
                LIBITEM_RELEASE_YEAR_FIELD,
                LIBITEM_FRONT_COVER_FIELD,
                LIBITEM_BACK_COVER_FIELD,
                LIBITEM_LYRICS_FIELD,
                LIBITEM_LENGTH_FIELD
            )
            .valuesOfRows(insertRows)
    }

    fun deleteItem(filePath: Path) = txn { ctx ->
        DSL.using(ctx)
            .deleteFrom(LIBRARY_ITEMS_TABLE)
            .where(LIBITEM_FILE_PATH_FIELD.eq(filePath.absolutePathString()))
            .execute() > 0
    }

    private fun upsertItem(ctx: Configuration, item: LibraryItem) {
        DSL.using(ctx)
            .insertInto(LIBRARY_ITEMS_TABLE)
            .columns(
                LIBITEM_FILE_PATH_FIELD,
                LIBITEM_ADDED_AT_FIELD,
                LIBITEM_UPDATED_AT_FIELD,
                LIBITEM_TITLE_FIELD,
                LIBITEM_ARTIST_FIELD,
                LIBITEM_ALBUM_FIELD,
                LIBITEM_RELEASE_YEAR_FIELD,
                LIBITEM_FRONT_COVER_FIELD,
                LIBITEM_BACK_COVER_FIELD,
                LIBITEM_LYRICS_FIELD,
                LIBITEM_LENGTH_FIELD
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
            .onConflict(LIBITEM_FILE_PATH_FIELD)
            .doUpdate()
            .set(
                mapOf(
                    LIBITEM_UPDATED_AT_FIELD to Timestamp(item.filePath.toFile().lastModified()),
                    LIBITEM_TITLE_FIELD to item.title,
                    LIBITEM_ARTIST_FIELD to item.artist,
                    LIBITEM_ALBUM_FIELD to item.album,
                    LIBITEM_RELEASE_YEAR_FIELD to item.releaseYear,
                    LIBITEM_FRONT_COVER_FIELD to item.frontCoverart.value?.let { imageBytes(it) },
                    LIBITEM_BACK_COVER_FIELD to item.backCoverart.value?.let { imageBytes(it) },
                    LIBITEM_LYRICS_FIELD to item.lyrics?.joinToString("\n"),
                    LIBITEM_LENGTH_FIELD to item.length?.inWholeMilliseconds?.toInt()
                )
            )
            .execute()

        DSL.using(ctx)
            .deleteFrom(LIBITEM_GENRE_TABLE)
            .where(LIBITEM_GENRE_PATH_FIELD.eq(item.filePath.absolutePathString()))
            .execute()
        DSL.using(ctx)
            .deleteFrom(LIBITEM_LABEL_TABLE)
            .where(LIBITEM_LABEL_PATH_FIELD.eq(item.filePath.absolutePathString()))
            .execute()

        item.genres.forEach {
            DSL.using(ctx)
                .insertInto(LIBITEM_GENRE_TABLE)
                .columns(
                    LIBITEM_GENRE_PATH_FIELD,
                    LIBITEM_GENRE_NAME_FIELD
                )
                .values(item.filePath.absolutePathString(), it)
                .execute()
        }

        item.labels.forEach {
            DSL.using(ctx)
                .insertInto(LIBITEM_LABEL_TABLE)
                .columns(
                    LIBITEM_LABEL_PATH_FIELD,
                    LIBITEM_LABEL_NAME_FIELD
                )
                .values(item.filePath.absolutePathString(), it)
                .execute()
        }
    }

    private fun getGenres(ctx: Configuration, path: Path) = txn {
        DSL.using(ctx)
            .selectFrom(LIBITEM_GENRE_TABLE)
            .where(LIBITEM_GENRE_PATH_FIELD.eq(path.absolutePathString()))
            .fetch()
            .toList()
            .map {
                it.get(LIBITEM_GENRE_NAME_FIELD)
            }
    }

    private fun getLabels(ctx: Configuration, path: Path) = txn {
        DSL.using(ctx)
            .selectFrom(LIBITEM_LABEL_TABLE)
            .where(LIBITEM_LABEL_PATH_FIELD.eq(path.absolutePathString()))
            .fetch()
            .toList()
            .map {
                it.get(LIBITEM_LABEL_NAME_FIELD)
            }
    }

    private fun recordToLibraryItem(record: Record, genres: List<String>, labels: List<String>): LibraryItem {
        val filePath = Paths.get(record.get(LIBITEM_FILE_PATH_FIELD))

        return LibraryItem(
            filePath,
            record.get(LIBITEM_ADDED_AT_FIELD).toInstant(),
            record.get(LIBITEM_UPDATED_AT_FIELD).toInstant(),
            record.get(LIBITEM_TITLE_FIELD),
            record.get(LIBITEM_ARTIST_FIELD),
            record.get(LIBITEM_ALBUM_FIELD),
            genres,
            labels,
            record.get(LIBITEM_RELEASE_YEAR_FIELD),
            imageDataLoader(filePath, LIBITEM_FRONT_COVER_FIELD),
            imageDataLoader(filePath, LIBITEM_BACK_COVER_FIELD),
            record.get(LIBITEM_LYRICS_FIELD)?.split("\n"),
            record.get(LIBITEM_LENGTH_FIELD)?.milliseconds,
        )
    }

    private fun imageDataLoader(filePath: Path, field: Field<ByteArray>) = lazy {
        txn { ctx ->
            DSL.using(ctx)
                .select(field)
                .from(LIBRARY_ITEMS_TABLE)
                .where(LIBITEM_FILE_PATH_FIELD.eq(filePath.absolutePathString()))
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
        onConnection { con ->
            DSL.using(con).transactionResult(action)
        }

    private fun <T> onConnection(action: (Connection) -> T): T {
        return DriverManager.getConnection(jdbcUrl, "", "").use(action)
    }
}

enum class LibraryItemSortField(val tableField: Field<*>) {
    FILE_PATH(LIBITEM_FILE_PATH_FIELD),
    ADDED_AT(LIBITEM_ADDED_AT_FIELD),
    UPDATED_AT(LIBITEM_UPDATED_AT_FIELD),
    TITLE(LIBITEM_TITLE_FIELD),
    ARTIST(LIBITEM_ARTIST_FIELD),
    ALBUM(LIBITEM_ALBUM_FIELD),
    LENGTH(LIBITEM_LENGTH_FIELD)
}

enum class SortOrder(val jooqOrder: (Field<*>) -> SortField<*>) {
    ASC({ it.asc() }), DESC({ it.desc() })
}