BEGIN;

INSERT INTO products (
    id,
    name,
    description,
    is_active,
    created_at,
    updated_at,
    deleted_at,
    version
)
VALUES
    (
        'f66237f1-b137-4523-84ee-b05fd6489a3e',
        'Sample Coffee Beans',
        'Development example product. Replace it with your own sample data.',
        TRUE,
        CURRENT_TIMESTAMP,
        NULL,
        NULL,
        0
    ),
    (
        'b4472c56-68d9-4da3-bcc1-81495c6d284d',
        'Sample Tea',
        'Development example product. This record is not real business data.',
        TRUE,
        CURRENT_TIMESTAMP,
        NULL,
        NULL,
        0
    )
ON CONFLICT (id) DO NOTHING;

COMMIT;
