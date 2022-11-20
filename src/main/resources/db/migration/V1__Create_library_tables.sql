CREATE TABLE LibraryItems (
	filePath TEXT PRIMARY KEY,
	addedAt DATETIME NOT NULL,
	updatedAt DATETIME NOT NULL,
	title TEXT NOT NULL,
	artist TEXT NOT NULL,
	album TEXT NULL,
	releaseYear TEXT NULL,
	frontCoverart BLOB NULL,
	backCoverart BLOB NULL,
	lyrics TEXT NULL,
	lengthMillis INT NULL
);

CREATE TABLE LibraryItemGenres (
	itemFilePath TEXT NOT NULL,
	genreName TEXT NOT NULL,
	PRIMARY KEY(itemFilePath, genreName)
	FOREIGN KEY(itemFilePath) REFERENCES LibraryItems(filePath) ON DELETE CASCADE
);

CREATE TABLE LibraryItemLabels (
	itemFilePath TEXT NOT NULL,
	labelName TEXT NOT NULL,
	PRIMARY KEY(itemFilePath, labelName),
	FOREIGN KEY(itemFilePath) REFERENCES LibraryItems(filePath) ON DELETE CASCADE
);