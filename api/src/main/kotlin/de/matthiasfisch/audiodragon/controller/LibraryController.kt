package de.matthiasfisch.audiodragon.controller

import de.matthiasfisch.audiodragon.library.peristence.LibraryItemSortField
import de.matthiasfisch.audiodragon.library.peristence.SortOrder
import de.matthiasfisch.audiodragon.service.LibraryService
import de.matthiasfisch.audiodragon.types.LibraryItemDTO
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.nio.file.Paths
import javax.servlet.http.HttpServletResponse

@RestController
@RequestMapping("/v1/library")
class LibraryController(private val libraryService: LibraryService) {

    @GetMapping
    fun getAllItems(
        @RequestParam(name = "search", required = false) search: String?,
        @RequestParam(name = "title", required = false) titleSearch: String?,
        @RequestParam(name = "artist", required = false) artistSearch: String?,
        @RequestParam(name = "album", required = false) albumSearch: String?,
        @RequestParam(name = "genre", required = false) genre: String?,
        @RequestParam(name = "page", required = false) page: Int?,
        @RequestParam(name = "pageSize", required = false, defaultValue = "10") pageSize: Int,
        @RequestParam(name = "sort", required = false, defaultValue = "UPDATED_AT") sortBy: LibraryItemSortField,
        @RequestParam(name = "sortOrder", required = false, defaultValue = "DESC") sortOrder: SortOrder
    ): List<LibraryItemDTO> {
        return libraryService.getAllItems(search, titleSearch, artistSearch, albumSearch, genre?.let { listOf(it) }, page, pageSize, sortBy, sortOrder)
    }

    @GetMapping("/frontcover")
    fun getFrontCoverImage(@RequestParam("file") filePath: String, response: HttpServletResponse) {
        response.setHeader(HttpHeaders.CONTENT_TYPE, "image/png")
        libraryService.getItemFrontCover(Paths.get(filePath), response.outputStream, "png")
    }

    @GetMapping("/backcover")
    fun getBackCoverImage(@RequestParam("file") filePath: String, response: HttpServletResponse) {
        response.setHeader(HttpHeaders.CONTENT_TYPE, "image/png")
        libraryService.getItemBackCover(Paths.get(filePath), response.outputStream, "png")
    }
}