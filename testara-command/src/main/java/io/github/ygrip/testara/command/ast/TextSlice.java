package io.github.ygrip.testara.command.ast;

/**
 * Zero-copy view of a substring. {@link #toString()} materializes the substring lazily.
 */
public final class TextSlice implements CharSequence {

    private final String source;
    private final int start;
    private final int end;

    public TextSlice(String source, int start, int end) {
        if (source == null) throw new NullPointerException("source");
        if (start < 0 || end > source.length() || start > end) {
            throw new IndexOutOfBoundsException(
                "Invalid range [" + start + "," + end + "] for length " + source.length());
        }
        this.source = source;
        this.start = start;
        this.end = end;
    }

    @Override
    public int length() {
        return end - start;
    }

    @Override
    public char charAt(int index) {
        if (index < 0 || index >= length()) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for length " + length());
        }
        return source.charAt(start + index);
    }

    @Override
    public CharSequence subSequence(int s, int e) {
        if (s < 0 || e > length() || s > e) {
            throw new IndexOutOfBoundsException(
                "Invalid subsequence [" + s + "," + e + "] for length " + length());
        }
        return new TextSlice(source, start + s, start + e);
    }

    @Override
    public String toString() {
        return source.substring(start, end);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TextSlice other)) return false;
        return toString().equals(other.toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
