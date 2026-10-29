package com.example.docassistant.service.extraction.layout;

public record BoundingBox(float x, float y, float width, float height) {
    public boolean intersects(BoundingBox other) {
        return x < other.x + other.width &&
                x + width > other.x &&
                y < other.y + other.height &&
                y + height > other.y;
    }

    public String toCoordinateString() {
        return String.format("[x=%.1f,y=%.1f,w=%.1f,h=%.1f]", x, y, width, height);
    }
}

