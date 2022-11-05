package de.matthiasfisch.audiodragon.library

import java.nio.file.Path

open class LibraryException(message: String): Exception(message)

class NoID3TagException(val path: Path): LibraryException("File $path does not have an ID3 header")