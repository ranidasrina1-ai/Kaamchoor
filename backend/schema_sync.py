"""Idempotent schema sync for self-hosted deployments.

`db.create_all()` creates missing tables but never adds columns to tables that
already exist. Older installs (or v1 leftovers) therefore break as soon as the
models gain a field (e.g. `chat_groups.last_activity`). This module adds any
missing columns so upgrades keep working without dropping data.
"""
from sqlalchemy import inspect, text
from sqlalchemy.schema import CreateColumn

from models import db


def _default_clause(column):
    """Return a DEFAULT clause for a new NOT NULL column on a populated table."""
    if column.server_default is not None:
        return ""
    if column.nullable:
        return ""
    type_name = column.type.__class__.__name__.lower()
    if "bool" in type_name:
        return " DEFAULT 0"
    if any(t in type_name for t in ("int", "float", "numeric", "decimal", "double")):
        return " DEFAULT 0"
    if any(t in type_name for t in ("date", "time")):
        return " DEFAULT CURRENT_TIMESTAMP"
    return " DEFAULT ''"


def sync_schema():
    """Add columns present in the models but missing from the database.

    Returns a list of "table.column" strings that were added.
    """
    engine = db.engine
    inspector = inspect(engine)
    existing_tables = set(inspector.get_table_names())
    preparer = engine.dialect.identifier_preparer
    added = []

    for table in db.metadata.sorted_tables:
        if table.name not in existing_tables:
            continue
        existing_columns = {c["name"] for c in inspector.get_columns(table.name)}
        for column in table.columns:
            if column.name in existing_columns:
                continue
            ddl = str(CreateColumn(column).compile(dialect=engine.dialect))
            ddl += _default_clause(column)
            statement = (
                f"ALTER TABLE {preparer.format_table(table)} "
                f"ADD COLUMN {ddl}"
            )
            with engine.begin() as conn:
                conn.execute(text(statement))
            added.append(f"{table.name}.{column.name}")

    return added
