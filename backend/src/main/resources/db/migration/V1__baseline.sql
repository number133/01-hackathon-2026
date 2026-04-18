CREATE TABLE schema_ready (
    id SMALLINT PRIMARY KEY,
    note TEXT NOT NULL
);

INSERT INTO schema_ready (id, note) VALUES (1, 'ok');
