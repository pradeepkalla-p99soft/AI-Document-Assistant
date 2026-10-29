-- Visual Assets schema migration
-- V2__create_visual_assets.sql

CREATE TABLE IF NOT EXISTS visual_assets (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    page_number INT NOT NULL,
    asset_type VARCHAR(50) NOT NULL,
    storage_key VARCHAR(255) NOT NULL UNIQUE,
    mime_type VARCHAR(100) NOT NULL,
    caption VARCHAR(2000),
    bounding_box VARCHAR(255),
    processing_status VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_visual_assets_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_visual_assets_document_id ON visual_assets(document_id);
CREATE INDEX IF NOT EXISTS idx_visual_assets_status ON visual_assets(processing_status);

