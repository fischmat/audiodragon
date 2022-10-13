package de.matthiasfisch.audiodragon.controller

import de.matthiasfisch.audiodragon.service.LibraryService
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.*
import java.util.*
import javax.servlet.http.HttpServletResponse

@RestController
@RequestMapping("/v1/library")
class LibraryController(private val libraryService: LibraryService) {

    @GetMapping fun getAllLibraryItems() = libraryService.getAllItems()

    @GetMapping("/coverart/front/{itemId}")
    fun getCoverart(@PathVariable("itemId") itemId: UUID, @RequestParam(name = "dim", defaultValue = "100") maxDimension: Int, response: HttpServletResponse) {
        response.setHeader(HttpHeaders.CONTENT_TYPE, "image/png")
        libraryService.writeFrontCoverartAsPng(itemId, response.outputStream, maxDimension)
    }
}