package com.smartrail.common.resource;

/**
 * Parsed resource path {@code collection[/id]}, e.g. {@code trains/TR01}, {@code tracks}, {@code schedules/TR02}.
 * Leading/trailing slashes are ignored. SHARED CONTRACT (frozen).
 *
 * @param id resource ID, or {@code null} for the whole collection
 */
public record ResourcePath(String collection, String id) {

    public static ResourcePath parse(String path) {
        String clean = path == null ? "" : path.trim();
        while (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("Empty resource path");
        }
        String[] parts = clean.split("/");
        if (parts.length > 2) {
            throw new IllegalArgumentException("Resource path must be collection[/id]: " + path);
        }
        return new ResourcePath(parts[0], parts.length == 2 ? parts[1] : null);
    }

    public boolean isCollection() {
        return id == null;
    }

    @Override
    public String toString() {
        return id == null ? collection : collection + "/" + id;
    }
}
