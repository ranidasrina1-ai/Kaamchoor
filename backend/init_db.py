"""Database init script — create all tables from models and sync columns."""
import click

from app import create_app
from models import db
from schema_sync import sync_schema


@click.command()
@click.option("--drop", is_flag=True, help="Drop all tables first (DESTRUCTIVE).")
def init_db(drop):
    app = create_app()
    with app.app_context():
        if drop:
            click.echo("Dropping all tables...")
            db.drop_all()
        click.echo("Creating tables...")
        db.create_all()
        added = sync_schema()
        if added:
            click.echo("Added missing columns: " + ", ".join(added))
        click.echo("Done. All tables created.")


if __name__ == "__main__":
    init_db()
