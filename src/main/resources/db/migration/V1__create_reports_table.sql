create table reports (
    id uuid primary key,
    title varchar(255) not null,
    type varchar(50) not null,
    status varchar(50) not null,
    result text,
    error_message text,
    requested_at timestamp with time zone not null,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    processing_millis bigint
);

create index idx_reports_status_requested_at
    on reports (status, requested_at desc);
