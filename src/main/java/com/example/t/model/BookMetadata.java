package com.example.t.model;

import java.sql.Timestamp;

/**
 * Bibliographic metadata for a book item enriched from local cache or online providers
 * (Google Books API, Open Library API).
 */
public class BookMetadata {
    private String isbn13;
    private String isbn10;
    private String title;
    private String subtitle;
    private String authors;
    private String publisher;
    private String publishedYear;
    private String edition;
    private String format;
    private String genre;
    private String description;
    private int pageCount;
    private String coverImagePath;
    private String coverUrl;
    private String source;
    private Timestamp cachedAt;
    private String rawJson;

    public BookMetadata() {
    }

    public BookMetadata(String isbn13, String isbn10, String title, String subtitle,
                        String authors, String publisher, String publishedYear,
                        String edition, String format, String genre, String description,
                        int pageCount, String coverImagePath, String coverUrl,
                        String source, Timestamp cachedAt, String rawJson) {
        this.isbn13 = isbn13;
        this.isbn10 = isbn10;
        this.title = title;
        this.subtitle = subtitle;
        this.authors = authors;
        this.publisher = publisher;
        this.publishedYear = publishedYear;
        this.edition = edition;
        this.format = format;
        this.genre = genre;
        this.description = description;
        this.pageCount = pageCount;
        this.coverImagePath = coverImagePath;
        this.coverUrl = coverUrl;
        this.source = source;
        this.cachedAt = cachedAt;
        this.rawJson = rawJson;
    }

    public String getIsbn13() {
        return isbn13;
    }

    public void setIsbn13(String isbn13) {
        this.isbn13 = isbn13;
    }

    public String getIsbn10() {
        return isbn10;
    }

    public void setIsbn10(String isbn10) {
        this.isbn10 = isbn10;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getAuthors() {
        return authors;
    }

    public void setAuthors(String authors) {
        this.authors = authors;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getPublishedYear() {
        return publishedYear;
    }

    public void setPublishedYear(String publishedYear) {
        this.publishedYear = publishedYear;
    }

    public String getEdition() {
        return edition;
    }

    public void setEdition(String edition) {
        this.edition = edition;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getPageCount() {
        return pageCount;
    }

    public void setPageCount(int pageCount) {
        this.pageCount = pageCount;
    }

    public String getCoverImagePath() {
        return coverImagePath;
    }

    public void setCoverImagePath(String coverImagePath) {
        this.coverImagePath = coverImagePath;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public String getCoverImageUrl() {
        return coverUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverUrl = coverImageUrl;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Timestamp getCachedAt() {
        return cachedAt;
    }

    public void setCachedAt(Timestamp cachedAt) {
        this.cachedAt = cachedAt;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }

    @Override
    public String toString() {
        return "BookMetadata{" +
                "isbn13='" + isbn13 + '\'' +
                ", isbn10='" + isbn10 + '\'' +
                ", title='" + title + '\'' +
                ", authors='" + authors + '\'' +
                ", publisher='" + publisher + '\'' +
                ", publishedYear='" + publishedYear + '\'' +
                ", source='" + source + '\'' +
                '}';
    }
}
