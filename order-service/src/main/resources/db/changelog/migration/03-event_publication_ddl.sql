--liquibase formatted sql
--preconditions onFail:MARK_RAN onError:HALT
--precondition-dbms type:postgresql
--changeset raja:03-event_publication_ddl
CREATE TABLE IF NOT EXISTS event_publication
(
    id                     UUID NOT NULL,
    listener_id            TEXT NOT NULL,
    event_type             TEXT NOT NULL,
    serialized_event       TEXT NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
    );
CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx ON event_publication USING hash(serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx ON event_publication (completion_date);

CREATE TABLE IF NOT EXISTS event_publication_archive
(
    id                     UUID NOT NULL,
    listener_id            TEXT NOT NULL,
    event_type             TEXT NOT NULL,
    serialized_event       TEXT NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
    );
CREATE INDEX IF NOT EXISTS event_publication_archive_serialized_event_hash_idx ON event_publication_archive USING hash(serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_archive_by_completion_date_idx ON event_publication_archive (completion_date);

--rollback DROP INDEX IF EXISTS event_publication_archive_by_completion_date_idx;
--rollback DROP INDEX IF EXISTS event_publication_archive_serialized_event_hash_idx;
--rollback DROP TABLE IF EXISTS event_publication_archive;
--rollback DROP INDEX IF EXISTS event_publication_by_completion_date_idx;
--rollback DROP INDEX IF EXISTS event_publication_serialized_event_hash_idx;
--rollback DROP TABLE IF EXISTS event_publication;
