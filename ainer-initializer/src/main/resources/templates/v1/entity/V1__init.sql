CREATE TABLE {{table.name}} (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
{{entity.columns}}
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL{{entity.constraints}}
);
{{entity.comments}}